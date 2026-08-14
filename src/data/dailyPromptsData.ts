import { DailyPromptEntry, PromptCategory, PromptTemplate } from '../types';

export interface CategoryMeta {
  label: string;
  emoji: string;
  color: string;
  bgColor: string;
  borderColor: string;
  description: string;
}

export const PROMPT_CATEGORIES_METADATA: Record<PromptCategory, CategoryMeta> = {
  romantic: {
    label: 'Sweet Romance',
    emoji: '💖',
    color: '#FF4B72',
    bgColor: '#FFF0F3',
    borderColor: '#FFCCD5',
    description: 'Heartfelt affirmations & intimate feelings',
  },
  fun: {
    label: 'Playful & Silly',
    emoji: '🎉',
    color: '#D97706',
    bgColor: '#FEF3C7',
    borderColor: '#FDE68A',
    description: 'Fun scenarios, quizzes, and quirky hypotheticals',
  },
  activity: {
    label: 'Date Challenge',
    emoji: '🥂',
    color: '#8B5CF6',
    bgColor: '#F5F3FF',
    borderColor: '#DDD6FE',
    description: 'Quick romantic micro-dates and actions for today',
  },
  memories: {
    label: 'Nostalgia & Us',
    emoji: '✨',
    color: '#059669',
    bgColor: '#ECFDF5',
    borderColor: '#A7F3D0',
    description: 'Reflections on favorite moments and milestones',
  },
  deep: {
    label: 'Heart-to-Heart',
    emoji: '💬',
    color: '#0284C7',
    bgColor: '#F0F9FF',
    borderColor: '#BAE6FD',
    description: 'Soulful questions to deepen your emotional bond',
  },
  future: {
    label: 'Dreams & Bucket List',
    emoji: '🚀',
    color: '#E11D48',
    bgColor: '#FFE4E6',
    borderColor: '#FECDD3',
    description: 'Exciting future trips, goals, and life dreams',
  },
};

