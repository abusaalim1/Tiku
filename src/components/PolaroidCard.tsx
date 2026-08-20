import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Category, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { Category3DIcon } from './Romance3DIcons';
import { Heart, MapPin, Images, ChevronLeft, ChevronRight } from 'lucide-react';

interface PolaroidCardProps {
  snap: Snap;
  category?: Category;
  onSelect: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
}

export const PolaroidCard: React.FC<PolaroidCardProps> = ({
  snap,
  category,
  onSelect,
  onToggleFavorite,
}) => {
  const images = snap.imageUrls && snap.imageUrls.length > 0 ? snap.imageUrls : [snap.imageUrl];
  const [currentIdx, setCurrentIdx] = useState<number>(0);

  const handleNext = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIdx((prev) => (prev + 1) % images.length);
  };

  const handlePrev = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIdx((prev) => (prev - 1 + images.length) % images.length);
  };

  return (
    <motion.div
      whileHover={{ y: -6, scale: 1.025, rotate: (snap.rotationDeg || 0) * 0.3 }}
      whileTap={{ scale: 0.98 }}
      onClick={() => onSelect(snap)}
      style={{ transform: `rotate(${snap.rotationDeg || 0}deg)` }}
      className="polaroid-card group cursor-pointer relative flex flex-col justify-between transition-all duration-300 shadow-tuchi hover:shadow-tuchi-lg select-none bg-white rounded-[24px] p-3 border border-[#FDE2E7]"
    >
      {/* Top Cute Washi Tape Accent */}
      <div className="absolute -top-3 left-1/2 -translate-x-1/2 w-14 h-4 bg-[#FDE2E7]/90 backdrop-blur-xs border border-white/80 shadow-2xs rotate-[-1.5deg] rounded-sm pointer-events-none z-10 flex items-center justify-center">
        <span className="text-[8px] text-[#F47D90] font-bold opacity-80">♥ ♥</span>
      </div>

      {/* Photo Frame Container */}
      <div className="relative aspect-4/3 w-full overflow-hidden rounded-[18px] bg-[#FFF0F3] border border-[#FDE2E7]">
        <img
          src={images[currentIdx]}
          alt={snap.caption || 'Couple Snap Memory'}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy"
        />

        {/* Multi-Photo Indicator Pill */}
        {images.length > 1 && (
          <div className="absolute top-2.5 left-2.5 px-2.5 py-0.5 rounded-full bg-black/60 backdrop-blur-md text-[10px] font-bold text-white flex items-center gap-1 border border-white/30 shadow-xs z-10">
            <Images className="w-3 h-3 text-[#F47D90]" />
            <span>{currentIdx + 1}/{images.length}</span>
          </div>
        )}

        {/* Multi-Photo Prev / Next Arrows on Hover */}
        {images.length > 1 && (
          <>
            <button
              onClick={handlePrev}
              className="absolute left-1.5 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 backdrop-blur-md text-[#2D1F27] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-[#F47D90] hover:text-white z-10 cursor-pointer shadow-xs"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={handleNext}
              className="absolute right-1.5 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 backdrop-blur-md text-[#2D1F27] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-[#F47D90] hover:text-white z-10 cursor-pointer shadow-xs"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </>
        )}

        {/* Favorite Heart Button */}
        <motion.button
          whileTap={{ scale: 0.8 }}
          onClick={(e) => onToggleFavorite(snap.id, e)}
          className={`absolute top-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200 cursor-pointer z-10 ${
            snap.favorite
              ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
              : 'bg-white/90 text-[#6B5360] hover:bg-white hover:text-[#F47D90] shadow-xs'
          }`}
          title={snap.favorite ? 'Remove from favorites' : 'Add to favorites'}
        >
          <Heart
            className={`w-4 h-4 ${snap.favorite ? 'fill-current' : ''}`}
          />
        </motion.button>

        {/* Category Tag Icon & Label */}
        {category && (
          <div className="absolute bottom-2.5 left-2.5 px-2.5 py-1 rounded-full bg-white/95 backdrop-blur-md text-xs font-bold text-[#2D1F27] flex items-center gap-1.5 shadow-xs border border-[#FDE2E7] z-10">
            <Category3DIcon
              categoryName={category.name}
              size={14}
            />
            <span className="truncate max-w-[100px]">
              {category.name}
            </span>
          </div>
        )}
      </div>

      {/* Polaroid Bottom Caption Area */}
      <div className="pt-3 px-1 pb-1 flex flex-col justify-between flex-1 min-h-[52px]">
        <div>
          {snap.caption ? (
            <p className="text-[#2D1F27] text-sm font-extrabold line-clamp-1 leading-snug font-heading">
              {snap.caption}
            </p>
          ) : snap.description ? (
            <p className="text-[#2D1F27] text-xs font-semibold line-clamp-2 leading-relaxed">
              {snap.description}
            </p>
          ) : (
            <p className="text-[#6B5360] text-xs italic font-serif-italic">Sweet couple memory</p>
          )}

          {snap.caption && snap.description && (
            <p className="text-[#6B5360] text-[11px] font-medium line-clamp-1 mt-0.5 opacity-90 font-serif-italic">
              {snap.description}
            </p>
          )}
        </div>

        <div className="flex items-center justify-between text-[11px] text-[#6B5360] font-semibold pt-2 border-t border-[#FDE2E7] mt-2">
          <span>{formatDate(snap.snapDate)}</span>
          {snap.location && (
            <span className="flex items-center gap-0.5 truncate max-w-[110px] text-[#F47D90]">
              <MapPin className="w-3 h-3 flex-shrink-0" />
              <span className="truncate">{snap.location}</span>
            </span>
          )}
        </div>
      </div>
    </motion.div>
  );
};
