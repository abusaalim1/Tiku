import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import {
  getFirestore,
  doc,
  getDocFromServer,
  collection,
  onSnapshot,
  setDoc,
  deleteDoc,
  getDocs,
  writeBatch
} from 'firebase/firestore';
import firebaseConfig from '../../firebase-applet-config.json';
import { CoupleProfile, Category, Snap } from '../types';
import { compressAvatar, compressSnapPhoto, sanitizePhotoString } from '../utils/imageUtils';

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);
export const auth = getAuth(app);

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

// Test initial connection to Firestore asynchronously
async function testConnection() {
  try {
    await getDocFromServer(doc(db, 'test', 'connection'));
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    if (msg.includes('offline') || msg.includes('unavailable') || msg.includes('Could not reach')) {
      console.info('Firestore client operating in offline/reconnecting mode.');
    } else {
      console.warn('Firestore initial connection status:', msg);
    }
  }
}
setTimeout(testConnection, 1200);

// --- FIRESTORE SERVICES FOR TUCHI APP ---

const PROFILE_DOC_ID = 'main_couple';

export function subscribeProfile(
  onUpdate: (profile: CoupleProfile) => void,
  fallbackProfile: CoupleProfile
) {
  const profileRef = doc(db, 'profiles', PROFILE_DOC_ID);
  return onSnapshot(
    profileRef,
    (snapshot) => {
      if (snapshot.exists()) {
        onUpdate(snapshot.data() as CoupleProfile);
      } else {
        // Prepare sanitized fallback profile to avoid size errors on initial write
        const safeFallback: CoupleProfile = {
          ...fallbackProfile,
          userPhotoUrl: sanitizePhotoString(fallbackProfile.userPhotoUrl, 150000),
          partnerPhotoUrl: sanitizePhotoString(fallbackProfile.partnerPhotoUrl, 150000),
        };
        saveProfileToFirestore(safeFallback).catch((err) =>
          console.warn('Notice: Could not save fallback profile to firestore:', err?.message || err)
        );
        onUpdate(safeFallback);
      }
    },
    (error) => {
      console.info('Profile snapshot operating in offline mode:', error?.message || error);
      onUpdate(fallbackProfile);
    }
  );
}

export async function saveProfileToFirestore(profile: CoupleProfile): Promise<void> {
  const path = `profiles/${PROFILE_DOC_ID}`;
  try {
    let userPhotoUrl = profile.userPhotoUrl ? await compressAvatar(profile.userPhotoUrl) : '';
    let partnerPhotoUrl = profile.partnerPhotoUrl ? await compressAvatar(profile.partnerPhotoUrl) : '';

    // Extra safety length check
    userPhotoUrl = sanitizePhotoString(userPhotoUrl, 200000);
    partnerPhotoUrl = sanitizePhotoString(partnerPhotoUrl, 200000);

    const safeProfile: CoupleProfile = {
      ...profile,
      userPhotoUrl,
      partnerPhotoUrl,
    };

    await setDoc(doc(db, 'profiles', PROFILE_DOC_ID), safeProfile);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export function subscribeCategories(
  onUpdate: (categories: Category[]) => void,
  fallbackCategories: Category[]
) {
  const categoriesCol = collection(db, 'categories');
  return onSnapshot(
    categoriesCol,
    (snapshot) => {
      if (!snapshot.empty) {
        const cats: Category[] = snapshot.docs.map((d) => d.data() as Category);
        onUpdate(cats);
      } else {
        // Seed initial default categories if empty
        const batch = writeBatch(db);
        fallbackCategories.forEach((cat) => {
          batch.set(doc(db, 'categories', cat.id), cat);
        });
        batch.commit().catch((err) =>
          console.warn('Notice: Could not seed initial categories to firestore:', err?.message || err)
        );
        onUpdate(fallbackCategories);
      }
    },
    (error) => {
      console.info('Categories snapshot operating in offline mode:', error?.message || error);
      onUpdate(fallbackCategories);
    }
  );
}

export async function saveCategoryToFirestore(category: Category): Promise<void> {
  const path = `categories/${category.id}`;
  try {
    await setDoc(doc(db, 'categories', category.id), category);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export function subscribeSnaps(onUpdate: (snaps: Snap[]) => void) {
  const snapsCol = collection(db, 'snaps');
  return onSnapshot(
    snapsCol,
    (snapshot) => {
      const snaps: Snap[] = snapshot.docs.map((d) => d.data() as Snap);
      // Sort newest first
      snaps.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      onUpdate(snaps);
    },
    (error) => {
      console.info('Snaps snapshot operating in offline mode:', error?.message || error);
    }
  );
}

export async function saveSnapToFirestore(snap: Snap): Promise<void> {
  const path = `snaps/${snap.id}`;
  try {
    let imageUrl = snap.imageUrl ? await compressSnapPhoto(snap.imageUrl) : '';
    imageUrl = sanitizePhotoString(imageUrl, 350000);

    const safeSnap: Snap = {
      ...snap,
      imageUrl,
    };
    await setDoc(doc(db, 'snaps', snap.id), safeSnap);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function deleteSnapFromFirestore(snapId: string): Promise<void> {
  const path = `snaps/${snapId}`;
  try {
    await deleteDoc(doc(db, 'snaps', snapId));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

export async function clearAllSnapsFromFirestore(): Promise<void> {
  const path = 'snaps';
  try {
    const snapshot = await getDocs(collection(db, 'snaps'));
    const batch = writeBatch(db);
    snapshot.docs.forEach((d) => {
      batch.delete(d.ref);
    });
    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}
