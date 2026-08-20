import { SnapDraft } from '../types';

const DRAFTS_KEY = 'tuchi_snap_drafts';

export const getSavedDrafts = (): SnapDraft[] => {
  try {
    const raw = localStorage.getItem(DRAFTS_KEY);
    if (!raw) return [];
    return JSON.parse(raw);
  } catch (err) {
    console.error('Failed to parse drafts:', err);
    return [];
  }
};

export const saveDraft = (draft: Omit<SnapDraft, 'id' | 'updatedAt'> & { id?: string }): SnapDraft => {
  const currentDrafts = getSavedDrafts();
  const draftId = draft.id || `draft-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;
  
  const newDraft: SnapDraft = {
    id: draftId,
    categoryId: draft.categoryId,
    imageUrls: draft.imageUrls,
    caption: draft.caption || '',
    description: draft.description || '',
    location: draft.location || '',
    snapDate: draft.snapDate,
    updatedAt: new Date().toISOString(),
  };

  const existingIndex = currentDrafts.findIndex((d) => d.id === draftId);
  let updatedDrafts: SnapDraft[];
  
  if (existingIndex >= 0) {
    updatedDrafts = [...currentDrafts];
    updatedDrafts[existingIndex] = newDraft;
  } else {
    updatedDrafts = [newDraft, ...currentDrafts];
  }

  try {
    localStorage.setItem(DRAFTS_KEY, JSON.stringify(updatedDrafts));
  } catch (err) {
    console.error('Failed to save draft to localStorage:', err);
  }

  return newDraft;
};

export const deleteDraft = (draftId: string): void => {
  const currentDrafts = getSavedDrafts();
  const filtered = currentDrafts.filter((d) => d.id !== draftId);
  try {
    localStorage.setItem(DRAFTS_KEY, JSON.stringify(filtered));
  } catch (err) {
    console.error('Failed to delete draft:', err);
  }
};
