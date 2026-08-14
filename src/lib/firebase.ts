import { initializeApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInAnonymously,
  sendPasswordResetEmail,
  signOut,
  onAuthStateChanged,
  User,
} from 'firebase/auth';
import {
  getFirestore,
  doc,
  collection,
  onSnapshot,
  setDoc,
  deleteDoc,
  getDocs,
  writeBatch,
} from 'firebase/firestore';
import firebaseConfig from '../../firebase-applet-config.json';
import { CoupleProfile, Category, Snap, MilestoneNote, DailyPromptEntry } from '../types';
import { compressAvatar, compressSnapPhoto, sanitizePhotoString } from '../utils/imageUtils';

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);
export const auth = getAuth(app);

export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    isAnonymous?: boolean | null;
    tenantId?: string | null;
    providerInfo?: {
      providerId?: string | null;
      email?: string | null;
    }[];
  };
}

export function handleFirestoreError(error: unknown, operationType: OperationType, path: string | null) {
  const errInfo: FirestoreErrorInfo = {
    error: error instanceof Error ? error.message : String(error),
    authInfo: {
      userId: auth.currentUser?.uid,
      email: auth.currentUser?.email,
      emailVerified: auth.currentUser?.emailVerified,
      isAnonymous: auth.currentUser?.isAnonymous,
      tenantId: auth.currentUser?.tenantId,
      providerInfo: auth.currentUser?.providerData?.map((provider) => ({
        providerId: provider.providerId,
        email: provider.email,
      })) || [],
    },
    operationType,
    path,
  };
  console.error('Firestore Error: ', JSON.stringify(errInfo));
  throw new Error(JSON.stringify(errInfo));
}

// --- FIREBASE AUTHENTICATION HELPERS ---

export async function loginWithGoogle(): Promise<User> {
  const result = await signInWithPopup(auth, googleProvider);
  return result.user;
}

export async function loginWithEmail(email: string, pass: string): Promise<User> {
  const result = await signInWithEmailAndPassword(auth, email.trim(), pass);
  return result.user;
}

export async function registerWithEmail(email: string, pass: string): Promise<User> {
  const result = await createUserWithEmailAndPassword(auth, email.trim(), pass);
  return result.user;
}

export async function sendPasswordReset(email: string): Promise<void> {
  await sendPasswordResetEmail(auth, email.trim());
}

export async function loginGuest(): Promise<User> {
  const result = await signInAnonymously(auth);
  return result.user;
}

export async function logoutUser(): Promise<void> {
  await signOut(auth);
}

export function subscribeAuth(callback: (user: User | null) => void) {
  return onAuthStateChanged(auth, callback);
}

// --- USER-ISOLATED FIRESTORE SERVICES ---

export function subscribeProfile(
  userId: string,
  onUpdate: (profile: CoupleProfile) => void,
  fallbackProfile: CoupleProfile
) {
  if (!userId) return () => {};
  const profileRef = doc(db, 'users', userId, 'profile', 'couple');
  return onSnapshot(
    profileRef,
    (snapshot) => {
      if (snapshot.exists()) {
        onUpdate(snapshot.data() as CoupleProfile);
      } else {
        const safeFallback: CoupleProfile = {
          ...fallbackProfile,
          userPhotoUrl: sanitizePhotoString(fallbackProfile.userPhotoUrl, 150000),
          partnerPhotoUrl: sanitizePhotoString(fallbackProfile.partnerPhotoUrl, 150000),
        };
        saveProfileToFirestore(userId, safeFallback).catch((err) =>
          console.warn('Notice: Could not save initial profile to firestore:', err?.message || err)
        );
        onUpdate(safeFallback);
      }
    },
    (error) => {
      console.info('Profile snapshot offline/info:', error?.message || error);
      onUpdate(fallbackProfile);
    }
  );
}

