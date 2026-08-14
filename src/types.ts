export interface CoupleProfile {
  userName: string;
  partnerName: string;
  startDate: string; // YYYY-MM-DD
  userPhotoUrl: string;
  partnerPhotoUrl: string;
  isOnboarded: boolean;
  createdAt: string;
}

export interface Category {
  id: string;
  name: string;
  iconName: string;
  color: string; // Hex color
}

export interface Snap {
  id: string;
  categoryId: string;
  imageUrl: string;
  caption?: string;
  description?: string; // Story / Details of what happened
  location?: string;
  snapDate: string; // YYYY-MM-DD
  rotationDeg: number; // -4 to +4 for polaroid tilt
  favorite?: boolean;
  createdAt: string;
}

export type NavTab = 'home' | 'albums' | 'memories' | 'us';

export interface Milestone {
  id: string;
  days: number;
  label: string;
  subtitle: string;
  iconName: string;
  reached: boolean;
  targetDate: string;
  daysRemaining: number;
  promptQuestion: string;
  rewardBadge: string;
}

export interface MilestoneNote {
  milestoneId: string;
  note: string;
  updatedAt: string;
}

export interface AnniversaryItem {
  type: 'monthly' | 'annual';
  label: string;
  targetDate: string;
  daysRemaining: number;
  isToday: boolean;
  count: number; // e.g. 6 (6th month) or 2 (2nd year)
  countLabel: string; // e.g. "6th Month" or "2nd Year"
  badgeEmoji: string;
}

export interface UpcomingAnniversarySummary {
  monthly: AnniversaryItem;
  annual: AnniversaryItem;
  nextClosest: AnniversaryItem;
  isAnyAnniversaryToday: boolean;
}
