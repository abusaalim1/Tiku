import React, { useState, useEffect } from 'react';
import { Category, CoupleProfile, NavTab, Snap } from './types';
import {
  loadProfile,
  saveProfile,
  loadCategories,
  saveCategories,
  loadSnaps,
  saveSnaps,
  resetToDemoData,
} from './utils/storage';
import {
  subscribeProfile,
  saveProfileToFirestore,
  subscribeCategories,
  saveCategoryToFirestore,
  subscribeSnaps,
  saveSnapToFirestore,
  deleteSnapFromFirestore,
  clearAllSnapsFromFirestore,
} from './lib/firebase';
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
  const [profile, setProfileState] = useState<CoupleProfile>(() => loadProfile());
  const [categories, setCategoriesState] = useState<Category[]>(() => loadCategories());
  const [snaps, setSnapsState] = useState<Snap[]>(() => loadSnaps());
  const [activeTab, setActiveTab] = useState<NavTab>('home');

  // Modals state
  const [isAddSnapOpen, setIsAddSnapOpen] = useState<boolean>(false);
  const [isAddCategoryOpen, setIsAddCategoryOpen] = useState<boolean>(false);
  const [selectedSnap, setSelectedSnap] = useState<Snap | null>(null);

  // Subscribe to real-time Firestore sync
  useEffect(() => {
    const unsubProfile = subscribeProfile((p) => {
      setProfileState(p);
      saveProfile(p);
    }, loadProfile());

    const unsubCategories = subscribeCategories((cats) => {
      setCategoriesState(cats);
      saveCategories(cats);
    }, loadCategories());

    const unsubSnaps = subscribeSnaps((sList) => {
      setSnapsState(sList);
      saveSnaps(sList);
    });

    return () => {
      unsubProfile();
      unsubCategories();
      unsubSnaps();
    };
  }, []);

  // Update profile wrapper
  const updateProfile = (updated: CoupleProfile) => {
    setProfileState(updated);
    saveProfile(updated);
    saveProfileToFirestore(updated).catch(console.error);
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
    saveSnapToFirestore(newSnap).catch(console.error);
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

    if (updatedSnap) {
      saveSnapToFirestore(updatedSnap).catch(console.error);
    }

    // Also update currently open detail modal snap if selected
    if (selectedSnap && selectedSnap.id === snapId) {
      setSelectedSnap((prev) => (prev ? { ...prev, favorite: !prev.favorite } : null));
    }
  };

  const handleDeleteSnap = (snapId: string) => {
    setSnapsState((prev) => prev.filter((s) => s.id !== snapId));
    deleteSnapFromFirestore(snapId).catch(console.error);
  };

  const handleSaveCategory = (newCategoryData: { name: string; iconName: string; color: string }) => {
    const newCat: Category = {
      id: `cat-${Date.now()}`,
      name: newCategoryData.name,
      iconName: newCategoryData.iconName,
      color: newCategoryData.color,
    };

    setCategoriesState((prev) => [...prev, newCat]);
    saveCategoryToFirestore(newCat).catch(console.error);
  };

  const handleResetData = () => {
    resetToDemoData();
    const defaultProf = loadProfile();
    updateProfile(defaultProf);
    clearAllSnapsFromFirestore().catch(console.error);
  };

  const handleClearAllSnaps = () => {
    setSnapsState([]);
    saveSnaps([]);
    clearAllSnapsFromFirestore().catch(console.error);
  };

  // If user hasn't completed onboarding, render Onboarding Screen
  if (!profile.isOnboarded) {
    return (
      <main className="min-h-screen selection:bg-[#FFDEE5]">
        <Onboarding initialProfile={profile} onComplete={handleOnboardingComplete} />
      </main>
    );
  }

  return (
    <main className="min-h-screen selection:bg-[#FFDEE5] relative">
      {/* Active Screen Content */}
      {activeTab === 'home' && (
        <HomeGallery
          profile={profile}
          categories={categories}
          snaps={snaps}
          onSelectSnap={(snap) => setSelectedSnap(snap)}
          onToggleFavorite={handleToggleFavorite}
          onOpenAddSnap={() => setIsAddSnapOpen(true)}
          onOpenAddCategory={() => setIsAddCategoryOpen(true)}
          onNavigateToUs={() => setActiveTab('us')}
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
          onSelectSnap={(snap) => setSelectedSnap(snap)}
          onToggleFavorite={handleToggleFavorite}
        />
      )}

      {activeTab === 'us' && (
        <UsProfileView
          profile={profile}
          categories={categories}
          snaps={snaps}
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
