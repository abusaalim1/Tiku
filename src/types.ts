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
  days: number;
  label: string;
  iconName: string;
  reached: boolean;
  targetDate: string;
}
