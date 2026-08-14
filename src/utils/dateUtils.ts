import { AnniversaryItem, UpcomingAnniversarySummary } from '../types';

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

export function getOrdinalSuffix(n: number): string {
  const j = n % 10;
  const k = n % 100;
  if (j === 1 && k !== 11) {
    return `${n}st`;
  }
  if (j === 2 && k !== 12) {
    return `${n}nd`;
  }
  if (j === 3 && k !== 13) {
    return `${n}rd`;
  }
  return `${n}th`;
}

/**
 * Calculates upcoming monthly and annual anniversaries based on couple's start date
 */
export function calculateUpcomingAnniversaries(startDateStr: string): UpcomingAnniversarySummary {
  const defaultDateStr = startDateStr || getTodayDateString();
  const [sYear, sMonth, sDay] = defaultDateStr.split('-').map(Number);

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // 1. Calculate next Monthly Anniversary
  let monthlyItem: AnniversaryItem = {
    type: 'monthly',
    label: 'Monthly Anniversary',
    targetDate: defaultDateStr,
    daysRemaining: 0,
    isToday: false,
    count: 1,
    countLabel: '1st Month',
    badgeEmoji: '🌸',
  };

  // Find smallest monthOffset >= 1 where targetDate >= today (or monthOffset=0 if start date is in the future)
  for (let monthOffset = 1; monthOffset <= 1200; monthOffset++) {
    const totalMonths = (sMonth - 1) + monthOffset;
    const targetYear = sYear + Math.floor(totalMonths / 12);
    const targetMonth = totalMonths % 12; // 0-indexed

    // Calculate max days in that target month
    const daysInMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    const clampedDay = Math.min(sDay, daysInMonth);

    const targetDateObj = new Date(targetYear, targetMonth, clampedDay);
    targetDateObj.setHours(0, 0, 0, 0);

    const diffDays = Math.round((targetDateObj.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays >= 0) {
      const monthStr = String(targetMonth + 1).padStart(2, '0');
      const dayStr = String(clampedDay).padStart(2, '0');
      const dateIso = `${targetYear}-${monthStr}-${dayStr}`;
      const ordinal = getOrdinalSuffix(monthOffset);

      monthlyItem = {
        type: 'monthly',
        label: `${ordinal} Monthiversary`,
        targetDate: dateIso,
        daysRemaining: diffDays,
        isToday: diffDays === 0,
        count: monthOffset,
        countLabel: `${ordinal} Month`,
        badgeEmoji: diffDays === 0 ? '🎉' : '💖',
      };
      break;
    }
  }

  // 2. Calculate next Annual Anniversary
  let annualItem: AnniversaryItem = {
    type: 'annual',
    label: '1st Year Anniversary',
    targetDate: defaultDateStr,
    daysRemaining: 0,
    isToday: false,
    count: 1,
    countLabel: '1st Year',
    badgeEmoji: '💍',
  };

  for (let yearOffset = 1; yearOffset <= 100; yearOffset++) {
    const targetYear = sYear + yearOffset;
    const targetMonth = sMonth - 1;

    const daysInMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    const clampedDay = Math.min(sDay, daysInMonth);

    const targetDateObj = new Date(targetYear, targetMonth, clampedDay);
    targetDateObj.setHours(0, 0, 0, 0);

    const diffDays = Math.round((targetDateObj.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays >= 0) {
      const monthStr = String(targetMonth + 1).padStart(2, '0');
      const dayStr = String(clampedDay).padStart(2, '0');
      const dateIso = `${targetYear}-${monthStr}-${dayStr}`;
      const ordinal = getOrdinalSuffix(yearOffset);

      annualItem = {
        type: 'annual',
        label: `${ordinal} Year Anniversary`,
        targetDate: dateIso,
        daysRemaining: diffDays,
        isToday: diffDays === 0,
        count: yearOffset,
        countLabel: `${ordinal} Year`,
        badgeEmoji: diffDays === 0 ? '👑' : '✨',
      };
      break;
    }
  }

  // Determine closest next anniversary
  const nextClosest = annualItem.isToday
    ? annualItem
    : monthlyItem.isToday
    ? monthlyItem
    : annualItem.daysRemaining < monthlyItem.daysRemaining
    ? annualItem
    : monthlyItem;

  const isAnyAnniversaryToday = monthlyItem.isToday || annualItem.isToday;

  return {
    monthly: monthlyItem,
    annual: annualItem,
    nextClosest,
    isAnyAnniversaryToday,
  };
}

