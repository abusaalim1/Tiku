// Story voiceover player — asks /api/tts for audio chunk by chunk
// and plays the chunks in order through one shared <audio> element.
// Long stories are split on sentence boundaries so the first words
// start playing quickly instead of waiting for the whole story.

export type VoiceState = 'idle' | 'loading' | 'playing' | 'error';

let currentAudio: HTMLAudioElement | null = null;
let session = 0;

export function stopVoiceover() {
  session++;
  if (currentAudio) {
    currentAudio.pause();
    currentAudio.removeAttribute('src');
    currentAudio = null;
  }
}

function splitChunks(text: string, max = 650): string[] {
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
  stopVoiceover();
  const my = session;
  onState('loading');
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
        const audio = new Audio(url);
        currentAudio = audio;
        onState('playing');
        audio.onended = () => {
          URL.revokeObjectURL(url);
          resolve();
        };
        audio.onerror = () => {
          URL.revokeObjectURL(url);
          reject(new Error('playback failed'));
        };
        audio.play().catch(reject);
      });
      if (my !== session) return;
    }
    if (my === session) onState('idle');
  } catch {
    if (my === session) onState('error');
  }
}
