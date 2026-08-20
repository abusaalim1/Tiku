import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Category, CoupleProfile, DailyPromptEntry, Snap } from '../types';
import { calculateDaysTogether, formatDate } from '../utils/dateUtils';
import { INITIAL_LOVE_NOTES } from '../data/initialData';
import { DailyPromptCard } from './DailyPromptCard';
import { AnniversaryCountdownWidget } from './AnniversaryCountdownWidget';
import { AndroidInstallModal } from './AndroidInstallModal';
import {
  Heart,
  Camera,
  Image as ImageIcon,
  Star,
  Folder,
  Plus,
  Bell,
  ChevronDown,
  Sparkles,
  Smartphone,
  Images,
} from 'lucide-react';

interface HomeGalleryProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  dailyPrompts: DailyPromptEntry[];
  onSavePromptEntry: (entry: DailyPromptEntry) => void;
  onDeletePromptEntry?: (entryId: string) => void;
  onSaveSnap: (newSnap: {
    categoryId: string;
    imageUrl: string;
    imageUrls?: string[];
    caption?: string;
    description?: string;
    location?: string;
    snapDate: string;
    rotationDeg: number;
  }) => void;
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
  onOpenAddSnap: () => void;
  onOpenAddCategory: () => void;
  onNavigateToUs: () => void;
  onNavigateToMemories?: () => void;
  onNavigateToAlbums?: () => void;
}

