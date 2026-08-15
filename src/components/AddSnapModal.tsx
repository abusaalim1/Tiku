import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Category } from '../types';
import { getTodayDateString, getRandomTilt } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { compressSnapPhoto, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_MB } from '../utils/imageUtils';
import {
  X,
  Upload,
  Calendar,
  MapPin,
  Heart,
  Camera,
  Check,
  ChevronDown,
  Sparkles,
  RefreshCw,
  Gift,
  Smile,
  Plane,
  Plus,
} from 'lucide-react';

interface AddSnapModalProps {
  categories: Category[];
  isOpen: boolean;
  onClose: () => void;
  onSaveSnap: (newSnap: {
    categoryId: string;
    imageUrl: string;
    caption?: string;
    description?: string;
    location?: string;
    snapDate: string;
    rotationDeg: number;
  }) => void;
  onOpenAddCategory: () => void;
}

export const AddSnapModal: React.FC<AddSnapModalProps> = ({
  categories,
  isOpen,
  onClose,
  onSaveSnap,
  onOpenAddCategory,
}) => {
  const [imageUrl, setImageUrl] = useState<string>('');
  const [categoryId, setCategoryId] = useState<string>(categories[0]?.id || 'cat-special');
  const [caption, setCaption] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [snapDate, setSnapDate] = useState<string>(getTodayDateString());
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [isCompressing, setIsCompressing] = useState<boolean>(false);
  const [isDeveloping, setIsDeveloping] = useState<boolean>(false);

  // Preset default tags matching the design if category list is minimal
  const displayTags = [
    { id: 'cat-special', name: 'Anniversaries', icon: Gift, color: '#F47D90' },
    { id: 'cat-dates', name: 'Dates', icon: Calendar, color: '#FDE2E7' },
    { id: 'cat-cute', name: 'First Kiss', icon: Heart, color: '#EE6A80' },
    { id: 'cat-random', name: 'Random Cute', icon: Smile, color: '#F8A0AF' },
    { id: 'cat-adventures', name: 'Trips', icon: Plane, color: '#F29CA9' },
  ];

  // Merge custom user categories that aren't already represented
  const mergedTags = [
    ...displayTags,
    ...categories.filter((cat) => !displayTags.some((d) => d.id === cat.id)),
  ];

  const handleFileUpload = async (file: File) => {
    if (file && file.type.startsWith('image/')) {
      if (file.size > MAX_FILE_SIZE_BYTES) {
        alert(`Photo size is over ${MAX_FILE_SIZE_MB}MB! Please select an image smaller than 20MB.`);
        return;
      }
      setIsCompressing(true);
      try {
        const compressed = await compressSnapPhoto(file);
        if (!compressed) {
          alert('Could not process image file. Please try another image.');
          return;
        }
        setImageUrl(compressed);
      } catch (err) {
        console.error('Snap photo compression failed:', err);
        alert('Failed to process image. Please try again.');
      } finally {
        setIsCompressing(false);
      }
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!imageUrl || isDeveloping) return;

    setIsDeveloping(true);

    setTimeout(async () => {
      try {
        const compressedImage = await compressSnapPhoto(imageUrl);

        // Confetti burst on save!
        confetti({
          particleCount: 75,
          spread: 80,
          origin: { y: 0.6 },
          colors: ['#F47D90', '#FDE2E7', '#FFF0F3', '#EE6A80', '#FFFFFF'],
          shapes: ['circle', 'square'],
        });

        onSaveSnap({
          categoryId: categoryId || categories[0]?.id || 'cat-special',
          imageUrl: compressedImage,
          caption: caption.trim() || undefined,
          description: description.trim() || undefined,
          location: location.trim() || undefined,
          snapDate: snapDate || getTodayDateString(),
          rotationDeg: getRandomTilt(),
        });

        // Reset fields
        setImageUrl('');
        setCaption('');
        setDescription('');
        setLocation('');
        setIsDeveloping(false);
        onClose();
      } catch (err) {
        console.error('Error submitting snap:', err);
        setIsDeveloping(false);
      }
    }, 800);
  };

  const handleModalClose = () => {
    if (isDeveloping) return;
    setImageUrl('');
    setCaption('');
    setDescription('');
    setLocation('');
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
          {/* Backdrop Blur & Dim */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleModalClose}
            className="fixed inset-0 bg-[#3E2431]/40 backdrop-blur-md cursor-pointer"
          />

          {/* Modal Card */}
          <motion.div
            initial={{ opacity: 0, scale: 0.92, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.92, y: 16 }}
            transition={{ type: 'spring', damping: 24, stiffness: 300 }}
            className="relative w-full max-w-xl bg-white rounded-[36px] p-6 sm:p-8 border border-[#FDE2E7] shadow-[0_20px_50px_rgba(62,36,49,0.18)] max-h-[92vh] overflow-y-auto no-scrollbar z-20"
          >
            {/* Header: Icon + Title + Close Button */}
            <div className="flex items-start justify-between mb-6">
              <div className="flex items-center gap-3.5">
                {/* Pink Rounded Camera Box */}
                <div className="w-12 h-12 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90] shadow-2xs flex-shrink-0">
                  <div className="relative">
                    <Camera className="w-6 h-6 stroke-[1.8]" />
                    <Heart className="w-2.5 h-2.5 fill-[#F47D90] text-[#F47D90] absolute -top-1 -right-1" />
                  </div>
                </div>

                <div>
                  <h2 className="text-xl sm:text-2xl font-bold font-heading text-[#3E2431] tracking-tight">
                    Add a{' '}
                    <span className="text-[#F47D90] font-heading font-extrabold">Couple Memory</span>
                  </h2>
                  <p className="text-xs sm:text-sm text-[#7A5C68] font-medium mt-0.5">
                    Turn your special moment into a sweet memory ✨📸
                  </p>
                </div>
              </div>

              {/* Circular Close Button */}
              <button
                type="button"
                onClick={handleModalClose}
                disabled={isDeveloping}
                className="w-9 h-9 rounded-full bg-[#FFF7F9] hover:bg-[#FFF0F3] text-[#7A5C68] hover:text-[#3E2431] flex items-center justify-center transition-all cursor-pointer shadow-2xs border border-[#FDE2E7]"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* 01 MEMORY PHOTO */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2">
                    <span className="text-[#F47D90]">01</span>
                    <span className="text-[#3E2431]">MEMORY PHOTO</span>
                  </label>
                  <span className="text-[11px] text-[#7A5C68] font-medium">Max 20MB (jpg, png, webp)</span>
                </div>

                {/* Hidden File Input */}
                <input
                  type="file"
                  accept="image/*"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleFileUpload(file);
                  }}
                  className="hidden"
                  id="snap-file-input"
                />

                {imageUrl ? (
                  /* Photo Uploaded Preview State */
                  <div className="relative rounded-[28px] overflow-hidden bg-[#FAF5F0] border-2 border-dashed border-[#FDE2E7] p-3 text-center">
                    <div className="aspect-4/3 sm:aspect-16/9 rounded-[22px] overflow-hidden bg-white relative shadow-xs">
                      <img src={imageUrl} alt="Uploaded Memory" className="w-full h-full object-cover" />
                      <label
                        htmlFor="snap-file-input"
                        className="absolute bottom-3 right-3 px-4 py-2 rounded-full bg-white hover:bg-[#F47D90] text-[#F47D90] hover:text-white text-xs font-bold shadow-md border border-[#FDE2E7] transition-all cursor-pointer flex items-center gap-1.5"
                      >
                        <RefreshCw className="w-3.5 h-3.5" />
                        <span>Change Photo</span>
                      </label>
                    </div>
                  </div>
                ) : (
                  /* Drag & Drop Dashed Area Matching Screenshot */
                  <div
                    onDragOver={(e) => {
                      e.preventDefault();
                      setIsDragOver(true);
                    }}
                    onDragLeave={() => setIsDragOver(false)}
                    onDrop={handleDrop}
                    className={`relative rounded-[28px] border-2 border-dashed transition-all p-7 sm:p-9 text-center flex flex-col items-center justify-center gap-2.5 overflow-hidden ${
                      isDragOver
                        ? 'border-[#F47D90] bg-[#FFF0F3]'
                        : 'border-[#FDE2E7] bg-[#FFF7F9] hover:bg-[#FFF0F3]'
                    }`}
                  >
                    {/* Decorative Botanical Leaf (Bottom Left) */}
                    <div className="absolute bottom-3 left-4 pointer-events-none opacity-40 select-none text-[#FDE2E7]">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 3.5 2 7a7 7 0 0 1-10 11Z" />
                        <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12" />
                      </svg>
                    </div>

                    {/* Decorative Intertwined Hearts (Bottom Right) */}
                    <div className="absolute bottom-3 right-4 pointer-events-none opacity-40 select-none text-[#FDE2E7]">
                      <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                        <path d="M12 5 9.04 7.96a2.17 2.17 0 0 0 0 3.08v0c.82.82 2.13.85 3 .07l.96-.83" />
                      </svg>
                    </div>

                    {/* Cloud Upload Circle with Sparkles */}
                    <div className="relative mb-1">
                      <div className="w-14 h-14 rounded-full bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90]">
                        <Upload className="w-6 h-6 stroke-[2]" />
                      </div>
                      <span className="absolute -top-1 -right-3 text-[#F8A0AF] text-xs select-none">✦</span>
                      <span className="absolute -top-1 -left-3 text-[#F8A0AF] text-xs select-none">✦</span>
                      <span className="absolute -bottom-1 right-5 text-[#F8A0AF] text-[10px] select-none">✦</span>
                    </div>

                    {/* Main Title & Click to Browse */}
                    <div>
                      <p className="text-sm sm:text-base font-extrabold text-[#3E2431]">
                        Drag & drop your couple photo here
                      </p>
                      <p className="text-xs text-[#7A5C68] font-medium mt-1">
                        Up to 20MB • or{' '}
                        <label
                          htmlFor="snap-file-input"
                          className="text-[#F47D90] underline font-bold cursor-pointer hover:text-[#EE6A80]"
                        >
                          click to browse
                        </label>
                      </p>
                    </div>

                    {/* Gradient Pill Button: Choose Memory Photo */}
                    <label
                      htmlFor="snap-file-input"
                      className="mt-2 inline-flex items-center gap-2 px-6 py-2.5 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white text-xs sm:text-sm font-bold shadow-[0_4px_14px_rgba(244,125,144,0.3)] transition-all cursor-pointer hover:scale-105 active:scale-95"
                    >
                      <Camera className="w-4 h-4" />
                      <span>Choose Memory Photo</span>
                    </label>
                  </div>
                )}
              </div>

              {/* 02 MEMORY TAG / CATEGORY */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2">
                    <span className="text-[#F47D90]">02</span>
                    <span className="text-[#3E2431]">MEMORY TAG / CATEGORY</span>
                  </label>
                  <button
                    type="button"
                    onClick={onOpenAddCategory}
                    className="text-xs font-bold text-[#F47D90] hover:text-[#3E2431] flex items-center gap-1 cursor-pointer transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Custom Tag</span>
                  </button>
                </div>

                {/* Category Pill Buttons */}
                <div className="flex flex-wrap gap-2.5">
                  {mergedTags.map((tag) => {
                    const isSelected = categoryId === tag.id;
                    const IconComponent = (tag as any).icon || Sparkles;

                    return (
                      <button
                        key={tag.id}
                        type="button"
                        onClick={() => setCategoryId(tag.id)}
                        className={`inline-flex items-center gap-2 px-4 py-2 rounded-full text-xs font-bold transition-all cursor-pointer border ${
                          isSelected
                            ? 'bg-[#F47D90] border-[#F47D90] text-white shadow-sm'
                            : 'bg-white border-[#FDE2E7] text-[#3E2431] hover:bg-[#FFF0F3] hover:border-[#F47D90]'
                        }`}
                      >
                        {tag.icon ? (
                          <IconComponent className={`w-4 h-4 ${isSelected ? 'text-white' : 'text-[#F47D90]'}`} />
                        ) : (
                          <CategoryIcon iconName={(tag as any).iconName} categoryName={tag.name} className="w-4 h-4" />
                        )}
                        <span>{tag.name}</span>
                        {isSelected && (
                          <div className="w-4 h-4 rounded-full bg-white/20 flex items-center justify-center ml-0.5">
                            <Check className="w-2.5 h-2.5 stroke-[3]" />
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 03 SWEET TITLE / CAPTION */}
              <div>
                <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2">
                  <span className="text-[#F47D90]">03</span>
                  <span className="text-[#3E2431]">SWEET TITLE / CAPTION</span>
                </label>
                <div className="relative">
                  <input
                    type="text"
                    value={caption}
                    onChange={(e) => setCaption(e.target.value)}
                    placeholder="e.g. Late night ice cream run 🍦"
                    className="w-full px-4 py-3 rounded-2xl bg-white border border-[#FDE2E7] text-[#3E2431] text-xs sm:text-sm font-medium placeholder:text-[#7A5C68]/60 focus:outline-none focus:border-[#F47D90] focus:ring-2 focus:ring-[#FDE2E7] shadow-2xs pr-10"
                  />
                  <Heart className="w-4 h-4 text-[#FDE2E7] absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                </div>
              </div>

              {/* 04 MEMORY STORY / WHAT HAPPENED? */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2">
                    <span className="text-[#F47D90]">04</span>
                    <span className="text-[#3E2431]">MEMORY STORY / WHAT HAPPENED?</span>
                  </label>
                  <span className="text-[11px] text-[#7A5C68] font-medium">Optional story</span>
                </div>
                <div className="relative">
                  <textarea
                    rows={3}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Describe what happened at this moment...&#10;e.g. Us din hum bina bataye picnic par gaye aur bohot enjoy kiya ✨"
                    className="w-full px-4 py-3 rounded-2xl bg-white border border-[#FDE2E7] text-[#3E2431] text-xs sm:text-sm font-medium placeholder:text-[#7A5C68]/60 focus:outline-none focus:border-[#F47D90] focus:ring-2 focus:ring-[#FDE2E7] shadow-2xs resize-none pr-10"
                  />
                  <Heart className="w-4 h-4 text-[#FDE2E7] absolute right-3.5 bottom-3.5 pointer-events-none" />
                </div>
              </div>

              {/* 05 DATE & 06 LOCATION (2-Column Grid) */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                {/* 05 DATE */}
                <div>
                  <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2">
                    <span className="text-[#F47D90]">05</span>
                    <span className="text-[#3E2431]">DATE</span>
                  </label>
                  <div className="relative flex items-center bg-white border border-[#FDE2E7] rounded-2xl px-3.5 py-2.5 shadow-2xs focus-within:border-[#F47D90] focus-within:ring-2 focus-within:ring-[#FDE2E7]">
                    <Calendar className="w-4 h-4 text-[#7A5C68] mr-2 flex-shrink-0" />
                    <input
                      type="date"
                      value={snapDate}
                      onChange={(e) => setSnapDate(e.target.value)}
                      className="w-full bg-transparent text-[#3E2431] text-xs font-semibold focus:outline-none cursor-pointer"
                    />
                    <ChevronDown className="w-4 h-4 text-[#7A5C68] pointer-events-none ml-1 flex-shrink-0" />
                  </div>
                </div>

                {/* 06 LOCATION */}
                <div>
                  <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2">
                    <span className="text-[#F47D90]">06</span>
                    <span className="text-[#3E2431]">LOCATION</span>
                  </label>
                  <div className="relative flex items-center bg-white border border-[#FDE2E7] rounded-2xl px-3.5 py-2.5 shadow-2xs focus-within:border-[#F47D90] focus-within:ring-2 focus-within:ring-[#FDE2E7]">
                    <MapPin className="w-4 h-4 text-[#7A5C68] mr-2 flex-shrink-0" />
                    <input
                      type="text"
                      value={location}
                      onChange={(e) => setLocation(e.target.value)}
                      placeholder="Add location"
                      className="w-full bg-transparent text-[#3E2431] text-xs font-medium placeholder:text-[#7A5C68]/60 focus:outline-none"
                    />
                    <ChevronDown className="w-4 h-4 text-[#7A5C68] pointer-events-none ml-1 flex-shrink-0" />
                  </div>
                </div>
              </div>

              {/* SAVE MEMORY SUBMIT BUTTON */}
              <div className="pt-2">
                <motion.button
                  whileHover={{ scale: imageUrl ? 1.015 : 1 }}
                  whileTap={{ scale: imageUrl ? 0.985 : 1 }}
                  type="submit"
                  disabled={!imageUrl || isDeveloping}
                  className={`w-full py-3.5 sm:py-4 px-6 rounded-full font-bold text-sm sm:text-base transition-all flex items-center justify-center gap-2 ${
                    imageUrl && !isDeveloping
                      ? 'bg-[#F47D90] hover:bg-[#EE6A80] text-white cursor-pointer shadow-[0_8px_24px_rgba(244,125,144,0.35)]'
                      : 'bg-[#FDE2E7] text-[#7A5C68]/60 cursor-not-allowed'
                  }`}
                >
                  <Sparkles className="w-4 h-4 fill-white/80 text-white" />
                  <span>{isDeveloping ? 'Saving Memory...' : 'Save Memory'}</span>
                </motion.button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
