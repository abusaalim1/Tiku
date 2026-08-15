import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Category, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { X, Heart, MapPin, Calendar, Trash2, Sparkles, BookOpen } from 'lucide-react';

interface SnapDetailModalProps {
  snap: Snap | null;
  category?: Category;
  onClose: () => void;
  onToggleFavorite: (snapId: string) => void;
  onDeleteSnap: (snapId: string) => void;
}

export const SnapDetailModal: React.FC<SnapDetailModalProps> = ({
  snap,
  category,
  onClose,
  onToggleFavorite,
  onDeleteSnap,
}) => {
  return (
    <AnimatePresence>
      {snap && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto">
          {/* Backdrop Blur */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/55 backdrop-blur-md cursor-pointer"
          />

          {/* Modal Content */}
          <motion.div
            initial={{ opacity: 0, scale: 0.88, y: 24, rotate: snap.rotationDeg || -2 }}
            animate={{ opacity: 1, scale: 1, y: 0, rotate: 0 }}
            exit={{ opacity: 0, scale: 0.88, y: 20, rotate: 2 }}
            transition={{ type: 'spring', damping: 22, stiffness: 280 }}
            className="relative w-full max-w-lg glass-modal rounded-[32px] p-5 sm:p-7 border border-[#FDE2E7] max-h-[92vh] overflow-y-auto no-scrollbar z-10 glossy-top"
          >
            {/* Washi Tape Graphic */}
            <div className="absolute -top-3 left-1/2 -translate-x-1/2 w-28 h-6 bg-[#FDE2E7] border border-[#FDE2E7] rotate-[-1deg] shadow-2xs rounded-xs flex items-center justify-center pointer-events-none z-20">
              <span className="text-[10px] font-bold text-[#F47D90] tracking-wider uppercase flex items-center gap-1">
                <Sparkles className="w-2.5 h-2.5" /> Polaroid Snap
              </span>
            </div>

            {/* Close Button */}
            <button
              onClick={onClose}
              className="absolute top-4 right-4 w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#7A5C68] hover:text-[#3E2431] flex items-center justify-center transition-all shadow-xs z-30 cursor-pointer hover:scale-105 active:scale-95 border border-[#FDE2E7]"
            >
              <X className="w-5 h-5" />
            </button>

            {/* Polaroid Style Display */}
            <div className="mt-2 bg-white p-4 sm:p-5 rounded-2xl border-2 border-[#FDE2E7] shadow-tuchi relative">
              {/* Photo Frame */}
              <div className="relative rounded-xl overflow-hidden aspect-4/3 bg-[#FFF7F9] shadow-inner border border-[#FDE2E7]">
                <img
                  src={snap.imageUrl}
                  alt={snap.caption || 'Memory Detail'}
                  className="w-full h-full object-cover"
                />

                {/* Favorite Toggle Button */}
                <div className="absolute top-3 right-3">
                  <motion.button
                    whileTap={{ scale: 0.8 }}
                    onClick={() => onToggleFavorite(snap.id)}
                    className={`w-10 h-10 rounded-full flex items-center justify-center transition-all shadow-md cursor-pointer ${
                      snap.favorite
                        ? 'bg-[#F47D90] text-white'
                        : 'bg-white/80 text-[#7A5C68] hover:bg-white'
                    }`}
                  >
                    <Heart className={`w-5 h-5 ${snap.favorite ? 'fill-current' : ''}`} />
                  </motion.button>
                </div>
              </div>

              {/* Details Section */}
              <div className="pt-4 space-y-3">
                {/* Category Tag */}
                {category && (
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#FFF0F3] text-xs font-bold text-[#3E2431] shadow-2xs border border-[#FDE2E7]">
                    <span
                      className="w-2.5 h-2.5 rounded-full"
                      style={{ backgroundColor: category.color || '#F47D90' }}
                    />
                    <CategoryIcon iconName={category.iconName} className="w-3.5 h-3.5 text-[#F47D90]" />
                    <span>{category.name}</span>
                  </div>
                )}

                {/* Caption */}
                {snap.caption ? (
                  <h3 className="text-xl sm:text-2xl font-bold font-heading text-[#3E2431] leading-snug">
                    {snap.caption}
                  </h3>
                ) : (
                  <p className="text-sm italic text-[#7A5C68]">A quiet, beautiful memory together 💖</p>
                )}

                {/* Description / What Happened */}
                {snap.description && (
                  <div className="p-3.5 sm:p-4 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-1">
                    <div className="text-[11px] font-bold text-[#F47D90] uppercase tracking-wider flex items-center gap-1.5">
                      <BookOpen className="w-3.5 h-3.5 text-[#F47D90]" /> What Happened That Day
                    </div>
                    <p className="text-xs sm:text-sm text-[#3E2431] font-medium leading-relaxed whitespace-pre-wrap">
                      {snap.description}
                    </p>
                  </div>
                )}

                {/* Meta details */}
                <div className="flex flex-wrap items-center gap-4 text-xs font-semibold text-[#7A5C68] pt-3 border-t border-[#FDE2E7]">
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-4 h-4 text-[#F47D90]" />
                    <span>{formatDate(snap.snapDate)}</span>
                  </div>

                  {snap.location && (
                    <div className="flex items-center gap-1.5">
                      <MapPin className="w-4 h-4 text-[#F47D90]" />
                      <span>{snap.location}</span>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Action Controls */}
            <div className="mt-5 flex items-center justify-between gap-3 pt-1">
              <button
                onClick={() => {
                  if (confirm('Are you sure you want to delete this memory snap?')) {
                    onDeleteSnap(snap.id);
                    onClose();
                  }
                }}
                className="py-2.5 px-4 rounded-full bg-red-50 hover:bg-red-100 text-red-600 text-xs font-bold flex items-center gap-1.5 transition-colors border border-red-200 cursor-pointer active:scale-95"
              >
                <Trash2 className="w-4 h-4" />
                <span>Delete Snap</span>
              </button>

              <button
                onClick={onClose}
                className="py-2.5 px-6 rounded-full bg-[#3E2431] hover:bg-[#F47D90] text-white text-xs font-extrabold shadow-tuchi cursor-pointer active:scale-95 transition-all"
              >
                Close Memory
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
