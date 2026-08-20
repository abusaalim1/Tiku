import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Category, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { Category3DIcon, Sparkles3D, LoveLetter3D } from './Romance3DIcons';
import { X, Heart, MapPin, Calendar, Trash2, ChevronLeft, ChevronRight, Images } from 'lucide-react';

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
  const [photoIndex, setPhotoIndex] = useState<number>(0);

  if (!snap) return null;

  const images = snap.imageUrls && snap.imageUrls.length > 0 ? snap.imageUrls : [snap.imageUrl];

  const handleNext = () => {
    setPhotoIndex((prev) => (prev + 1) % images.length);
  };

  const handlePrev = () => {
    setPhotoIndex((prev) => (prev - 1 + images.length) % images.length);
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-black/40 backdrop-blur-md cursor-pointer"
        />

        {/* Modal Content */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9, y: 24, rotate: snap.rotationDeg || -2 }}
          animate={{ opacity: 1, scale: 1, y: 0, rotate: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: 20, rotate: 2 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          className="relative w-full max-w-lg glass-modal rounded-[36px] p-5 sm:p-7 border border-[#FDE2E7] max-h-[92vh] overflow-y-auto no-scrollbar z-10 shadow-[0_24px_60px_rgba(244,125,144,0.2)] bg-white text-[#2D1F27]"
        >
          {/* Washi Tape Graphic */}
          <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 bg-[#FDE2E7] border border-white/80 rotate-[-1deg] shadow-2xs rounded-sm flex items-center justify-center pointer-events-none z-20">
            <span className="text-[10px] font-extrabold text-[#6B5360] tracking-wider uppercase flex items-center gap-1.5">
              <Sparkles3D size={12} /> Polaroid Memory
            </span>
          </div>

          {/* Close Button */}
          <button
            onClick={onClose}
            className="absolute top-4 right-4 w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] flex items-center justify-center transition-all shadow-xs z-30 cursor-pointer hover:scale-105 active:scale-95 border border-[#FDE2E7]"
          >
            <X className="w-4 h-4" />
          </button>

          {/* Polaroid Style Display */}
          <div className="mt-2 bg-white p-4 sm:p-5 rounded-[28px] border border-[#FDE2E7] shadow-tuchi relative text-[#2D1F27]">
            {/* Photo Carousel Container */}
            <div className="relative rounded-[22px] overflow-hidden aspect-4/3 bg-[#FFF0F3] shadow-inner border border-[#FDE2E7]">
              <img
                src={images[photoIndex]}
                alt={snap.caption || 'Memory Detail'}
                className="w-full h-full object-cover"
              />

              {/* Multi-Photo Carousel Controls */}
              {images.length > 1 && (
                <>
                  <div className="absolute top-3 left-3 px-2.5 py-1 rounded-full bg-black/60 backdrop-blur-md text-xs font-bold text-white flex items-center gap-1.5 border border-white/20">
                    <Images className="w-3.5 h-3.5 text-[#F47D90]" />
                    <span>{photoIndex + 1} / {images.length}</span>
                  </div>

                  <button
                    onClick={handlePrev}
                    className="absolute left-2.5 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-white/90 hover:bg-[#F47D90] text-[#2D1F27] hover:text-white flex items-center justify-center transition-all cursor-pointer shadow-md"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </button>

                  <button
                    onClick={handleNext}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-white/90 hover:bg-[#F47D90] text-[#2D1F27] hover:text-white flex items-center justify-center transition-all cursor-pointer shadow-md"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </button>

                  {/* Indicator Dots */}
                  <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/50 backdrop-blur-md">
                    {images.map((_, idx) => (
                      <button
                        key={idx}
                        onClick={() => setPhotoIndex(idx)}
                        className={`h-2 rounded-full transition-all cursor-pointer ${
                          photoIndex === idx ? 'w-5 bg-[#F47D90]' : 'w-2 bg-white/50'
                        }`}
                      />
                    ))}
                  </div>
                </>
              )}

              {/* Favorite Toggle Button */}
              <div className="absolute top-3 right-3">
                <motion.button
                  whileTap={{ scale: 0.8 }}
                  onClick={() => onToggleFavorite(snap.id)}
                  className={`w-10 h-10 rounded-full flex items-center justify-center transition-all shadow-md cursor-pointer ${
                    snap.favorite
                      ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                      : 'bg-white/90 text-[#6B5360] hover:bg-white hover:text-[#F47D90]'
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
                <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#FFF0F3] text-xs font-bold text-[#2D1F27] shadow-xs border border-[#FDE2E7]">
                  <Category3DIcon categoryName={category.name} size={16} />
                  <span>{category.name}</span>
                </div>
              )}

              {/* Caption */}
              {snap.caption ? (
                <h3 className="text-xl sm:text-2xl font-bold font-heading text-[#2D1F27] leading-snug">
                  {snap.caption}
                </h3>
              ) : (
                <p className="text-sm italic font-serif-italic text-[#6B5360]">A quiet, beautiful memory together 💖</p>
              )}

              {/* Description / Story */}
              {snap.description && (
                <div className="p-3.5 sm:p-4 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-1">
                  <div className="text-[11px] font-bold text-[#F47D90] uppercase tracking-wider flex items-center gap-1.5">
                    <LoveLetter3D size={16} /> What Happened That Day
                  </div>
                  <p className="text-xs sm:text-sm text-[#2D1F27] font-medium leading-relaxed whitespace-pre-wrap">
                    {snap.description}
                  </p>
                </div>
              )}

              {/* Meta details */}
              <div className="flex flex-wrap items-center gap-4 text-xs font-semibold text-[#6B5360] pt-3 border-t border-[#FDE2E7]">
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
              className="py-2.5 px-4 rounded-2xl bg-red-50 hover:bg-red-100 text-red-500 text-xs font-bold flex items-center gap-1.5 transition-colors border border-red-200 cursor-pointer active:scale-95"
            >
              <Trash2 className="w-4 h-4" />
              <span>Delete Snap</span>
            </button>

            <button
              onClick={onClose}
              className="py-2.5 px-6 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-extrabold shadow-tuchi-glow cursor-pointer active:scale-95 transition-all"
            >
              Close Memory
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
