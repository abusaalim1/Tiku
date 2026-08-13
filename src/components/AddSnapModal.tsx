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
  Plus,
  CalendarDays,
  MapPin,
  Heart,
  Camera,
  CheckCircle2,
  RefreshCw,
  ImagePlus,
  Tags,
  MessageSquareHeart,
  FileText,
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
  const [categoryId, setCategoryId] = useState<string>(categories[0]?.id || '');
  const [caption, setCaption] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [snapDate, setSnapDate] = useState<string>(getTodayDateString());
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [isCompressing, setIsCompressing] = useState<boolean>(false);
  const [isDeveloping, setIsDeveloping] = useState<boolean>(false);

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

    // Cute polaroid development animation delay
    setTimeout(async () => {
      try {
        const compressedImage = await compressSnapPhoto(imageUrl);

        // Confetti burst on save!
        confetti({
          particleCount: 65,
          spread: 70,
          origin: { y: 0.6 },
          colors: ['#FFDEE5', '#E8AFC0', '#D9A15E', '#BFE3D0', '#FF4B72'],
          shapes: ['circle', 'square'],
        });

        onSaveSnap({
          categoryId: categoryId || categories[0]?.id || 'cat-random',
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
    }, 900);
  };

  const handleModalClose = () => {
    if (isDeveloping) return;
    setImageUrl('');
    setCaption('');
    setLocation('');
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 overflow-y-auto">
          {/* Backdrop Blur & Fade */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleModalClose}
            className="fixed inset-0 bg-black/45 backdrop-blur-md cursor-pointer"
          />

          {/* Floating Hearts Background Elements */}
          <div className="fixed inset-0 pointer-events-none overflow-hidden z-10">
            <motion.div
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: [0, -15, 0], opacity: [0.3, 0.7, 0.3] }}
              transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
              className="absolute top-12 left-10 text-[#FFDEE5]"
            >
              <Heart className="w-8 h-8 fill-current" />
            </motion.div>
            <motion.div
              initial={{ y: -20, opacity: 0 }}
              animate={{ y: [0, 15, 0], opacity: [0.3, 0.6, 0.3] }}
              transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
              className="absolute bottom-16 right-12 text-[#E8AFC0]"
            >
              <Heart className="w-10 h-10 fill-current" />
            </motion.div>
          </div>

          {/* Modal Container */}
          <motion.div
            initial={{ opacity: 0, scale: 0.88, y: 24, rotate: -1 }}
            animate={{ opacity: 1, scale: 1, y: 0, rotate: 0 }}
            exit={{ opacity: 0, scale: 0.88, y: 20, rotate: 1 }}
            transition={{ type: 'spring', damping: 22, stiffness: 280 }}
            className="relative w-full max-w-lg glass-modal rounded-[32px] p-6 sm:p-8 border border-white/90 max-h-[92vh] overflow-y-auto no-scrollbar z-20 glossy-top"
          >
            {/* Close Button */}
            <button
              onClick={handleModalClose}
              disabled={isDeveloping}
              className="absolute top-5 right-5 w-9 h-9 rounded-full bg-white/90 hover:bg-[#FFDEE5] text-[#7A5566] hover:text-[#4A2C3A] flex items-center justify-center transition-all shadow-xs z-30 cursor-pointer hover:scale-105 active:scale-95"
            >
              <X className="w-5 h-5" />
            </button>

            {/* Clean Header */}
            <div className="text-center mb-6 pt-1">
              <h2 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#4A2C3A] tracking-tight">
                Add a Couple Memory
              </h2>
              <p className="text-xs sm:text-sm text-[#7A5566] font-medium mt-1">
                Turn your special moment into a sweet polaroid snap 📸
              </p>
            </div>

            {/* Developing Polaroid Animation Overlay */}
            <AnimatePresence>
              {isDeveloping && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0 }}
                  className="absolute inset-0 bg-[#FFFDF9]/95 backdrop-blur-md rounded-[32px] z-40 flex flex-col items-center justify-center p-8 text-center"
                >
                  <motion.div
                    animate={{ rotate: [0, -5, 5, -3, 0], scale: [0.95, 1.05, 1] }}
                    transition={{ duration: 1.2, repeat: Infinity, ease: 'easeInOut' }}
                    className="relative w-48 bg-white p-3 pt-3 pb-8 rounded-2xl shadow-tuchi border border-[#FFDEE5] mb-6"
                  >
                    <div className="aspect-square rounded-xl overflow-hidden bg-[#FFE5EC] relative flex items-center justify-center">
                      {imageUrl ? (
                        <img src={imageUrl} alt="Developing" className="w-full h-full object-cover blur-[1px] opacity-80" />
                      ) : (
                        <Camera className="w-10 h-10 text-[#E8AFC0]" />
                      )}
                      <div className="absolute inset-0 bg-gradient-to-t from-[#8C3F5D]/30 to-transparent flex items-center justify-center">
                        <motion.div
                          animate={{ scale: [0.8, 1.2, 0.8] }}
                          transition={{ duration: 0.8, repeat: Infinity }}
                        >
                          <Heart className="w-10 h-10 fill-[#FF4B72] text-[#FF4B72] drop-shadow-md" />
                        </motion.div>
                      </div>
                    </div>
                    <div className="mt-3 text-center">
                      <p className="font-heading text-xs font-bold text-[#4A2C3A]">Developing Snap...</p>
                    </div>
                  </motion.div>

                  <h3 className="text-xl font-bold font-heading text-[#4A2C3A]">Creating Your Memory Polaroid 💖</h3>
                  <p className="text-xs text-[#7A5566] mt-1.5 animate-pulse">Saving to your couple album...</p>
                </motion.div>
              )}
            </AnimatePresence>

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Photo Upload Area */}
              <div>
                <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-2 flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <ImagePlus className="w-4 h-4 text-[#8C3F5D]" /> Memory Photo
                  </span>
                  <span className="text-[11px] text-[#A85B7A] font-bold lowercase">Max 20MB (JPG, PNG, WEBP)</span>
                </label>

                {/* Hidden File Input for both Dropzone and Replace button */}
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
                  /* Aesthetic Scrapbook Polaroid Preview */
                  <motion.div
                    initial={{ scale: 0.94, opacity: 0, rotate: -1 }}
                    animate={{ scale: 1, opacity: 1, rotate: 0 }}
                    transition={{ type: 'spring', stiffness: 220, damping: 20 }}
                    onDragOver={(e) => {
                      e.preventDefault();
                      setIsDragOver(true);
                    }}
                    onDragLeave={() => setIsDragOver(false)}
                    onDrop={handleDrop}
                    className={`relative bg-white/90 p-4 rounded-[28px] border-2 border-dashed transition-all shadow-tuchi group ${
                      isDragOver
                        ? 'border-[#8C3F5D] bg-[#FFE5EC]/50 ring-4 ring-[#FFDEE5]'
                        : 'border-[#E8AFC0] hover:border-[#8C3F5D]'
                    }`}
                  >
                    {/* Cute Border-Radius Preview Thumbnail */}
                    <div className="relative rounded-[22px] overflow-hidden aspect-4/3 bg-[#FFF0F3] border border-[#FFDEE5] shadow-inner group/thumb">
                      <img
                        src={imageUrl}
                        alt="Memory Preview"
                        className="w-full h-full object-cover transition-transform duration-300 group-hover/thumb:scale-105"
                      />

                      {/* Photo Ready Badge */}
                      <div className="absolute top-3 left-3 px-3 py-1 rounded-full bg-white/90 backdrop-blur-md text-[11px] font-extrabold text-[#4A2C3A] shadow-xs border border-[#FFCCD5] flex items-center gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 fill-emerald-100" />
                        <span>Ready to Snap ✨</span>
                      </div>

                      {/* Replace Photo Button */}
                      <label
                        htmlFor="snap-file-input"
                        className="absolute bottom-3 right-3 px-3.5 py-1.5 rounded-full bg-white/95 hover:bg-[#8C3F5D] text-[#8C3F5D] hover:text-white text-xs font-extrabold backdrop-blur-md border border-[#FFCCD5] shadow-md transition-all cursor-pointer flex items-center gap-1.5 hover:scale-105 active:scale-95"
                      >
                        <RefreshCw className="w-3.5 h-3.5" />
                        <span>Replace Photo</span>
                      </label>
                    </div>

                    {/* Drag-and-drop Replacement Prompt */}
                    <div className="mt-2.5 flex items-center justify-between px-1 text-[11px] font-semibold text-[#7A5566]">
                      <span className="truncate flex items-center gap-1">
                        <Heart className="w-3 h-3 text-[#FF4B72] fill-current" /> {caption || 'Sweet Memory Photo'}
                      </span>
                      <span className="text-[10px] text-[#A85B7A] font-bold">
                        Drop image to replace
                      </span>
                    </div>
                  </motion.div>
                ) : (
                  /* Aesthetic Drag & Drop Area */
                  <motion.div
                    whileHover={{ scale: 1.01 }}
                    whileTap={{ scale: 0.99 }}
                    onDragOver={(e) => {
                      e.preventDefault();
                      setIsDragOver(true);
                    }}
                    onDragLeave={() => setIsDragOver(false)}
                    onDrop={handleDrop}
                    className={`relative border-2 border-dashed rounded-[28px] p-7 text-center transition-all cursor-pointer flex flex-col items-center justify-center gap-3 overflow-hidden ${
                      isDragOver
                        ? 'border-[#8C3F5D] bg-[#FFE5EC]/70 scale-[1.01] shadow-tuchi-glow'
                        : 'border-[#E8AFC0] bg-gradient-to-br from-white via-[#FFF8F9] to-[#FFF3F5] hover:bg-white hover:border-[#8C3F5D] shadow-xs'
                    }`}
                  >
                    {/* Floating Heart Bubbles in Dropzone */}
                    <div className="absolute -top-2 -left-2 text-[#FFE5EC] pointer-events-none">
                      <Heart className="w-12 h-12 fill-current" />
                    </div>
                    <div className="absolute -bottom-2 -right-2 text-[#FFE5EC] pointer-events-none">
                      <Heart className="w-10 h-10 fill-current" />
                    </div>

                    {isCompressing ? (
                      <div className="py-4 flex flex-col items-center gap-2">
                        <motion.div
                          animate={{ rotate: 360 }}
                          transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
                          className="w-10 h-10 rounded-full border-3 border-[#FFE5EC] border-t-[#8C3F5D] flex items-center justify-center"
                        />
                        <p className="text-xs font-bold text-[#4A2C3A] mt-2">Optimizing Photo with Love...</p>
                      </div>
                    ) : (
                      <>
                        <motion.div
                          animate={{ y: [0, -4, 0] }}
                          transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
                          className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-[#FFE5EC] to-[#FFF0F3] border border-[#FFCCD5] flex items-center justify-center text-[#8C3F5D] shadow-xs"
                        >
                          <Upload className="w-7 h-7" />
                        </motion.div>

                        <div>
                          <p className="text-sm font-bold text-[#4A2C3A]">
                            Drag & drop your couple photo here
                          </p>
                          <p className="text-xs text-[#7A5566] mt-0.5">
                            Up to <span className="text-[#8C3F5D] font-bold">20MB</span> • <span className="text-[#8C3F5D] font-bold underline">click to browse</span>
                          </p>
                        </div>

                        <label
                          htmlFor="snap-file-input"
                          className="mt-1 px-5 py-2 rounded-full bg-[#FFE5EC] hover:bg-[#8C3F5D] text-[#8C3F5D] hover:text-white text-xs font-extrabold transition-all cursor-pointer shadow-2xs hover:shadow-xs active:scale-95 flex items-center gap-1.5"
                        >
                          <Camera className="w-3.5 h-3.5" /> Choose Memory Photo
                        </label>
                      </>
                    )}
                  </motion.div>
                )}
              </div>

              {/* Category Selector */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-[#7A5566] uppercase tracking-wider flex items-center gap-1.5">
                    <Tags className="w-4 h-4 text-[#8C3F5D]" /> Memory Tag / Category
                  </label>
                  <button
                    type="button"
                    onClick={onOpenAddCategory}
                    className="text-xs font-bold text-[#8C3F5D] hover:text-[#4A2C3A] flex items-center gap-1 cursor-pointer transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" /> Custom Tag
                  </button>
                </div>

                <div className="flex flex-wrap gap-2 max-h-32 overflow-y-auto p-1 no-scrollbar">
                  {categories.map((cat) => {
                    const isSelected = categoryId === cat.id;
                    return (
                      <motion.button
                        key={cat.id}
                        type="button"
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        onClick={() => setCategoryId(cat.id)}
                        className={`px-3.5 py-1.5 rounded-full text-xs font-bold flex items-center gap-2 transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-[#4A2C3A] text-white shadow-xs scale-105 ring-2 ring-[#FFDEE5]'
                            : 'bg-white border border-[#FFDEE5] text-[#7A5566] hover:bg-[#FFF3F5] hover:border-[#E8AFC0]'
                        }`}
                      >
                        <span
                          className="w-2.5 h-2.5 rounded-full"
                          style={{ backgroundColor: cat.color || '#E8AFC0' }}
                        />
                        <CategoryIcon iconName={cat.iconName} categoryName={cat.name} className="w-3.5 h-3.5" />
                        <span>{cat.name}</span>
                      </motion.button>
                    );
                  })}
                </div>
              </div>

              {/* Caption, Date, Location Inputs */}
              <div className="space-y-3 pt-1">
                <div>
                  <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                    <MessageSquareHeart className="w-4 h-4 text-[#FF4B72]" /> Sweet Title / Caption
                  </label>
                  <input
                    type="text"
                    value={caption}
                    onChange={(e) => setCaption(e.target.value)}
                    placeholder="e.g. Late night ice cream run 🍦"
                    className="w-full px-4 py-2.5 rounded-2xl bg-white border-2 border-[#FFDEE5] text-[#4A2C3A] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#8C3F5D] focus:ring-2 focus:ring-[#FFE5EC] transition-all"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <FileText className="w-4 h-4 text-[#8C3F5D]" /> Memory Story / What Happened?
                    </span>
                    <span className="text-[10px] text-[#A85B7A] font-medium lowercase">optional story</span>
                  </label>
                  <textarea
                    rows={3}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Describe what happened at this moment... e.g. Us din hum bina bataye picnic par gaye aur bohot enjoy kiya ✨"
                    className="w-full px-4 py-2.5 rounded-2xl bg-white border-2 border-[#FFDEE5] text-[#4A2C3A] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#8C3F5D] focus:ring-2 focus:ring-[#FFE5EC] transition-all resize-none"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                      <CalendarDays className="w-4 h-4 text-[#8C3F5D]" /> Date
                    </label>
                    <input
                      type="date"
                      value={snapDate}
                      onChange={(e) => setSnapDate(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-2xl bg-white border-2 border-[#FFDEE5] text-[#4A2C3A] text-xs font-semibold focus:outline-none focus:border-[#8C3F5D] transition-all"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                      <MapPin className="w-4 h-4 text-[#8C3F5D]" /> Location
                    </label>
                    <input
                      type="text"
                      value={location}
                      onChange={(e) => setLocation(e.target.value)}
                      placeholder="e.g. Paris, Rooftop..."
                      className="w-full px-3.5 py-2.5 rounded-2xl bg-white border-2 border-[#FFDEE5] text-[#4A2C3A] text-xs font-medium focus:outline-none focus:border-[#8C3F5D] transition-all"
                    />
                  </div>
                </div>
              </div>

              {/* Submit Button */}
              <div className="pt-3">
                <motion.button
                  whileHover={{ scale: imageUrl ? 1.02 : 1 }}
                  whileTap={{ scale: imageUrl ? 0.98 : 1 }}
                  type="submit"
                  disabled={!imageUrl || isDeveloping}
                  className={`w-full py-4 px-6 rounded-full font-extrabold text-sm sm:text-base shadow-tuchi transition-all flex items-center justify-center gap-2 ${
                    imageUrl && !isDeveloping
                      ? 'bg-gradient-to-r from-[#4A2C3A] via-[#5C3247] to-[#8C3F5D] hover:opacity-95 text-white cursor-pointer shadow-tuchi-glow'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed border border-gray-300'
                  }`}
                >
                  <Heart className="w-4 h-4 fill-[#FFDEE5] text-[#FFDEE5]" />
                  <span>{isDeveloping ? 'Developing Memory...' : 'Save Memory Snap 📸'}</span>
                </motion.button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
