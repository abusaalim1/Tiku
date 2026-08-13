import React from 'react';
import { motion } from 'motion/react';
import { Category, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { Heart, MapPin } from 'lucide-react';

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
  return (
    <motion.div
      whileHover={{ y: -6, scale: 1.03, rotate: (snap.rotationDeg || 0) * 0.5 }}
      whileTap={{ scale: 0.97 }}
      onClick={() => onSelect(snap)}
      style={{ transform: `rotate(${snap.rotationDeg || 0}deg)` }}
      className="polaroid-card group cursor-pointer relative flex flex-col justify-between transition-all duration-300 shadow-tuchi hover:shadow-tuchi-lg select-none"
    >
      {/* Top Tape Accent */}
      <div className="absolute -top-3 left-1/2 -translate-x-1/2 w-12 h-4 bg-[#FFE5EC]/80 backdrop-blur-xs border border-white/60 shadow-2xs rotate-[-2deg] rounded-xs pointer-events-none z-10" />

      {/* Photo Container */}
      <div className="relative aspect-4/3 w-full overflow-hidden rounded-xl bg-[#FFF6F0] border border-[#FFCCD5]">
        <img
          src={snap.imageUrl}
          alt={snap.caption || 'Couple Snap Memory'}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy"
        />

        {/* Favorite Heart Button */}
        <motion.button
          whileTap={{ scale: 0.8 }}
          onClick={(e) => onToggleFavorite(snap.id, e)}
          className={`absolute top-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200 cursor-pointer ${
            snap.favorite
              ? 'bg-[#FF4B72] text-white shadow-md'
              : 'bg-white/90 text-[#7A5566] hover:bg-white hover:text-[#4A2C3A] shadow-xs'
          }`}
          title={snap.favorite ? 'Remove from favorites' : 'Add to favorites'}
        >
          <Heart
            className={`w-4 h-4 ${snap.favorite ? 'fill-current' : ''}`}
          />
        </motion.button>

        {/* Category Tag Icon & Label */}
        {category && (
          <div className="absolute bottom-2.5 left-2.5 px-2.5 py-1 rounded-full bg-white/95 backdrop-blur-md text-xs font-bold text-[#4A2C3A] flex items-center gap-1.5 shadow-xs border border-[#FFCCD5]">
            <span
              className="w-2 h-2 rounded-full flex-shrink-0"
              style={{ backgroundColor: category.color || '#E8AFC0' }}
            />
            <CategoryIcon
              iconName={category.iconName}
              categoryName={category.name}
              className="w-3.5 h-3.5 text-[#8C3F5D]"
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
            <p className="text-[#4A2C3A] text-sm font-bold line-clamp-1 leading-snug font-heading">
              {snap.caption}
            </p>
          ) : snap.description ? (
            <p className="text-[#4A2C3A] text-xs font-semibold line-clamp-2 leading-relaxed">
              {snap.description}
            </p>
          ) : (
            <p className="text-[#7A5566]/60 text-xs italic font-heading">Sweet memory</p>
          )}

          {snap.caption && snap.description && (
            <p className="text-[#7A5566] text-[11px] font-medium line-clamp-1 mt-0.5 opacity-90">
              {snap.description}
            </p>
          )}
        </div>

        <div className="flex items-center justify-between text-[11px] text-[#7A5566] font-semibold pt-2 border-t border-[#FFDEE5] mt-1.5">
          <span>{formatDate(snap.snapDate)}</span>
          {snap.location && (
            <span className="flex items-center gap-0.5 truncate max-w-[110px] text-[#8C3F5D]">
              <MapPin className="w-3 h-3 flex-shrink-0" />
              <span className="truncate">{snap.location}</span>
            </span>
          )}
        </div>
      </div>
    </motion.div>
  );
};
