import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Category, CoupleProfile, Snap } from '../types';
import { calculateDaysTogether, calculateUpcomingAnniversaries, formatDate } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { PolaroidCard } from './PolaroidCard';
import { AnniversaryCelebrationCard } from './AnniversaryCelebrationCard';
import {
  Heart,
  Plus,
  Search,
  Camera,
  Calendar,
  Sparkles,
  Users,
} from 'lucide-react';

interface HomeGalleryProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
  onOpenAddSnap: () => void;
  onOpenAddCategory: () => void;
  onNavigateToUs: () => void;
  onNavigateToMemories?: () => void;
}

export const HomeGallery: React.FC<HomeGalleryProps> = ({
  profile,
  categories,
  snaps,
  onSelectSnap,
  onToggleFavorite,
  onOpenAddSnap,
  onOpenAddCategory,
  onNavigateToUs,
  onNavigateToMemories,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [onlyFavorites, setOnlyFavorites] = useState<boolean>(false);

  // Calculate days together
  const daysTogether = calculateDaysTogether(profile.startDate);

  // Calculate upcoming monthly and annual anniversaries
  const anniversaries = useMemo(
    () => calculateUpcomingAnniversaries(profile.startDate),
    [profile.startDate]
  );

  // Category map for fast lookup
  const categoryMap = new Map<string, Category>(categories.map((c) => [c.id, c]));

  // Filter logic
  const filteredSnaps = snaps.filter((snap) => {
    // Favorite filter
    if (onlyFavorites && !snap.favorite) return false;

    // Category filter
    if (selectedCategory !== 'all' && snap.categoryId !== selectedCategory) return false;

    // Search query
    if (searchQuery.trim() !== '') {
      const q = searchQuery.toLowerCase();
      const matchCaption = snap.caption?.toLowerCase().includes(q);
      const matchDesc = snap.description?.toLowerCase().includes(q);
      const matchLoc = snap.location?.toLowerCase().includes(q);
      const cat = categoryMap.get(snap.categoryId);
      const matchCat = cat?.name.toLowerCase().includes(q);
      return matchCaption || matchDesc || matchLoc || matchCat;
    }

    return true;
  });

  return (
    <div className="pb-28 pt-4 px-4 sm:px-6 max-w-5xl mx-auto space-y-6">
      {/* HEADER BAR */}
      <header className="flex items-center justify-between gap-4 pt-1">
        <div className="flex items-center gap-3">
          {/* Couple Avatars Badge */}
          <motion.div
            whileHover={{ scale: 1.05, rotate: -2 }}
            whileTap={{ scale: 0.95 }}
            onClick={onNavigateToUs}
            className="flex -space-x-3 cursor-pointer group"
          >
            <div className="w-10 h-10 rounded-full border-2 border-white bg-[#FFDEE5] overflow-hidden shadow-xs flex items-center justify-center">
              {profile.userPhotoUrl ? (
                <img src={profile.userPhotoUrl} alt="You" className="w-full h-full object-cover" />
              ) : (
                <span className="text-xs font-bold text-[#4A2C3A]">
                  {profile.userName ? profile.userName.charAt(0).toUpperCase() : 'Y'}
                </span>
              )}
            </div>
            <div className="w-10 h-10 rounded-full border-2 border-white bg-[#E8AFC0] overflow-hidden shadow-xs flex items-center justify-center">
              {profile.partnerPhotoUrl ? (
                <img src={profile.partnerPhotoUrl} alt="Partner" className="w-full h-full object-cover" />
              ) : (
                <span className="text-xs font-bold text-[#4A2C3A]">
                  {profile.partnerName ? profile.partnerName.charAt(0).toUpperCase() : 'P'}
                </span>
              )}
            </div>
          </motion.div>
          <div>
            <h1 className="text-2xl font-bold font-heading text-[#4A2C3A] tracking-tight flex items-center gap-1.5">
              Tiku <Heart className="w-4 h-4 fill-[#FF4B72] text-[#FF4B72]" />
            </h1>
            <p className="text-[11px] text-[#7A5566] font-medium -mt-0.5">Couple Snap Memories</p>
          </div>
        </div>

        {/* Days Together Counter Button */}
        <motion.button
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          onClick={onNavigateToUs}
          className="px-4 py-2 rounded-full bg-gradient-to-r from-[#FFDEE5] to-[#FFF0F3] hover:from-[#FFE5EC] border border-[#FFCCD5] text-[#4A2C3A] text-xs font-bold shadow-2xs flex items-center gap-1.5 cursor-pointer transition-all"
        >
          <Calendar className="w-3.5 h-3.5 text-[#8C3F5D]" />
          <span>Day {daysTogether}</span>
        </motion.button>
      </header>

      {/* LOVE STORY COUNTER BANNER */}
      <section className="glass-panel rounded-3xl p-6 sm:p-8 text-center border border-white/90 shadow-tuchi relative overflow-hidden glossy-top">
        <div className="absolute top-3.5 left-4 text-xs opacity-70">
          <Heart className="w-4 h-4 fill-[#FF4B72] text-[#FF4B72]" />
        </div>
        <div className="absolute bottom-3.5 right-4 text-xs opacity-70">
          <Heart className="w-4 h-4 fill-[#E8AFC0] text-[#E8AFC0]" />
        </div>

        <div className="relative z-10 space-y-2.5">
          <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-white/90 text-[#3E2431] text-xs font-extrabold shadow-xs border border-white">
            <Heart className="w-3.5 h-3.5 fill-[#FF4B72] text-[#FF4B72]" />
            <span>Love Story Counter</span>
          </div>

          <h2 className="text-3xl sm:text-5xl font-black font-heading text-[#3E2431] tracking-tight">
            {daysTogether} Days of Us 💖
          </h2>

          <p className="text-xs sm:text-sm text-[#6E495B] font-medium max-w-md mx-auto">
            {profile.userName && profile.partnerName
              ? `${profile.userName} & ${profile.partnerName}`
              : 'Our Beautiful Journey'}{' '}
            • {snaps.length} {snaps.length === 1 ? 'memory snap' : 'memory snaps'} collected so far
          </p>
        </div>
      </section>

      {/* UPCOMING ANNIVERSARY NOTIFICATION / CELEBRATION BADGE */}
      <AnniversaryCelebrationCard
        profile={profile}
        anniversaries={anniversaries}
        onNavigateToMemories={onNavigateToMemories}
      />

      {/* FILTER & SEARCH BAR */}
      <section className="space-y-3.5">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Search Box */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-[#8C3F5D] absolute left-4 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search memories, stories, locations..."
              className="w-full pl-11 pr-10 py-3 rounded-full bg-white/80 backdrop-blur-xl border border-white/90 text-[#3E2431] text-xs font-semibold focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFDEE5] shadow-xs transition-all placeholder:text-[#6E495B]/60"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-bold text-[#FF4B72] hover:text-[#3E2431] transition-colors"
              >
                Clear
              </button>
            )}
          </div>

          {/* Favorites Filter Toggle */}
          <motion.button
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setOnlyFavorites((prev) => !prev)}
            className={`px-4 py-3 rounded-full text-xs font-bold flex items-center justify-center gap-1.5 transition-all shadow-xs cursor-pointer border ${
              onlyFavorites
                ? 'bg-[#FF4B72] text-white border-[#FF4B72] shadow-tuchi-glow'
                : 'bg-white/80 backdrop-blur-xl text-[#6E495B] border-white/90 hover:bg-white'
            }`}
          >
            <Heart className={`w-3.5 h-3.5 ${onlyFavorites ? 'fill-current text-white' : 'text-[#FF4B72]'}`} />
            <span>Favorites ({snaps.filter((s) => s.favorite).length})</span>
          </motion.button>
        </div>

        {/* Category Horizontal Filter Chips */}
        <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
          {/* All Chip */}
          <motion.button
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
            onClick={() => setSelectedCategory('all')}
            className={`px-4 py-2.5 rounded-full text-xs font-extrabold flex-shrink-0 transition-all cursor-pointer border ${
              selectedCategory === 'all'
                ? 'bg-[#3E2431] text-white border-[#3E2431] shadow-md ring-2 ring-[#FFDEE5]'
                : 'bg-white/70 backdrop-blur-md border-white/80 text-[#6E495B] hover:bg-white'
            }`}
          >
            All Snaps ({snaps.length})
          </motion.button>

          {/* Category Chips with Icons */}
          {categories.map((cat) => {
            const count = snaps.filter((s) => s.categoryId === cat.id).length;
            const isSelected = selectedCategory === cat.id;

            return (
              <motion.button
                key={cat.id}
                whileHover={{ scale: 1.04 }}
                whileTap={{ scale: 0.96 }}
                onClick={() => setSelectedCategory(cat.id)}
                className={`px-4 py-2.5 rounded-full text-xs font-extrabold flex items-center gap-2 flex-shrink-0 transition-all cursor-pointer border ${
                  isSelected
                    ? 'bg-[#3E2431] text-white border-[#3E2431] shadow-md ring-2 ring-[#FFDEE5]'
                    : 'bg-white/70 backdrop-blur-md border-white/80 text-[#6E495B] hover:bg-white'
                }`}
              >
                <span
                  className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                  style={{ backgroundColor: cat.color || '#E8AFC0' }}
                />
                <CategoryIcon
                  iconName={cat.iconName}
                  categoryName={cat.name}
                  className={`w-3.5 h-3.5 ${isSelected ? 'text-white' : 'text-[#8C3F5D]'}`}
                />
                <span>
                  {cat.name} ({count})
                </span>
              </motion.button>
            );
          })}

          {/* Add Category Chip */}
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={onOpenAddCategory}
            className="px-3 py-2 rounded-full text-xs font-extrabold text-[#8C3F5D] bg-white border border-dashed border-[#E8AFC0] hover:bg-[#FFE5EC]/50 flex items-center gap-1 flex-shrink-0 transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" /> Tag
          </motion.button>
        </div>
      </section>

      {/* GALLERY POLAROID GRID WITH CUTE ANIMATIONS */}
      <section>
        <AnimatePresence mode="wait">
          {filteredSnaps.length > 0 ? (
            <motion.div
              key={`${selectedCategory}-${searchQuery}-${onlyFavorites}`}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.25 }}
              className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 sm:gap-8 pt-2"
            >
              {filteredSnaps.map((snap) => (
                <PolaroidCard
                  key={snap.id}
                  snap={snap}
                  category={categoryMap.get(snap.categoryId)}
                  onSelect={onSelectSnap}
                  onToggleFavorite={onToggleFavorite}
                />
              ))}
            </motion.div>
          ) : (
            /* CUTE EMPTY STATE */
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="glass-panel rounded-3xl p-10 text-center space-y-4 border-2 border-[#FFDEE5] my-6"
            >
              <div className="w-16 h-16 mx-auto rounded-full bg-[#FFE5EC] flex items-center justify-center text-[#4A2C3A]">
                <Camera className="w-8 h-8 text-[#8C3F5D]" />
              </div>
              <div className="space-y-1">
                <h3 className="text-xl font-bold font-heading text-[#4A2C3A]">
                  No memories found
                </h3>
                <p className="text-xs text-[#7A5566] max-w-xs mx-auto">
                  {searchQuery || selectedCategory !== 'all' || onlyFavorites
                    ? 'Try adjusting your filters or search terms to find your snaps.'
                    : 'No memories saved yet. Click the button below to upload your first couple photo!'}
                </p>
              </div>
              <motion.button
                whileHover={{ scale: 1.04 }}
                whileTap={{ scale: 0.96 }}
                onClick={onOpenAddSnap}
                className="py-3 px-6 rounded-full bg-[#4A2C3A] hover:bg-[#38202b] text-white text-xs font-bold shadow-tuchi inline-flex items-center gap-2 transition-transform cursor-pointer"
              >
                <Plus className="w-4 h-4 text-[#FFDEE5]" />
                <span>Add Your First Memory</span>
              </motion.button>
            </motion.div>
          )}
        </AnimatePresence>
      </section>

      {/* FLOATING "+" BUTTON BOTTOM RIGHT */}
      <motion.button
        whileHover={{ scale: 1.1, rotate: 90 }}
        whileTap={{ scale: 0.9 }}
        onClick={onOpenAddSnap}
        className="fixed bottom-20 right-6 sm:bottom-24 sm:right-10 w-14 h-14 rounded-full bg-gradient-to-tr from-[#4A2C3A] to-[#8C3F5D] text-white shadow-tuchi-lg flex items-center justify-center z-40 cursor-pointer"
        title="Add new memory snap"
      >
        <Plus className="w-7 h-7 text-[#FFDEE5]" />
      </motion.button>
    </div>
  );
};
