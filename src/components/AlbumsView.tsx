import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Category, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { FolderHeart, Sparkles, ArrowRight, Image as ImageIcon, ArrowLeft } from 'lucide-react';
import { PolaroidCard } from './PolaroidCard';

interface AlbumsViewProps {
  categories: Category[];
  snaps: Snap[];
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
  onOpenAddCategory: () => void;
}

export const AlbumsView: React.FC<AlbumsViewProps> = ({
  categories,
  snaps,
  onSelectSnap,
  onToggleFavorite,
  onOpenAddCategory,
}) => {
  const [selectedAlbumId, setSelectedAlbumId] = useState<string | null>(null);

  // Filter out empty albums (only show albums containing at least 1 snap)
  const nonEmptyCategories = categories.filter((cat) =>
    snaps.some((snap) => snap.categoryId === cat.id)
  );

  // Get active selected album category
  const activeCategory = categories.find((cat) => cat.id === selectedAlbumId);
  const activeSnaps = selectedAlbumId
    ? snaps.filter((snap) => snap.categoryId === selectedAlbumId)
    : [];

  return (
    <div className="pb-28 pt-4 px-4 sm:px-6 max-w-5xl mx-auto space-y-6">
      {/* HEADER */}
      <div className="text-center space-y-1.5">
        <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-white/80 backdrop-blur-md text-[#3E2431] text-xs font-extrabold border border-[#FDE2E7] shadow-xs">
          <FolderHeart className="w-3.5 h-3.5 text-[#F47D90]" /> Couple Collections
        </div>
        <h2 className="text-3xl sm:text-4xl font-extrabold font-heading text-[#3E2431] tracking-tight">
          Photo Albums
        </h2>
        <p className="text-xs sm:text-sm text-[#7A5C68] font-medium">
          Your active memory albums categorized with love
        </p>
      </div>

      {/* ALBUM FILTER DETAIL OR GRID WITH ANIMATED SWITCHING */}
      <AnimatePresence mode="wait">
        {selectedAlbumId && activeCategory ? (
          <motion.div
            key="album-detail"
            initial={{ opacity: 0, scale: 0.96, y: 15 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -15 }}
            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
            className="space-y-6"
          >
            {/* Back to all albums banner */}
            <div className="glass-panel rounded-3xl p-4 sm:p-5 flex items-center justify-between border border-[#FDE2E7] shadow-tuchi glossy-top">
              <div className="flex items-center gap-3.5">
                <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-[#FFF0F3] to-[#FDE2E7] border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                  <CategoryIcon iconName={activeCategory.iconName} categoryName={activeCategory.name} className="w-5 h-5 text-[#F47D90]" />
                </div>
                <div>
                  <h3 className="text-xl font-bold font-heading text-[#3E2431]">
                    {activeCategory.name} Album
                  </h3>
                  <p className="text-xs text-[#7A5C68] font-medium">
                    {activeSnaps.length} {activeSnaps.length === 1 ? 'photo memory' : 'photo memories'}
                  </p>
                </div>
              </div>

              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => setSelectedAlbumId(null)}
                className="py-2.5 px-4 rounded-full bg-white hover:bg-[#FFF0F3] text-[#3E2431] text-xs font-bold border border-[#FDE2E7] shadow-xs cursor-pointer flex items-center gap-1.5 transition-all"
              >
                <ArrowLeft className="w-3.5 h-3.5" /> Back to Albums
              </motion.button>
            </div>

            {/* Snaps Grid */}
            {activeSnaps.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 pt-2">
                {activeSnaps.map((snap) => (
                  <PolaroidCard
                    key={snap.id}
                    snap={snap}
                    category={activeCategory}
                    onSelect={onSelectSnap}
                    onToggleFavorite={onToggleFavorite}
                  />
                ))}
              </div>
            ) : (
              <div className="glass-panel rounded-3xl p-10 text-center space-y-3 border border-[#FDE2E7]">
                <Sparkles className="w-8 h-8 text-[#F47D90] mx-auto" />
                <h4 className="text-lg font-bold font-heading text-[#3E2431]">
                  No snaps in {activeCategory.name} yet
                </h4>
                <p className="text-xs text-[#7A5C68]">Add photos under this category from the home screen!</p>
              </div>
            )}
          </motion.div>
        ) : (
          /* ALBUMS GRID */
          <motion.div
            key="album-grid"
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -15 }}
            transition={{ duration: 0.25 }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5"
          >
            {nonEmptyCategories.map((cat) => {
              const catSnaps = snaps.filter((s) => s.categoryId === cat.id);
              const coverSnap = catSnaps[0]; // latest photo

              return (
                <motion.div
                  key={cat.id}
                  whileHover={{ y: -5, scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  onClick={() => setSelectedAlbumId(cat.id)}
                  className="glass-panel glass-panel-hover rounded-3xl p-4 border border-[#FDE2E7] shadow-tuchi cursor-pointer group flex flex-col justify-between glossy-top"
                >
                  {/* Album Cover Photo Stack */}
                  <div className="relative aspect-4/3 w-full rounded-2xl overflow-hidden bg-[#FFF7F9] border border-[#FDE2E7] mb-3.5 shadow-2xs">
                    {coverSnap ? (
                      <img
                        src={coverSnap.imageUrl}
                        alt={cat.name}
                        className="w-full h-full object-cover group-hover:scale-108 transition-transform duration-700 ease-out"
                      />
                    ) : null}

                    {/* Top Category Badge */}
                    <div className="absolute top-3 left-3 px-3 py-1 rounded-full bg-white/90 backdrop-blur-xl text-xs font-extrabold text-[#3E2431] flex items-center gap-1.5 shadow-xs border border-[#FDE2E7]">
                      <span
                        className="w-2.5 h-2.5 rounded-full ring-2 ring-white/60"
                        style={{ backgroundColor: cat.color || '#F47D90' }}
                      />
                      <CategoryIcon iconName={cat.iconName} categoryName={cat.name} className="w-3.5 h-3.5 text-[#F47D90]" />
                      <span>{cat.name}</span>
                    </div>
                  </div>

                  {/* Album Info */}
                  <div className="flex items-center justify-between px-1">
                    <div>
                      <h3 className="text-lg font-bold font-heading text-[#3E2431] group-hover:text-[#F47D90] transition-colors">
                        {cat.name}
                      </h3>
                      <p className="text-xs text-[#7A5C68] font-medium">
                        {catSnaps.length} {catSnaps.length === 1 ? 'photo' : 'photos'}
                        {coverSnap && ` • ${formatDate(coverSnap.snapDate)}`}
                      </p>
                    </div>

                    <div className="w-8 h-8 rounded-full bg-[#FFF0F3] text-[#F47D90] flex items-center justify-center group-hover:translate-x-1 group-hover:bg-[#F47D90] group-hover:text-white transition-all shadow-xs border border-[#FDE2E7]">
                      <ArrowRight className="w-4 h-4" />
                    </div>
                  </div>
                </motion.div>
              );
            })}

            {/* Empty state when NO albums have photos */}
            {nonEmptyCategories.length === 0 && (
              <div className="col-span-full glass-panel rounded-3xl p-10 text-center space-y-3 border border-[#FDE2E7] my-4">
                <div className="w-14 h-14 rounded-full bg-[#FFF0F3] text-[#F47D90] flex items-center justify-center mx-auto border border-[#FDE2E7] shadow-xs">
                  <ImageIcon className="w-7 h-7" />
                </div>
                <h3 className="text-xl font-bold font-heading text-[#3E2431]">No Photo Albums Yet</h3>
                <p className="text-xs text-[#7A5C68] max-w-sm mx-auto">
                  Upload your first photo memory from the home screen, and its album will automatically appear here!
                </p>
              </div>
            )}

            {/* Add New Album Custom Category Button */}
            <motion.button
              whileHover={{ scale: 1.02, y: -2 }}
              whileTap={{ scale: 0.98 }}
              onClick={onOpenAddCategory}
              className="rounded-3xl p-6 border-2 border-dashed border-[#FDE2E7] hover:border-[#F47D90] bg-white/40 hover:bg-white/80 backdrop-blur-md text-center flex flex-col items-center justify-center gap-2.5 transition-all cursor-pointer min-h-[200px]"
            >
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-[#FDE2E7] text-[#F47D90] flex items-center justify-center border border-[#FDE2E7] shadow-xs">
                <Sparkles className="w-6 h-6 text-[#F47D90]" />
              </div>
              <div>
                <span className="text-sm font-bold font-heading text-[#3E2431]">Create Custom Album Tag</span>
                <p className="text-xs text-[#7A5C68] mt-0.5">Add a new category for your memories</p>
              </div>
            </motion.button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
