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
  imageUrls?: string[]; // Multiple photos support (2-3+ photos)
  caption?: string;
  description?: string; // Story / Details of what happened
  location?: string;
  snapDate: string; // YYYY-MM-DD
  rotationDeg: number; // -4 to +4 for polaroid tilt
  favorite?: boolean;
  createdAt: string;
}

export interface SnapDraft {
  id: string;
  categoryId: string;
  imageUrls: string[];
  caption: string;
  description: string;
  location: string;
  snapDate: string;
  updatedAt: string;
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

export type PromptCategory = 'romantic' | 'fun' | 'activity' | 'memories' | 'deep' | 'future';

export interface PromptTemplate {
  id: string;
  category: PromptCategory;
  type: 'question' | 'activity';
  text: string;
  subtext?: string;
  emoji: string;
  suggestedAction?: string;
}

export interface DailyPromptEntry {
  id: string; // e.g. "dp-2026-08-14" or "dp-1738..."
  date: string; // YYYY-MM-DD
  promptId: string;
  category: PromptCategory;
  type: 'question' | 'activity';
  question: string;
  userAnswer?: string;
  partnerAnswer?: string;
  jointAnswer?: string;
  imageUrl?: string;
  isCompleted: boolean;
  completedAt?: string;
  createdAt: string;
  convertedToSnapId?: string;
}