export const PROMPT_TEMPLATES: PromptTemplate[] = [
  // ROMANTIC QUESTIONS & AFFIRMATIONS
  {
    id: 'pr-rom-1',
    category: 'romantic',
    type: 'question',
    emoji: '💖',
    text: 'What was the exact moment or day you realized you had truly fallen for me?',
    subtext: 'Describe where we were, what we were doing, or what made your heart skip a beat.',
    suggestedAction: 'Take a second to look into each other’s eyes as you answer.',
  },
  {
    id: 'pr-rom-2',
    category: 'romantic',
    type: 'question',
    emoji: '💌',
    text: 'What is one subtle, everyday thing I do that never fails to make your heart warm?',
    subtext: 'Could be the way I say goodbye, a funny habit, or a warm morning gesture.',
  },
  {
    id: 'pr-rom-3',
    category: 'romantic',
    type: 'question',
    emoji: '🌹',
    text: 'If you could relive any single romantic hour we have shared together, which one would it be?',
    subtext: 'Paint the picture of that hour in your own words.',
  },
  {
    id: 'pr-rom-4',
    category: 'romantic',
    type: 'question',
    emoji: '✨',
    text: 'What is your favorite compliment or sweet phrase I have ever told you?',
    subtext: 'Why did those specific words stay so close to your heart?',
  },
  {
    id: 'pr-rom-5',
    category: 'romantic',
    type: 'question',
    emoji: '🥰',
    text: 'What is something you adore about my personality that others might not notice at first glance?',
  },
  {
    id: 'pr-rom-6',
    category: 'romantic',
    type: 'question',
    emoji: '🌙',
    text: 'When we are apart, what do you miss most about having me beside you?',
  },
  {
    id: 'pr-rom-7',
    category: 'romantic',
    type: 'question',
    emoji: '🌸',
    text: 'What is one song that always makes you think of us, and what memory does it bring back?',
  },

  // PLAYFUL & FUN HYPOTHETICALS
  {
    id: 'pr-fun-1',
    category: 'fun',
    type: 'question',
    emoji: '🎬',
    text: 'If a Hollywood movie was made about our relationship, what would the title and genre be?',
    subtext: 'Who plays each of us, and what is the biggest plot twist in the movie?',
  },
  {
    id: 'pr-fun-2',
    category: 'fun',
    type: 'question',
    emoji: '✈️',
    text: 'If we were handed 2 boarding passes to anywhere in the world leaving in 2 hours, where are we heading?',
    subtext: 'What is the very first food or drink we are tasting when we land?',
  },
  {
    id: 'pr-fun-3',
    category: 'fun',
    type: 'question',
    emoji: '🍕',
    text: 'What is our ultimate signature couple comfort food order when we are having a lazy couch day?',
  },
  {
    id: 'pr-fun-4',
    category: 'fun',
    type: 'question',
    emoji: '🎭',
    text: 'What is our funniest inside joke or silly habit that would sound totally absurd to anybody else?',
  },
  {
    id: 'pr-fun-5',
    category: 'fun',
    type: 'question',
    emoji: '🦸‍♂️',
    text: 'If we were a superhero duo, what would our couple superpower and hero nicknames be?',
  },
  {
    id: 'pr-fun-6',
    category: 'fun',
    type: 'question',
    emoji: '🏆',
    text: 'If I had to win an Olympic medal in one quirky or funny daily skill, what medal am I taking home?',
  },
  {
    id: 'pr-fun-7',
    category: 'fun',
    type: 'question',
    emoji: '🏝️',
    text: 'If we were stranded on a deserted island, who is building the shelter and who is foraging for snacks?',
  },

  // DATE CHALLENGES & ROMANTIC ACTIVITIES
  {
    id: 'pr-act-1',
    category: 'activity',
    type: 'activity',
    emoji: '🫂',
    text: 'The 60-Second Soul Hug Challenge',
    subtext: 'Stop everything right now, hold each other in a long, silent 60-second hug, and take 3 deep breaths together.',
    suggestedAction: 'Snap a selfie right after your hug to save in today’s memory!',
  },
  {
    id: 'pr-act-2',
    category: 'activity',
    type: 'activity',
    emoji: '☕',
    text: 'Surprise Beverage or Treat Delivery',
    subtext: 'Make or grab your partner’s favorite warm drink or favorite snack with a sweet little handwritten note.',
    suggestedAction: 'Write a 1-sentence love note on a napkin or sticky note.',
  },
  {
    id: 'pr-act-3',
    category: 'activity',
    type: 'activity',
    emoji: '🎶',
    text: 'Living Room Slow Dance',
    subtext: 'Put on our favorite love song and share an unhurried 3-minute slow dance right in the living room or kitchen.',
    suggestedAction: 'No phones allowed during the song except for the music playback!',
  },
  {
    id: 'pr-act-4',
    category: 'activity',
    type: 'activity',
    emoji: '📸',
    text: 'Silly Face Polaroid Snap Challenge',
    subtext: 'Pose together making the goofiest faces you can muster, and upload it as today’s daily snap!',
  },
  {
    id: 'pr-act-5',
    category: 'activity',
    type: 'activity',
    emoji: '🕯️',
    text: 'Candlelight Dessert or Evening Tea',
    subtext: 'Turn down the bright overhead lights tonight, light a candle, and enjoy a treat or tea together in soft glow.',
  },
  {
    id: 'pr-act-6',
    category: 'activity',
    type: 'activity',
    emoji: '💆‍♂️',
    text: '5-Minute Shoulder or Hand Massage',
    subtext: 'Give each other a relaxing 5-minute shoulder or scalp massage to melt away all the day’s stress.',
  },
  {
    id: 'pr-act-7',
    category: 'activity',
    type: 'activity',
    emoji: '✍️',
    text: 'The 3-Word Love Letter Challenge',
    subtext: 'Without discussing beforehand, write 3 words that capture how much you cherish your partner right now.',
  },

  // MEMORIES & NOSTALGIA
  {
    id: 'pr-mem-1',
    category: 'memories',
    type: 'question',
    emoji: '📸',
    text: 'What was your very first impression of me the first time we ever saw or spoke to each other?',
    subtext: 'Be completely honest! What caught your eye or surprised you?',
  },
  {
    id: 'pr-mem-2',
    category: 'memories',
    type: 'question',
    emoji: '☕',
    text: 'What is your favorite memory from our very first official date together?',
    subtext: 'What were we wearing, what were we talking about, and how nervous were you?',
  },
  {
    id: 'pr-mem-3',
    category: 'memories',
    type: 'question',
    emoji: '🗺️',
    text: 'What is the most unforgettable trip, weekend getaway, or spontaneous drive we have ever taken?',
    subtext: 'What made that adventure feel so uniquely magical?',
  },
  {
    id: 'pr-mem-4',
    category: 'memories',
    type: 'question',
    emoji: '😂',
    text: 'What is a time we both laughed so hard together that our stomachs ached or tears came out?',
  },
  {
    id: 'pr-mem-5',
    category: 'memories',
    type: 'question',
    emoji: '🌧️',
    text: 'Think of a challenging day or stressful time we got through together. How did it make us stronger?',
  },
  {
    id: 'pr-mem-6',
    category: 'memories',
    type: 'question',
    emoji: '🎁',
    text: 'What is the most meaningful gift or surprise you have ever received from me?',
  },

  // DEEP & HEART-TO-HEART
  {
    id: 'pr-deep-1',
    category: 'deep',
    type: 'question',
    emoji: '💎',
    text: 'In what ways do you feel you have grown or blossomed as a person since we became a couple?',
    subtext: 'How has our love shaped the way you see the world and yourself?',
  },
  {
    id: 'pr-deep-2',
    category: 'deep',
    type: 'question',
    emoji: '🛡️',
    text: 'What makes you feel safest and most deeply understood when you are with me?',
  },
  {
    id: 'pr-deep-3',
    category: 'deep',
    type: 'question',
    emoji: '🌱',
    text: 'What is one personal goal or passion of yours that you would love my help and encouragement with this year?',
  },
  {
    id: 'pr-deep-4',
    category: 'deep',
    type: 'question',
    emoji: '🌊',
    text: 'When you are feeling overwhelmed or low, what is the best way for me to comfort and support you?',
  },
  {
    id: 'pr-deep-5',
    category: 'deep',
    type: 'question',
    emoji: '🕯️',
    text: 'What is a fear or vulnerability you have overcome because of the trust we built together?',
  },
  {
    id: 'pr-deep-6',
    category: 'deep',
    type: 'question',
    emoji: '⭐',
    text: 'What is a value or quality about our relationship that you are most proud of protecting?',
  },

  // FUTURE & DREAMS
  {
    id: 'pr-fut-1',
    category: 'future',
    type: 'question',
    emoji: '🏡',
    text: 'What does our dream cozy Sunday morning look like 5 years from today?',
    subtext: 'Where are we living, what are we eating, and what is playing in the background?',
  },
  {
    id: 'pr-fut-2',
    category: 'future',
    type: 'question',
    emoji: '🌍',
    text: 'What is the number #1 bucket list adventure or destination we must experience together before we grow old?',
  },
  {
    id: 'pr-fut-3',
    category: 'future',
    type: 'question',
    emoji: '🥂',
    text: 'How do you imagine we will celebrate our 25th or 50th relationship anniversary in the future?',
  },
  {
    id: 'pr-fut-4',
    category: 'future',
    type: 'question',
    emoji: '🐾',
    text: 'What is a hobby, creative project, or activity you want us to learn or master together as a couple?',
  },
  {
    id: 'pr-fut-5',
    category: 'future',
    type: 'question',
    emoji: '✨',
    text: 'If we could build our dream home together, what is one quirky or romantic room we would design?',
  },
];

