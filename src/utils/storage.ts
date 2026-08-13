import { Category, CoupleProfile, Snap } from '../types';
import { DEFAULT_CATEGORIES, DEFAULT_PROFILE, INITIAL_SNAPS } from '../data/initialData';
import { sanitizePhotoString } from './imageUtils';

const KEYS = {
  PROFILE: 'tuchi_couple_profile',
  CATEGORIES: 'tuchi_categories',
  SNAPS: 'tuchi_snaps',
};

export const loadProfile = (): CoupleProfile => {
  try {
    const saved = localStorage.getItem(KEYS.PROFILE);
    if (saved) {
      const parsed: CoupleProfile = JSON.parse(saved);
      return {
        ...parsed,
        userPhotoUrl: sanitizePhotoString(parsed.userPhotoUrl, 150000),
        partnerPhotoUrl: sanitizePhotoString(parsed.partnerPhotoUrl, 150000),
      };
    }
  } catch (e) {
    console.error('Failed to load couple profile from storage', e);
  }
  return DEFAULT_PROFILE;
};

export const saveProfile = (profile: CoupleProfile): void => {
  try {
    const safeProfile: CoupleProfile = {
      ...profile,
      userPhotoUrl: sanitizePhotoString(profile.userPhotoUrl, 150000),
      partnerPhotoUrl: sanitizePhotoString(profile.partnerPhotoUrl, 150000),
    };
    localStorage.setItem(KEYS.PROFILE, JSON.stringify(safeProfile));
  } catch (e) {
    console.error('Failed to save profile to storage', e);
  }
};

export const loadCategories = (): Category[] => {
  try {
    const saved = localStorage.getItem(KEYS.CATEGORIES);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed;
      }
    }
  } catch (e) {
    console.error('Failed to load categories from storage', e);
  }
  return DEFAULT_CATEGORIES;
};

export const saveCategories = (categories: Category[]): void => {
  try {
    localStorage.setItem(KEYS.CATEGORIES, JSON.stringify(categories));
  } catch (e) {
    console.error('Failed to save categories to storage', e);
  }
};

export const loadSnaps = (): Snap[] => {
  try {
    const saved = localStorage.getItem(KEYS.SNAPS);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed)) {
        return parsed.map((s) => ({
          ...s,
          imageUrl: sanitizePhotoString(s.imageUrl, 350000),
        }));
      }
    }
  } catch (e) {
    console.error('Failed to load snaps from storage', e);
  }
  return INITIAL_SNAPS;
};

export const saveSnaps = (snaps: Snap[]): void => {
  try {
    localStorage.setItem(KEYS.SNAPS, JSON.stringify(snaps));
  } catch (e) {
    console.error('Failed to save snaps to storage', e);
  }
};

export const resetToDemoData = () => {
  localStorage.setItem(KEYS.PROFILE, JSON.stringify({ ...DEFAULT_PROFILE, isOnboarded: true }));
  localStorage.setItem(KEYS.CATEGORIES, JSON.stringify(DEFAULT_CATEGORIES));
  localStorage.setItem(KEYS.SNAPS, JSON.stringify(INITIAL_SNAPS));
};

export const clearAllData = () => {
  localStorage.removeItem(KEYS.PROFILE);
  localStorage.removeItem(KEYS.CATEGORIES);
  localStorage.removeItem(KEYS.SNAPS);
};
