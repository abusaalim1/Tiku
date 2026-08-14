// Utility functions for dates and couple day calculations

export function calculateDaysTogether(startDateStr: string): number {
  if (!startDateStr) return 1;
  const start = new Date(startDateStr);
  const now = new Date();
  
  // Set both to midnight UTC to compare full days
  start.setHours(0, 0, 0, 0);
  now.setHours(0, 0, 0, 0);
  
  const diffTime = Math.abs(now.getTime() - start.getTime());
  const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24)) + 1; // Including start day as Day 1
  return diffDays > 0 ? diffDays : 1;
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '';
  const date = new Date(dateStr + 'T00:00:00');
  if (isNaN(date.getTime())) return dateStr;
  
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).format(date);
}

export function formatMonthYear(dateStr: string): string {
  if (!dateStr) return '';
  const date = new Date(dateStr + 'T00:00:00');
  if (isNaN(date.getTime())) return dateStr;
  
  return new Intl.DateTimeFormat('en-US', {
    month: 'long',
    year: 'numeric'
  }).format(date);
}

export function getTodayDateString(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function calculateMilestones(startDateStr: string) {
  const currentDays = calculateDaysTogether(startDateStr);
  const startDate = new Date(startDateStr || getTodayDateString());

  const targets = [
    {
      id: 'm-30',
      days: 30,
      label: '1st Month Honeymoon',
      subtitle: 'First 30 days of sweet memories',
      iconName: 'Heart',
      promptQuestion: 'What was the exact moment you realized you truly liked each other?',
      rewardBadge: '🌸 Sweet Spark'
    },
    {
      id: 'm-50',
      days: 50,
      label: '50 Golden Days',
      subtitle: 'Building little inside jokes and routines',
      iconName: 'Sparkles',
      promptQuestion: 'What is your favorite inside joke or nickname for each other?',
      rewardBadge: '✨ Golden Bond'
    },
    {
      id: 'm-100',
      days: 100,
      label: '100 Days Milestone',
      subtitle: 'A major classic couple milestone celebrated!',
      iconName: 'Award',
      promptQuestion: 'What has been the most memorable date or surprise so far?',
      rewardBadge: '💖 Century of Love'
    },
    {
      id: 'm-180',
      days: 180,
      label: 'Half-Year (6 Months)',
      subtitle: '6 wonderful months side by side',
      iconName: 'Star',
      promptQuestion: 'What new thing have you learned about your partner since day 1?',
      rewardBadge: '⭐ Half-Year Harmony'
    },
    {
      id: 'm-365',
      days: 365,
      label: '1st Year Anniversary',
      subtitle: '365 days of laughter, warmth & love',
      iconName: 'Gift',
      promptQuestion: 'Write a sweet 1-year love note or anniversary promise to each other.',
      rewardBadge: '🏆 365-Day Champion'
    },
    {
      id: 'm-500',
      days: 500,
      label: '500 Days Milestone',
      subtitle: 'Over 500 days together and counting',
      iconName: 'Sun',
      promptQuestion: 'What is your dream vacation or future dream goal together?',
      rewardBadge: '☀️ 500 Sunshines'
    },
    {
      id: 'm-730',
      days: 730,
      label: '2 Years Anniversary',
      subtitle: 'Two whole years of pure companionship',
      iconName: 'Cake',
      promptQuestion: 'What is one song, movie, or place that will always belong to just the two of you?',
      rewardBadge: '👑 Double Decade Duo'
    },
    {
      id: 'm-1000',
      days: 1000,
      label: '1,000 Days Club',
      subtitle: 'A legendary quadruple-digit relationship milestone',
      iconName: 'Compass',
      promptQuestion: 'Looking back over 1,000 days, what are you most grateful for?',
      rewardBadge: '💎 1K Diamond Love'
    },
    {
      id: 'm-1095',
      days: 1095,
      label: '3 Years Anniversary',
      subtitle: 'Tri-annual celebration of love and devotion',
      iconName: 'Users',
      promptQuestion: 'How has your relationship helped both of you grow as people?',
      rewardBadge: '🌟 Platinum Soulmates'
    },
  ];

  return targets.map((t) => {
    const targetDate = new Date(startDate);
    targetDate.setDate(startDate.getDate() + (t.days - 1));
    const reached = currentDays >= t.days;
    const daysRemaining = Math.max(0, t.days - currentDays);

    return {
      id: t.id,
      days: t.days,
      label: t.label,
      subtitle: t.subtitle,
      iconName: t.iconName,
      reached,
      targetDate: targetDate.toISOString().split('T')[0],
      daysRemaining,
      promptQuestion: t.promptQuestion,
      rewardBadge: t.rewardBadge,
    };
  });
}

export function getRandomTilt(): number {
  // Return a random float between -3.8 and 3.8
  const tilt = (Math.random() * 7.6) - 3.8;
  return Number(tilt.toFixed(1));
}
