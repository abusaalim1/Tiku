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

function splitChunks(text: string, max = 450): string[] {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean.length <= max) return clean ? [clean] : [];
  const sentences = clean.match(/[^.!?…।\n]+[.!?…।]*/g) || [clean];
  const chunks: string[] = [];
  let buf = '';
  for (const s of sentences) {
    const part = s.trim();
    if (!part) continue;
    if (buf && buf.length + part.length + 1 > max) {
      chunks.push(buf);
      buf = part;
    } else {
      buf = buf ? buf + ' ' + part : part;
    }
    while (buf.length > max) {
      chunks.push(buf.slice(0, max));
      buf = buf.slice(max);
    }
  }
  if (buf) chunks.push(buf);
  return chunks;
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
    for (const chunk of chunks) {
      if (my !== session) return;
      const resp = await fetch('/api/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: chunk }),
      });
      if (!resp.ok) throw new Error('tts failed');
      const blob = await resp.blob();
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