export async function saveProfileToFirestore(userId: string, profile: CoupleProfile): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/profile/couple`;
  try {
    let userPhotoUrl = profile.userPhotoUrl ? await compressAvatar(profile.userPhotoUrl) : '';
    let partnerPhotoUrl = profile.partnerPhotoUrl ? await compressAvatar(profile.partnerPhotoUrl) : '';

    userPhotoUrl = sanitizePhotoString(userPhotoUrl, 200000);
    partnerPhotoUrl = sanitizePhotoString(partnerPhotoUrl, 200000);

    const safeProfile: CoupleProfile = {
      ...profile,
      userPhotoUrl,
      partnerPhotoUrl,
    };

    await setDoc(doc(db, 'users', userId, 'profile', 'couple'), safeProfile);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export function subscribeCategories(
  userId: string,
  onUpdate: (categories: Category[]) => void,
  fallbackCategories: Category[]
) {
  if (!userId) return () => {};
  const categoriesCol = collection(db, 'users', userId, 'categories');
  return onSnapshot(
    categoriesCol,
    (snapshot) => {
      if (!snapshot.empty) {
        const cats: Category[] = snapshot.docs.map((d) => d.data() as Category);
        onUpdate(cats);
      } else {
        // Seed default categories for this user
        const batch = writeBatch(db);
        fallbackCategories.forEach((cat) => {
          batch.set(doc(db, 'users', userId, 'categories', cat.id), cat);
        });
        batch.commit().catch((err) =>
          console.warn('Notice: Could not seed initial categories:', err?.message || err)
        );
        onUpdate(fallbackCategories);
      }
    },
    (error) => {
      console.info('Categories snapshot offline/info:', error?.message || error);
      onUpdate(fallbackCategories);
    }
  );
}

export async function saveCategoryToFirestore(userId: string, category: Category): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/categories/${category.id}`;
  try {
    await setDoc(doc(db, 'users', userId, 'categories', category.id), category);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export function subscribeSnaps(userId: string, onUpdate: (snaps: Snap[]) => void) {
  if (!userId) return () => {};
  const snapsCol = collection(db, 'users', userId, 'snaps');
  return onSnapshot(
    snapsCol,
    (snapshot) => {
      const snaps: Snap[] = snapshot.docs.map((d) => d.data() as Snap);
      snaps.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      onUpdate(snaps);
    },
    (error) => {
      console.info('Snaps snapshot offline/info:', error?.message || error);
    }
  );
}

export async function saveSnapToFirestore(userId: string, snap: Snap): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/snaps/${snap.id}`;
  try {
    let imageUrl = snap.imageUrl ? await compressSnapPhoto(snap.imageUrl) : '';
    imageUrl = sanitizePhotoString(imageUrl, 350000);

    const safeSnap: Snap = {
      ...snap,
      imageUrl,
    };
    await setDoc(doc(db, 'users', userId, 'snaps', snap.id), safeSnap);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function deleteSnapFromFirestore(userId: string, snapId: string): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/snaps/${snapId}`;
  try {
    await deleteDoc(doc(db, 'users', userId, 'snaps', snapId));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

export async function clearAllSnapsFromFirestore(userId: string): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/snaps`;
  try {
    const snapshot = await getDocs(collection(db, 'users', userId, 'snaps'));
    const batch = writeBatch(db);
    snapshot.docs.forEach((d) => {
      batch.delete(d.ref);
    });
    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

export function subscribeMilestoneNotes(userId: string, onUpdate: (notes: Record<string, string>) => void) {
  if (!userId) return () => {};
  const notesCol = collection(db, 'users', userId, 'milestoneNotes');
  return onSnapshot(
    notesCol,
    (snapshot) => {
      const map: Record<string, string> = {};
      snapshot.docs.forEach((d) => {
        const data = d.data() as MilestoneNote;
        map[data.milestoneId || d.id] = data.note;
      });
      onUpdate(map);
    },
    (error) => {
      console.info('Milestone notes snapshot offline/info:', error?.message || error);
    }
  );
}

export async function saveMilestoneNoteToFirestore(userId: string, milestoneId: string, note: string): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/milestoneNotes/${milestoneId}`;
  try {
    const data: MilestoneNote = {
      milestoneId,
      note,
      updatedAt: new Date().toISOString(),
    };
    await setDoc(doc(db, 'users', userId, 'milestoneNotes', milestoneId), data);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export function subscribeDailyPrompts(
  userId: string,
  onUpdate: (prompts: DailyPromptEntry[]) => void
) {
  if (!userId) return () => {};
  const promptsCol = collection(db, 'users', userId, 'dailyPrompts');
  return onSnapshot(
    promptsCol,
    (snapshot) => {
      const list: DailyPromptEntry[] = snapshot.docs.map((d) => d.data() as DailyPromptEntry);
      list.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
      onUpdate(list);
    },
    (error) => {
      console.info('Daily prompts snapshot offline/info:', error?.message || error);
    }
  );
}

export async function saveDailyPromptToFirestore(
  userId: string,
  promptEntry: DailyPromptEntry
): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/dailyPrompts/${promptEntry.id}`;
  try {
    let imageUrl = promptEntry.imageUrl ? await compressSnapPhoto(promptEntry.imageUrl) : '';
    imageUrl = sanitizePhotoString(imageUrl, 350000);

    const safeEntry: DailyPromptEntry = {
      ...promptEntry,
      imageUrl,
    };
    await setDoc(doc(db, 'users', userId, 'dailyPrompts', promptEntry.id), safeEntry);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function deleteDailyPromptFromFirestore(
  userId: string,
  promptId: string
): Promise<void> {
  if (!userId) return;
  const path = `users/${userId}/dailyPrompts/${promptId}`;
  try {
    await deleteDoc(doc(db, 'users', userId, 'dailyPrompts', promptId));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}
