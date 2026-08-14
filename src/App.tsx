import React, { useState, useEffect } from 'react';
import { User } from 'firebase/auth';
import { Category, CoupleProfile, DailyPromptEntry, NavTab, Snap } from './types';
import {
  loadProfile,
  saveProfile,
  loadCategories,
  saveCategories,
  loadSnaps,
  saveSnaps,
  loadDailyPrompts,
  saveDailyPrompts,
  resetToDemoData,
} from './utils/storage';
import {
  subscribeAuth,
  subscribeProfile,
  saveProfileToFirestore,
  subscribeCategories,
  saveCategoryToFirestore,
  subscribeSnaps,
  saveSnapToFirestore,
  deleteSnapFromFirestore,
  clearAllSnapsFromFirestore,
  subscribeMilestoneNotes,
  saveMilestoneNoteToFirestore,
  subscribeDailyPrompts,
  saveDailyPromptToFirestore,
  deleteDailyPromptFromFirestore,
  logoutUser,
} from './lib/firebase';
import { AuthScreen } from './components/AuthScreen';
import { Onboarding } from './components/Onboarding';
import { HomeGallery } from './components/HomeGallery';
import { AlbumsView } from './components/AlbumsView';
import { MemoriesTimelineView } from './components/MemoriesTimelineView';
import { UsProfileView } from './components/UsProfileView';
import { BottomNav } from './components/BottomNav';
import { AddSnapModal } from './components/AddSnapModal';
import { SnapDetailModal } from './components/SnapDetailModal';
import { CategoryModal } from './components/CategoryModal';

