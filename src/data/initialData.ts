import { Category, CoupleProfile, Snap } from '../types';

export const DEFAULT_PROFILE: CoupleProfile = {
  userName: '',
  partnerName: '',
  startDate: new Date().toISOString().split('T')[0],
  userPhotoUrl: '',
  partnerPhotoUrl: '',
  isOnboarded: false,
  createdAt: new Date().toISOString(),
};

export const DEFAULT_CATEGORIES: Category[] = [
  { id: 'cat-dates', name: 'Dates', iconName: 'Coffee', color: '#E8AFC0' },
  { id: 'cat-kiss', name: 'First Kiss', iconName: 'Heart', color: '#FFDEE5' },
  { id: 'cat-trips', name: 'Trips', iconName: 'Plane', color: '#BFE3D0' },
  { id: 'cat-anniversary', name: 'Anniversaries', iconName: 'Gift', color: '#D9A15E' },
  { id: 'cat-random', name: 'Random Cute', iconName: 'Smile', color: '#7A5566' },
];

export const INITIAL_SNAPS: Snap[] = [];