/**
 * Deterministically pick a daily prompt for a specific date (YYYY-MM-DD)
 */
export function getDailyPromptForDate(dateStr: string): PromptTemplate {
  // Simple deterministic hash based on date string
  let hash = 0;
  for (let i = 0; i < dateStr.length; i++) {
    hash = (hash << 5) - hash + dateStr.charCodeAt(i);
    hash |= 0;
  }
  const index = Math.abs(hash) % PROMPT_TEMPLATES.length;
  return PROMPT_TEMPLATES[index] || PROMPT_TEMPLATES[0];
}

/**
 * Pick a random prompt (optionally from a specific category)
 */
export function getRandomPrompt(
  excludeId?: string,
  category?: PromptCategory
): PromptTemplate {
  const pool = PROMPT_TEMPLATES.filter((p) => {
    if (category && p.category !== category) return false;
    if (excludeId && p.id === excludeId && PROMPT_TEMPLATES.length > 1) return false;
    return true;
  });

  if (pool.length === 0) return PROMPT_TEMPLATES[0];
  const idx = Math.floor(Math.random() * pool.length);
  return pool[idx];
}

/**
 * Calculate connection streak: consecutive days with answered prompts up to today
 */
export function calculatePromptStreak(entries: DailyPromptEntry[]): {
  streak: number;
  completedToday: boolean;
  totalAnswered: number;
} {
  const completedEntries = entries.filter(
    (e) => e.isCompleted || e.userAnswer?.trim() || e.partnerAnswer?.trim() || e.jointAnswer?.trim()
  );

  const totalAnswered = completedEntries.length;
  if (totalAnswered === 0) {
    return { streak: 0, completedToday: false, totalAnswered: 0 };
  }

  const todayStr = new Date().toISOString().split('T')[0];
  const completedDates = new Set(completedEntries.map((e) => e.date));

  const completedToday = completedDates.has(todayStr);

  // Check yesterday
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  const yesterdayStr = yesterday.toISOString().split('T')[0];

  // If not completed today and not completed yesterday, streak is 0
  if (!completedToday && !completedDates.has(yesterdayStr)) {
    return { streak: 0, completedToday: false, totalAnswered };
  }

  // Count backwards
  let streak = 0;
  const cursor = new Date();
  if (!completedToday) {
    cursor.setDate(cursor.getDate() - 1);
  }

  while (true) {
    const curStr = cursor.toISOString().split('T')[0];
    if (completedDates.has(curStr)) {
      streak++;
      cursor.setDate(cursor.getDate() - 1);
    } else {
      break;
    }
  }

  return { streak, completedToday, totalAnswered };
}