export default function App() {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState<boolean>(true);

  const [profile, setProfileState] = useState<CoupleProfile>(() => loadProfile());
  const [categories, setCategoriesState] = useState<Category[]>(() => loadCategories());
  const [snaps, setSnapsState] = useState<Snap[]>(() => loadSnaps());
  const [dailyPrompts, setDailyPrompts] = useState<DailyPromptEntry[]>(() => loadDailyPrompts());
  const [milestoneNotes, setMilestoneNotes] = useState<Record<string, string>>({});
  const [activeTab, setActiveTab] = useState<NavTab>('home');

  // Modals state
  const [isAddSnapOpen, setIsAddSnapOpen] = useState<boolean>(false);
  const [isAddCategoryOpen, setIsAddCategoryOpen] = useState<boolean>(false);
  const [selectedSnap, setSelectedSnap] = useState<Snap | null>(null);

  // 1. Subscribe to Firebase Authentication
  useEffect(() => {
    const unsubAuth = subscribeAuth((user) => {
      setCurrentUser(user);
      setIsAuthLoading(false);
    });

    return () => unsubAuth();
  }, []);

  // 2. Subscribe to real-time User-Scoped Firestore sync
  useEffect(() => {
    if (!currentUser?.uid) return;

    const userId = currentUser.uid;

    const unsubProfile = subscribeProfile(
      userId,
      (p) => {
        setProfileState(p);
        saveProfile(p);
      },
      loadProfile()
    );

    const unsubCategories = subscribeCategories(
      userId,
      (cats) => {
        setCategoriesState(cats);
        saveCategories(cats);
      },
      loadCategories()
    );

    const unsubSnaps = subscribeSnaps(userId, (sList) => {
      setSnapsState(sList);
      saveSnaps(sList);
    });

    const unsubMilestoneNotes = subscribeMilestoneNotes(userId, (notes) => {
      setMilestoneNotes(notes);
    });

    const unsubDailyPrompts = subscribeDailyPrompts(userId, (prompts) => {
      setDailyPrompts(prompts);
      saveDailyPrompts(prompts);
    });

    return () => {
      unsubProfile();
      unsubCategories();
      unsubSnaps();
      unsubMilestoneNotes();
      unsubDailyPrompts();
    };
  }, [currentUser?.uid]);

  // Update profile wrapper
  const updateProfile = (updated: CoupleProfile) => {
    setProfileState(updated);
    saveProfile(updated);
    if (currentUser?.uid) {
      saveProfileToFirestore(currentUser.uid, updated).catch(console.error);
    }
  };

  // Handlers
  const handleOnboardingComplete = (updatedProfile: CoupleProfile) => {
    updateProfile(updatedProfile);
  };

  const handleSaveSnap = (newSnapData: {
    categoryId: string;
    imageUrl: string;
    caption?: string;
    description?: string;
    location?: string;
    snapDate: string;
    rotationDeg: number;
  }) => {
    const newSnap: Snap = {
      id: `snap-${Date.now()}`,
      categoryId: newSnapData.categoryId,
      imageUrl: newSnapData.imageUrl,
      caption: newSnapData.caption,
      description: newSnapData.description,
      location: newSnapData.location,
      snapDate: newSnapData.snapDate,
      rotationDeg: newSnapData.rotationDeg,
      favorite: false,
      createdAt: new Date().toISOString(),
    };

    setSnapsState((prev) => [newSnap, ...prev]);
    if (currentUser?.uid) {
      saveSnapToFirestore(currentUser.uid, newSnap).catch(console.error);
    }
  };

  const handleToggleFavorite = (snapId: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    let updatedSnap: Snap | null = null;
    setSnapsState((prev) =>
      prev.map((s) => {
        if (s.id === snapId) {
          updatedSnap = { ...s, favorite: !s.favorite };
          return updatedSnap;
        }
        return s;
      })
    );

    if (updatedSnap && currentUser?.uid) {
      saveSnapToFirestore(currentUser.uid, updatedSnap).catch(console.error);
    }

    if (selectedSnap && selectedSnap.id === snapId) {
      setSelectedSnap((prev) => (prev ? { ...prev, favorite: !prev.favorite } : null));
    }
  };

  const handleDeleteSnap = (snapId: string) => {
    setSnapsState((prev) => prev.filter((s) => s.id !== snapId));
    if (currentUser?.uid) {
      deleteSnapFromFirestore(currentUser.uid, snapId).catch(console.error);
    }
  };

  const handleSaveCategory = (newCategoryData: { name: string; iconName: string; color: string }) => {
    const newCat: Category = {
      id: `cat-${Date.now()}`,
      name: newCategoryData.name,
      iconName: newCategoryData.iconName,
      color: newCategoryData.color,
    };

    setCategoriesState((prev) => [...prev, newCat]);
    if (currentUser?.uid) {
      saveCategoryToFirestore(currentUser.uid, newCat).catch(console.error);
    }
  };

  const handleSaveMilestoneNote = (milestoneId: string, note: string) => {
    setMilestoneNotes((prev) => ({ ...prev, [milestoneId]: note }));
    if (currentUser?.uid) {
      saveMilestoneNoteToFirestore(currentUser.uid, milestoneId, note).catch(console.error);
    }
  };

  const handleSavePromptEntry = (entry: DailyPromptEntry) => {
    setDailyPrompts((prev) => {
      const filtered = prev.filter((p) => p.id !== entry.id && p.date !== entry.date);
      const updated = [entry, ...filtered];
      saveDailyPrompts(updated);
      return updated;
    });
    if (currentUser?.uid) {
      saveDailyPromptToFirestore(currentUser.uid, entry).catch(console.error);
    }
  };

  const handleDeletePromptEntry = (entryId: string) => {
    setDailyPrompts((prev) => {
      const updated = prev.filter((p) => p.id !== entryId);
      saveDailyPrompts(updated);
      return updated;
    });
    if (currentUser?.uid) {
      deleteDailyPromptFromFirestore(currentUser.uid, entryId).catch(console.error);
    }
  };

  const handleResetData = () => {
    resetToDemoData();
    const defaultProf = loadProfile();
    updateProfile(defaultProf);
    if (currentUser?.uid) {
      clearAllSnapsFromFirestore(currentUser.uid).catch(console.error);
    }
  };

  const handleClearAllSnaps = () => {
    setSnapsState([]);
    saveSnaps([]);
    if (currentUser?.uid) {
      clearAllSnapsFromFirestore(currentUser.uid).catch(console.error);
    }
  };

  const handleSignOut = async () => {
    await logoutUser();
  };

  // Auth Loading State
  if (isAuthLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#FAF5F0]">
        <div className="text-center space-y-3">
          <div className="w-10 h-10 rounded-full border-3 border-[#FF4B72] border-t-transparent animate-spin mx-auto" />
          <p className="text-xs font-bold text-[#6E495B]">Opening secure couple vault...</p>
        </div>
      </div>
    );
  }

  // If user is not authenticated, show AuthScreen (Login/Signup/Guest)
  if (!currentUser) {
    return (
      <main className="min-h-screen selection:bg-[#FFE5EC]">
        <AuthScreen />
      </main>
    );
  }

  // If user hasn't completed onboarding, render Onboarding Screen
  if (!profile.isOnboarded) {
    return (
      <main className="min-h-screen selection:bg-[#FFE5EC]">
        <Onboarding initialProfile={profile} onComplete={handleOnboardingComplete} />
      </main>
    );
  }

  return (
    <main className="min-h-screen selection:bg-[#FFE5EC] relative">
      {/* Active Screen Content */}
      {activeTab === 'home' && (
        <HomeGallery
          profile={profile}
          categories={categories}
          snaps={snaps}
          dailyPrompts={dailyPrompts}
          onSavePromptEntry={handleSavePromptEntry}
          onDeletePromptEntry={handleDeletePromptEntry}
          onSaveSnap={handleSaveSnap}
          onSelectSnap={(snap) => setSelectedSnap(snap)}
          onToggleFavorite={handleToggleFavorite}
          onOpenAddSnap={() => setIsAddSnapOpen(true)}
          onOpenAddCategory={() => setIsAddCategoryOpen(true)}
          onNavigateToUs={() => setActiveTab('us')}
          onNavigateToMemories={() => setActiveTab('memories')}
        />
      )}

      {activeTab === 'albums' && (
        <AlbumsView
          categories={categories}
          snaps={snaps}
          onSelectSnap={(snap) => setSelectedSnap(snap)}
          onToggleFavorite={handleToggleFavorite}
          onOpenAddCategory={() => setIsAddCategoryOpen(true)}
        />
      )}

      {activeTab === 'memories' && (
        <MemoriesTimelineView
          profile={profile}
          categories={categories}
          snaps={snaps}
          milestoneNotes={milestoneNotes}
          onSaveMilestoneNote={handleSaveMilestoneNote}
          onSelectSnap={(snap) => setSelectedSnap(snap)}
          onToggleFavorite={handleToggleFavorite}
        />
      )}

      {activeTab === 'us' && (
        <UsProfileView
          profile={profile}
          categories={categories}
          snaps={snaps}
          dailyPrompts={dailyPrompts}
          currentUser={currentUser}
          onSignOut={handleSignOut}
          onUpdateProfile={(updated) => updateProfile(updated)}
          onResetData={handleResetData}
          onClearAllSnaps={handleClearAllSnaps}
        />
      )}

      {/* Persistent Bottom Navigation */}
      <BottomNav
        activeTab={activeTab}
        onTabChange={(tab) => setActiveTab(tab)}
        onOpenAddSnap={() => setIsAddSnapOpen(true)}
      />

      {/* Modals */}
      <AddSnapModal
        categories={categories}
        isOpen={isAddSnapOpen}
        onClose={() => setIsAddSnapOpen(false)}
        onSaveSnap={handleSaveSnap}
        onOpenAddCategory={() => {
          setIsAddCategoryOpen(true);
        }}
      />

      <CategoryModal
        isOpen={isAddCategoryOpen}
        onClose={() => setIsAddCategoryOpen(false)}
        onSaveCategory={handleSaveCategory}
      />

      <SnapDetailModal
        snap={selectedSnap}
        category={categories.find((c) => c.id === selectedSnap?.categoryId)}
        onClose={() => setSelectedSnap(null)}
        onToggleFavorite={(id) => handleToggleFavorite(id)}
        onDeleteSnap={handleDeleteSnap}
      />
    </main>
  );
}
