// Story voiceover — Vercel serverless function.
//
// Why this exists: the site is a static Vite SPA on Vercel, so the
// AI Studio pattern (an Express server holding GEMINI_API_KEY) never
// runs here — that is why every earlier voiceover attempt failed
// silently. This function keeps the key server-side and turns story
// text into spoken audio with Gemini TTS.
//
// Voice: "Sulafat" — a warm female voice, steered by a style prompt
// into a soft, romantic read. Language is auto-detected from the
// text itself, so Bengali, Benglish, Hinglish and English stories
// are all read in their own language.

const MODEL = 'gemini-2.5-flash-preview-tts';
const VOICE_NAME = 'Sulafat';
const STYLE_PREFIX =
  'Say in a soft, warm, romantic female voice, slowly and lovingly, ' +
  'like reading a love letter aloud to someone dear: ';
const MAX_CHARS = 900;

function pcmToWav(pcm: Buffer, sampleRate = 24000, channels = 1, bitsPerSample = 16): Buffer {
  const byteRate = (sampleRate * channels * bitsPerSample) / 8;
  const blockAlign = (channels * bitsPerSample) / 8;
  const header = Buffer.alloc(44);
  header.write('RIFF', 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write('WAVE', 8);
  header.write('fmt ', 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(channels, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(blockAlign, 32);
  header.writeUInt16LE(bitsPerSample, 34);
  header.write('data', 36);
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

// Emojis and decorative symbols get spelled out or stumbled over by
// TTS — strip them, keep every word and punctuation mark.
function cleanForSpeech(text: string): string {
  return text
    .replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}\u{200D}]/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export default async function handler(req: any, res: any) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    res.status(500).json({ error: 'Voice is not configured yet' });
    return;
  }

  let rawText = '';
  try {
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    rawText = (body?.text ?? '').toString();
  } catch {
    rawText = '';
  }

  const text = cleanForSpeech(rawText).slice(0, MAX_CHARS);
  if (!text) {
    res.status(400).json({ error: 'Nothing to read' });
    return;
  }

  const requestBody = JSON.stringify({
    contents: [{ parts: [{ text: STYLE_PREFIX + text }] }],
    generationConfig: {
      responseModalities: ['AUDIO'],
      speechConfig: {
        voiceConfig: {
          prebuiltVoiceConfig: { voiceName: VOICE_NAME },
        },
      },
    },
  });
  const callGemini = () =>
    fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': apiKey,
      },
      body: requestBody,
    });

  try {
    let resp = await callGemini();
    if (resp.status === 429) {
      // Free-tier TTS rate limit — wait a few seconds and retry once
      // instead of failing the story outright.
      await new Promise((r) => setTimeout(r, 5000));
      resp = await callGemini();
    }

    if (!resp.ok) {
      res.status(502).json({ error: 'Voice generation failed' });
      return;
    }

    const data: any = await resp.json();
    const b64: string | undefined =
      data?.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
    if (!b64) {
      res.status(502).json({ error: 'Voice generation failed' });
      return;
    }

    const wav = pcmToWav(Buffer.from(b64, 'base64'));
    res.setHeader('Content-Type', 'audio/wav');
    res.setHeader('Cache-Control', 'no-store');
    res.status(200).send(wav);
  } catch {
    res.status(502).json({ error: 'Voice generation failed' });
  }
}