export const HomeGallery: React.FC<HomeGalleryProps> = ({
  profile,
  categories,
  snaps,
  dailyPrompts,
  onSavePromptEntry,
  onDeletePromptEntry,
  onSaveSnap,
  onSelectSnap,
  onOpenAddSnap,
  onOpenAddCategory,
  onNavigateToUs,
  onNavigateToMemories,
  onNavigateToAlbums,
}) => {
  const [loveNoteIndex, setLoveNoteIndex] = useState<number>(0);
  const [showLoveNoteModal, setShowLoveNoteModal] = useState<boolean>(false);
  const [customLoveNotes, setCustomLoveNotes] = useState(INITIAL_LOVE_NOTES);
  const [newNoteText, setNewNoteText] = useState('');
  const [showDailyPromptSection, setShowDailyPromptSection] = useState<boolean>(false);
  const [showAndroidInstall, setShowAndroidInstall] = useState<boolean>(false);

  // Time of day greeting
  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }, []);

  // Calculate days together
  const daysTogether = calculateDaysTogether(profile.startDate) || 247;

  // Top 3 Recent memories
  const recentMemories = useMemo(() => {
    return snaps.slice(0, 3);
  }, [snaps]);

  // Featured couple hero photo
  const heroPhotoUrl =
    profile.userPhotoUrl && profile.partnerPhotoUrl
      ? profile.userPhotoUrl
      : snaps[0]?.imageUrl ||
        'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=800&auto=format&fit=crop&q=80';

  const handleAddLoveNote = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNoteText.trim()) return;
    const newNote = {
      id: `note-${Date.now()}`,
      author: profile.userName || 'You',
      content: newNoteText.trim(),
      date: 'Just now',
    };
    setCustomLoveNotes((prev) => [newNote, ...prev]);
    setNewNoteText('');
    setLoveNoteIndex(0);
    setShowLoveNoteModal(false);
  };

  return (
    <div className="pb-32 pt-3 px-3.5 sm:px-6 max-w-4xl mx-auto space-y-6 text-[#3E2431]">
      {/* 1. TOP HEADER BAR */}
      <header className="flex items-center justify-between px-1">
        {/* Brand Logo */}
        <div className="flex items-center gap-2 cursor-pointer" onClick={onNavigateToUs}>
          <span className="text-3xl font-extrabold font-display-heavy text-[#3E2431] tracking-tight flex items-center">
            Tuchi
            <span className="text-[#F47D90] text-2xl ml-1">♥</span>
          </span>
          <span className="hidden sm:inline-block px-2.5 py-0.5 rounded-full bg-[#FFF0F3] text-[#F47D90] text-[10px] font-bold border border-[#FDE2E7]">
            Scrapbook
          </span>
        </div>

        {/* Right Actions: Android Install Button + Notification Bell + Couple Avatar */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Android Install Button */}
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setShowAndroidInstall(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white hover:bg-[#FFF0F3] text-[#F47D90] border border-[#FDE2E7] text-xs font-bold shadow-xs cursor-pointer transition-all"
            title="Install on Android phone"
          >
            <Smartphone className="w-3.5 h-3.5 text-[#F47D90]" />
            <span className="hidden sm:inline">Android App</span>
          </motion.button>

          {/* Notification Bell */}
          <motion.button
            whileHover={{ scale: 1.08 }}
            whileTap={{ scale: 0.92 }}
            onClick={() => setShowDailyPromptSection((prev) => !prev)}
            className="w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-white border border-[#FDE2E7] flex items-center justify-center text-[#7A5C68] shadow-xs relative cursor-pointer"
            title="Notifications & Daily Prompts"
          >
            <Bell className="w-4 h-4 sm:w-5 sm:h-5 text-[#7A5C68]" />
            <span className="absolute top-2 right-2 w-2 h-2 bg-[#F47D90] rounded-full ring-2 ring-white" />
          </motion.button>

          {/* Couple Avatar Dropdown Pill */}
          <motion.div
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
            onClick={onNavigateToUs}
            className="flex items-center gap-1.5 p-1 pr-2.5 bg-white border border-[#FDE2E7] rounded-full shadow-xs cursor-pointer"
          >
            <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-full overflow-hidden border border-[#F47D90] bg-[#FFF0F3]">
              <img
                src={heroPhotoUrl}
                alt="Couple"
                className="w-full h-full object-cover"
              />
            </div>
            <ChevronDown className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#7A5C68]" />
          </motion.div>
        </div>
      </header>

      {/* 2. HERO CARD (Clean White + Soft Pink Minimalist) */}
      <section className="relative rounded-[32px] glass-panel p-6 sm:p-8 overflow-hidden glossy-top border border-[#FDE2E7] shadow-[0_12px_36px_rgba(244,125,144,0.12)] bg-gradient-to-br from-white via-[#FFF9FA] to-[#FFF0F3]">
        {/* Subtle decorative background blobs */}
        <div className="absolute -top-12 -left-12 w-48 h-48 bg-[#FDE2E7]/60 rounded-full blur-2xl pointer-events-none" />
        <div className="absolute -bottom-12 -right-12 w-48 h-48 bg-[#FFF0F3]/80 rounded-full blur-2xl pointer-events-none" />

        <div className="relative z-10 grid grid-cols-1 sm:grid-cols-12 gap-6 items-center">
          {/* Left Column: Greeting & Highlight Typography & Action Pills */}
          <div className="sm:col-span-7 space-y-4 text-left">
            <div>
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white border border-[#FDE2E7] text-[#F47D90] text-xs font-bold mb-2 shadow-2xs">
                <Sparkles className="w-3.5 h-3.5 text-[#F47D90]" />
                <span>{greeting}, {profile.userName || 'Love'} ✨</span>
              </div>

              <h2 className="text-3xl sm:text-4xl font-extrabold font-heading text-[#3E2431] tracking-tight leading-tight">
                Our <span className="font-serif-italic font-bold text-[#F47D90]">Love Story</span>
              </h2>

              <p className="text-sm sm:text-base text-[#7A5C68] font-medium mt-2 leading-relaxed">
                Every second with you is my favorite memory. Captured in polaroids, albums & sweet love notes. 💖
              </p>
            </div>

            {/* Action Buttons: Add Multi-Photo & Our Space */}
            <div className="flex flex-wrap items-center gap-2.5 pt-1">
              <motion.button
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.96 }}
                onClick={onOpenAddSnap}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white text-xs sm:text-sm font-bold shadow-tuchi-glow transition-all cursor-pointer"
              >
                <Plus className="w-4 h-4 stroke-[2.5]" />
                <span>Add Photos Memory</span>
              </motion.button>

              <motion.button
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.96 }}
                onClick={onNavigateToUs}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-full bg-white hover:bg-[#FFF0F3] text-[#7A5C68] border border-[#FDE2E7] text-xs sm:text-sm font-bold shadow-2xs transition-all cursor-pointer"
              >
                <Heart className="w-4 h-4 text-[#F47D90] fill-[#F47D90]/30" />
                <span>Our Space</span>
              </motion.button>
            </div>
          </div>

          {/* Right Column: Featured Couple Card */}
          <div className="sm:col-span-5 flex justify-center sm:justify-end items-center relative">
            <div
              onClick={onNavigateToUs}
              className="polaroid-card w-48 sm:w-56 rotate-2 shadow-tuchi hover:rotate-0 transition-transform cursor-pointer bg-white"
            >
              <div className="aspect-4/3 rounded-xl overflow-hidden bg-[#FFF0F3] border border-[#FDE2E7]">
                <img
                  src={heroPhotoUrl}
                  alt="Couple Together"
                  className="w-full h-full object-cover"
                />
              </div>
              <div className="pt-2 text-center">
                <p className="text-xs font-bold text-[#3E2431] font-serif-italic">
                  {profile.userName || 'You'} & {profile.partnerName || 'Partner'}
                </p>
                <p className="text-[10px] text-[#F47D90] font-semibold">Together Forever ✨</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 3. KEY STATS CARDS (4 Rounded Cards) */}
      <section className="glass-panel rounded-[28px] p-3 sm:p-4 border border-[#FDE2E7] shadow-[0_8px_24px_rgba(244,125,144,0.08)] bg-white">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-3">
          {/* Card 1: Days Together */}
          <div
            onClick={onNavigateToUs}
            className="flex items-center gap-3 p-3 rounded-2xl bg-[#FFF7F9] hover:bg-[#FFF0F3] border border-[#FDE2E7] transition-colors cursor-pointer"
          >
            <div className="w-10 h-10 rounded-2xl bg-white border border-[#FDE2E7] flex items-center justify-center flex-shrink-0 text-[#F47D90] shadow-2xs">
              <Heart className="w-5 h-5 fill-[#F47D90]" />
            </div>
            <div>
              <p className="text-[11px] text-[#7A5C68] font-medium leading-none">Days Together</p>
              <p className="text-xl font-extrabold text-[#3E2431] font-heading mt-0.5 leading-tight">{daysTogether}</p>
              <p className="text-[10px] text-[#F47D90] font-medium leading-none mt-0.5">and counting 💖</p>
            </div>
          </div>

          {/* Card 2: Memories */}
          <div
            onClick={onNavigateToAlbums || onNavigateToMemories}
            className="flex items-center gap-3 p-3 rounded-2xl bg-[#FFF7F9] hover:bg-[#FFF0F3] border border-[#FDE2E7] transition-colors cursor-pointer"
          >
            <div className="w-10 h-10 rounded-2xl bg-white border border-[#FDE2E7] flex items-center justify-center flex-shrink-0 text-[#F47D90] shadow-2xs">
              <ImageIcon className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] text-[#7A5C68] font-medium leading-none">Memories</p>
              <p className="text-xl font-extrabold text-[#3E2431] font-heading mt-0.5 leading-tight">{snaps.length}</p>
              <p className="text-[10px] text-[#F47D90] font-medium leading-none mt-0.5">saved forever</p>
            </div>
          </div>

          {/* Card 3: Special Moments */}
          <div
            onClick={onNavigateToMemories}
            className="flex items-center gap-3 p-3 rounded-2xl bg-[#FFF7F9] hover:bg-[#FFF0F3] border border-[#FDE2E7] transition-colors cursor-pointer"
          >
            <div className="w-10 h-10 rounded-2xl bg-white border border-[#FDE2E7] flex items-center justify-center flex-shrink-0 text-[#F47D90] shadow-2xs">
              <Star className="w-5 h-5 fill-[#F47D90]" />
            </div>
            <div>
              <p className="text-[11px] text-[#7A5C68] font-medium leading-none">Special Snaps</p>
              <p className="text-xl font-extrabold text-[#3E2431] font-heading mt-0.5 leading-tight">
                {snaps.filter((s) => s.favorite).length || 18}
              </p>
              <p className="text-[10px] text-[#F47D90] font-medium leading-none mt-0.5">unforgettable ✨</p>
            </div>
          </div>

          {/* Card 4: Love Score */}
          <div
            onClick={() => setShowLoveNoteModal(true)}
            className="flex items-center gap-3 p-3 rounded-2xl bg-[#FFF7F9] hover:bg-[#FFF0F3] border border-[#FDE2E7] transition-colors cursor-pointer"
          >
            <div className="w-10 h-10 rounded-2xl bg-white border border-[#FDE2E7] flex items-center justify-center flex-shrink-0 text-[#F47D90] shadow-2xs">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] text-[#7A5C68] font-medium leading-none">Love Score</p>
              <p className="text-xl font-extrabold text-[#3E2431] font-heading mt-0.5 leading-tight">100%</p>
              <p className="text-[10px] text-[#F47D90] font-medium leading-none mt-0.5">infinite bond 🌙</p>
            </div>
          </div>
        </div>
      </section>

      {/* 4. ANNIVERSARY COUNTDOWN WIDGET */}
      <AnniversaryCountdownWidget
        profile={profile}
        onNavigateToMemories={onNavigateToMemories}
        onNavigateToUs={onNavigateToUs}
      />

      {/* OPTIONAL EXPANDABLE: DAILY PROMPT & CELEBRATION */}
      <AnimatePresence>
        {showDailyPromptSection && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="space-y-4 overflow-hidden"
          >
            <DailyPromptCard
              profile={profile}
              categories={categories}
              dailyPrompts={dailyPrompts}
              onSavePromptEntry={onSavePromptEntry}
              onDeletePromptEntry={onDeletePromptEntry}
              onSaveSnap={onSaveSnap}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 5. MIDDLE SECTION: RECENT MEMORIES & LOVE NOTE */}
      <section className="grid grid-cols-1 md:grid-cols-12 gap-5">
        {/* Left Column: Recent Memories (7 cols) */}
        <div className="md:col-span-7 glass-panel rounded-[28px] p-5 sm:p-6 border border-[#FDE2E7] shadow-[0_8px_24px_rgba(244,125,144,0.08)] bg-white space-y-4">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-xl bg-[#FFF0F3] flex items-center justify-center text-[#F47D90] border border-[#FDE2E7]">
                <Camera className="w-4 h-4" />
              </div>
              <h3 className="text-base sm:text-lg font-bold font-heading text-[#3E2431]">
                Recent <span className="font-serif-italic font-bold text-[#F47D90]">Memories</span>
              </h3>
            </div>

            <button
              onClick={onNavigateToAlbums || onNavigateToMemories}
              className="text-xs font-bold text-[#F47D90] hover:text-[#EE6A80] bg-[#FFF0F3] hover:bg-[#FDE2E7] px-3 py-1 rounded-full border border-[#FDE2E7] transition-colors cursor-pointer"
            >
              View all
            </button>
          </div>

          {/* 3 Thumbnails Grid */}
          <div className="grid grid-cols-3 gap-3">
            {recentMemories.map((snap) => {
              const snapImgs = snap.imageUrls && snap.imageUrls.length > 0 ? snap.imageUrls : [snap.imageUrl];
              return (
                <motion.div
                  key={snap.id}
                  whileHover={{ y: -3, scale: 1.03 }}
                  whileTap={{ scale: 0.97 }}
                  onClick={() => onSelectSnap(snap)}
                  className="group cursor-pointer space-y-1.5 text-left"
                >
                  <div className="aspect-square rounded-2xl overflow-hidden bg-[#FFF0F3] border border-[#FDE2E7] shadow-2xs relative">
                    <img
                      src={snapImgs[0]}
                      alt={snap.caption || 'Memory'}
                      className="w-full h-full object-cover group-hover:scale-108 transition-transform duration-300"
                    />
                    {snapImgs.length > 1 && (
                      <div className="absolute top-1.5 right-1.5 px-1.5 py-0.5 rounded-md bg-black/70 backdrop-blur-xs text-[9px] font-bold text-white flex items-center gap-0.5">
                        <Images className="w-2.5 h-2.5 text-[#F47D90]" />
                        <span>{snapImgs.length}</span>
                      </div>
                    )}
                  </div>
                  <p className="text-xs font-bold text-[#3E2431] truncate group-hover:text-[#F47D90] transition-colors">
                    {snap.caption?.replace(/[^\w\s]/gi, '') || 'Our Memory'}
                  </p>
                  <p className="text-[10px] text-[#7A5C68] font-medium">
                    {formatDate(snap.snapDate)}
                  </p>
                </motion.div>
              );
            })}

            {/* If fewer than 3 snaps, show Add Memory Slot */}
            {recentMemories.length < 3 && (
              <motion.button
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
                onClick={onOpenAddSnap}
                className="aspect-square rounded-2xl border-2 border-dashed border-[#FDE2E7] bg-[#FFF7F9] hover:bg-[#FFF0F3] flex flex-col items-center justify-center gap-1 text-[#F47D90] transition-colors cursor-pointer"
              >
                <Plus className="w-5 h-5 text-[#F47D90]" />
                <span className="text-[10px] font-bold">Add Snap</span>
              </motion.button>
            )}
          </div>
        </div>

        {/* Right Column: Love Note (5 cols) */}
        <div className="md:col-span-5 glass-panel rounded-[28px] p-5 sm:p-6 border border-[#FDE2E7] shadow-[0_8px_24px_rgba(244,125,144,0.08)] bg-white flex flex-col justify-between space-y-4">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-lg">💌</span>
              <h3 className="text-base sm:text-lg font-bold font-heading text-[#3E2431]">
                Love <span className="font-serif-italic font-bold text-[#F47D90]">Notes</span>
              </h3>
            </div>

            <button
              onClick={() => setShowLoveNoteModal(true)}
              className="text-[11px] font-bold text-[#F47D90] hover:text-[#EE6A80] bg-[#FFF0F3] hover:bg-[#FDE2E7] px-2.5 py-1 rounded-full shadow-2xs border border-[#FDE2E7] transition-colors cursor-pointer"
            >
              + Write
            </button>
          </div>

          {/* Inner Note Card */}
          <div className="bg-[#FFF7F9] rounded-2xl p-4 sm:p-5 shadow-inner border border-[#FDE2E7] min-h-[120px] flex items-center justify-center text-center">
            <AnimatePresence mode="wait">
              <motion.p
                key={loveNoteIndex}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.2 }}
                className="text-xs sm:text-sm font-medium text-[#7A5C68] leading-relaxed italic font-serif-italic"
              >
                "{customLoveNotes[loveNoteIndex]?.content || 'I love you more with every passing day. 💖'}"
              </motion.p>
            </AnimatePresence>
          </div>

          {/* Carousel Pagination Dots */}
          <div className="flex items-center justify-center gap-1.5 pt-1">
            {customLoveNotes.slice(0, 4).map((_, idx) => (
              <button
                key={idx}
                onClick={() => setLoveNoteIndex(idx)}
                className={`h-2 rounded-full transition-all cursor-pointer ${
                  loveNoteIndex === idx
                    ? 'w-5 bg-[#F47D90]'
                    : 'w-2 bg-[#FDE2E7] hover:bg-[#F47D90]/50'
                }`}
                title={`Note ${idx + 1}`}
              />
            ))}
          </div>
        </div>
      </section>

      {/* 6. BOTTOM SECTION: MEMORY ALBUMS */}
      <section className="glass-panel rounded-[28px] p-5 sm:p-6 border border-[#FDE2E7] shadow-[0_8px_24px_rgba(244,125,144,0.08)] bg-white space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-[#FFF0F3] flex items-center justify-center text-[#F47D90] border border-[#FDE2E7]">
              <Folder className="w-4 h-4" />
            </div>
            <h3 className="text-base sm:text-lg font-bold font-heading text-[#3E2431]">
              Memory <span className="font-serif-italic font-bold text-[#F47D90]">Albums</span>
            </h3>
          </div>

          <button
            onClick={onNavigateToAlbums}
            className="text-xs font-bold text-[#F47D90] hover:text-[#EE6A80] bg-[#FFF0F3] hover:bg-[#FDE2E7] px-3 py-1 rounded-full border border-[#FDE2E7] transition-colors cursor-pointer"
          >
            See all
          </button>
        </div>

        {/* Albums Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-3">
          {categories.slice(0, 4).map((cat) => {
            const catSnaps = snaps.filter((s) => s.categoryId === cat.id);
            const coverImage =
              catSnaps[0]?.imageUrl ||
              'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=400&auto=format&fit=crop&q=80';

            return (
              <motion.div
                key={cat.id}
                whileHover={{ y: -3, scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={onNavigateToAlbums}
                className="group cursor-pointer space-y-1.5 text-left"
              >
                <div className="aspect-square rounded-2xl overflow-hidden bg-[#FFF0F3] border border-[#FDE2E7] shadow-2xs relative">
                  <img
                    src={coverImage}
                    alt={cat.name}
                    className="w-full h-full object-cover group-hover:scale-108 transition-transform duration-300"
                  />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-[#3E2431] group-hover:text-[#F47D90] transition-colors truncate">
                    {cat.name}
                  </h4>
                  <p className="text-[10px] text-[#7A5C68] font-medium">
                    {catSnaps.length || 12} memories
                  </p>
                </div>
              </motion.div>
            );
          })}

          {/* "+ New Album" Card */}
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={onOpenAddCategory}
            className="aspect-square rounded-2xl border-2 border-dashed border-[#FDE2E7] bg-[#FFF7F9] hover:bg-[#FFF0F3] flex flex-col items-center justify-center gap-1 text-[#F47D90] transition-colors cursor-pointer p-2"
          >
            <div className="w-8 h-8 rounded-full bg-[#F47D90] text-white flex items-center justify-center shadow-xs">
              <Plus className="w-4 h-4 stroke-[2.5]" />
            </div>
            <span className="text-[11px] font-bold text-[#7A5C68] mt-1">New Album</span>
          </motion.button>
        </div>
      </section>

      {/* LOVE NOTE MODAL */}
      <AnimatePresence>
        {showLoveNoteModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="glass-modal rounded-[28px] p-6 max-w-sm w-full shadow-2xl border border-[#FDE2E7] space-y-4 text-[#3E2431] bg-white"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xl">💌</span>
                  <h3 className="text-lg font-bold font-heading text-[#3E2431]">Leave a Love Note</h3>
                </div>
                <button
                  onClick={() => setShowLoveNoteModal(false)}
                  className="text-xs font-bold text-[#7A5C68] hover:text-[#3E2431] cursor-pointer"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={handleAddLoveNote} className="space-y-3">
                <textarea
                  value={newNoteText}
                  onChange={(e) => setNewNoteText(e.target.value)}
                  placeholder="Write a sweet message for your partner..."
                  rows={4}
                  className="w-full p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] focus:border-[#F47D90] text-xs sm:text-sm font-medium outline-none resize-none text-[#3E2431] placeholder:text-[#7A5C68]/60"
                  autoFocus
                />

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowLoveNoteModal(false)}
                    className="flex-1 py-2.5 rounded-full border border-[#FDE2E7] text-xs font-bold text-[#7A5C68] hover:bg-[#FFF0F3] cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="flex-1 py-2.5 rounded-full bg-[#F47D90] text-white text-xs font-bold shadow-tuchi-glow hover:bg-[#EE6A80] cursor-pointer"
                  >
                    Send Note 💖
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Android Install Modal */}
      <AndroidInstallModal
        isOpen={showAndroidInstall}
        onClose={() => setShowAndroidInstall(false)}
      />
    </div>
  );
};
