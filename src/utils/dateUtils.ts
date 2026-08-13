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
  const startDate = new Date(startDateStr);

  const targets = [
    { days: 100, label: '100 Days of Love', iconName: 'Heart' },
    { days: 200, label: '200 Days Together', iconName: 'Sparkles' },
    { days: 365, label: '1 Year Anniversary', iconName: 'Gift' },
    { days: 500, label: '500 Days Milestone', iconName: 'Star' },
    { days: 730, label: '2 Years Anniversary', iconName: 'Cake' },
    { days: 1000, label: '1,000 Days of Memories', iconName: 'Sun' },
    { days: 1460, label: '4 Years Anniversary', iconName: 'Compass' },
  ];

  return targets.map(t => {
    const targetDate = new Date(startDate);
    targetDate.setDate(startDate.getDate() + (t.days - 1));
    
    return {
      days: t.days,
      label: t.label,
      iconName: t.iconName,
      reached: currentDays >= t.days,
      targetDate: targetDate.toISOString().split('T')[0]
    };
  });
}

export function getRandomTilt(): number {
  // Return a random float between -3.8 and 3.8
  const tilt = (Math.random() * 7.6) - 3.8;
  return Number(tilt.toFixed(1));
}
