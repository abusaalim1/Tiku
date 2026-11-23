// Story voiceover player — asks /api/tts for audio chunk by chunk
// and plays the chunks in order through ONE shared <audio> element.
//
// Why one shared element + priming: phone browsers and the Android
// WebView refuse audio that was not unlocked by a real tap — and
// TTS generation takes a few seconds, so by the time the audio is
// ready the tap's "permission window" has already closed and play()
// gets rejected (the voiceover used to stop by itself right after
// tapping Listen). primeVoiceover() runs inside the tap itself and
// plays a 0.1s silent clip on the shared element; that unlocks the
// element for the rest of the session, so the real chunks can play
// whenever they arrive.

export type VoiceState = 'idle' | 'loading' | 'playing' | 'error';

let audioEl: HTMLAudioElement | null = null;
let session = 0;

function getAudio(): HTMLAudioElement {
  if (!audioEl) {
    audioEl = new Audio();
    audioEl.preload = 'auto';
  }
  return audioEl;
}

function silentWavUrl(): string {
  const sampleRate = 24000;
  const frames = 2400; // 0.1s of silence
  const pcmBytes = frames * 2;
  const wav = new Uint8Array(44 + pcmBytes);
  const dv = new DataView(wav.buffer);
  const wstr = (off: number, s: string) => {
    for (let i = 0; i < s.length; i++) dv.setUint8(off + i, s.charCodeAt(i));
  };
  wstr(0, 'RIFF');
  dv.setUint32(4, 36 + pcmBytes, true);
  wstr(8, 'WAVE');
  wstr(12, 'fmt ');
  dv.setUint32(16, 16, true);
  dv.setUint16(20, 1, true);
  dv.setUint16(22, 1, true);
  dv.setUint32(24, sampleRate, true);
  dv.setUint32(28, sampleRate * 2, true);
  dv.setUint16(32, 2, true);
  dv.setUint16(34, 16, true);
  wstr(36, 'data');
  dv.setUint32(40, pcmBytes, true);
  return URL.createObjectURL(new Blob([wav], { type: 'audio/wav' }));
}

// Call this synchronously inside the tap/click handler, before any
// async work — it is what unlocks audio playback on mobile.
export function primeVoiceover() {
  const a = getAudio();
  if (!a.src) {
    a.src = silentWavUrl();
  }
  a.play().catch(() => {
    // A rejected prime still counts as a gesture attempt on the
    // element in most browsers; the real chunk play() decides.
  });
}

export function stopVoiceover() {
  session++;
  if (audioEl) {
    audioEl.pause();
    audioEl.removeAttribute('src');
    audioEl.load();
  }
}

// Gemini TTS generation runs at roughly half of real time, so a
// big chunk means a long silent wait. The first chunk stays small
// (~150 chars ≈ 15s of audio) so her voice starts as soon as
// possible; later chunks are bigger and are generated WHILE the
// current one plays, so the story flows without gaps.
function splitChunks(text: string): string[] {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return [];
  const sentences = (clean.match(/[^.!?…।\n]+[.!?…।]*/g) || [clean])
    .map((s) => s.trim())
    .filter(Boolean);
  const chunks: string[] = [];
  let buf = '';
  let limit = 150;
  const flush = () => {
    if (buf) chunks.push(buf);
    buf = '';
    limit = 380;
  };
  for (const part of sentences) {
    if (buf && buf.length + part.length + 1 > limit) flush();
    buf = buf ? buf + ' ' + part : part;
    while (buf.length > limit) {
      chunks.push(buf.slice(0, limit));
      buf = buf.slice(limit);
      limit = 380;
    }
  }
  flush();
  return chunks;
}

async function fetchChunkAudio(text: string): Promise<Blob> {
  const resp = await fetch('/api/tts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  if (!resp.ok) throw new Error('tts failed');
  return resp.blob();
}

export async function playVoiceover(
  text: string,
  onState: (state: VoiceState) => void,
): Promise<void> {
  const my = ++session;
  onState('loading');
  const audio = getAudio();
  try {
    const chunks = splitChunks(text);
    if (chunks.length === 0) {
      onState('idle');
      return;
    }
    // Pipeline: the next chunk is generated while the current plays.
    let nextBlob: Promise<Blob> | null = fetchChunkAudio(chunks[0]);
    for (let i = 0; i < chunks.length; i++) {
      if (my !== session) return;
      const blob = await nextBlob;
      nextBlob = i + 1 < chunks.length ? fetchChunkAudio(chunks[i + 1]) : null;
      // A failed prefetch surfaces on its own turn; guard the rejection
      // until then so it is never reported as unhandled.
      nextBlob?.catch(() => {});
      if (my !== session) return;
      const url = URL.createObjectURL(blob);
      await new Promise<void>((resolve, reject) => {
        audio.onended = () => {
          URL.revokeObjectURL(url);
          resolve();
        };
        audio.onerror = () => {
          URL.revokeObjectURL(url);
          reject(new Error('playback failed'));
        };
        audio.src = url;
        onState('playing');
        audio.play().catch((err) => {
          // One delayed retry — some WebViews settle late.
          setTimeout(() => {
            if (my !== session) return;
            audio.play().catch(() => reject(err));
          }, 350);
        });
      });
      if (my !== session) return;
    }
    if (my === session) onState('idle');
  } catch {
    if (my === session) onState('error');
  }
}
