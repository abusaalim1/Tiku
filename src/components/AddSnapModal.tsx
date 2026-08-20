import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Category, SnapDraft } from '../types';
import { getTodayDateString, getRandomTilt } from '../utils/dateUtils';
import { Category3DIcon } from './Romance3DIcons';
import { compressSnapPhoto, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_MB } from '../utils/imageUtils';
import { getSavedDrafts, saveDraft, deleteDraft } from '../utils/draftsStorage';
import {
  X,
  Upload,
  Calendar,
  MapPin,
  Heart,
  Check,
  ChevronDown,
  Sparkles,
  Plus,
  Trash2,
  Bookmark,
  FileText,
  Clock,
  Images,
} from 'lucide-react';
import { PolaroidCamera3D, Sparkles3D, LoveLetter3D } from './Romance3DIcons';

interface AddSnapModalProps {
  categories: Category[];
  isOpen: boolean;
  onClose: () => void;
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
  onOpenAddCategory: () => void;
}

export const AddSnapModal: React.FC<AddSnapModalProps> = ({
  categories,
  isOpen,
  onClose,
  onSaveSnap,
  onOpenAddCategory,
}) => {
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [activeCoverIndex, setActiveCoverIndex] = useState<number>(0);
  const [categoryId, setCategoryId] = useState<string>(categories[0]?.id || 'cat-special');
  const [caption, setCaption] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [snapDate, setSnapDate] = useState<string>(getTodayDateString());
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [isCompressing, setIsCompressing] = useState<boolean>(false);
  const [isDeveloping, setIsDeveloping] = useState<boolean>(false);
  const [activeDraftId, setActiveDraftId] = useState<string | null>(null);
  const [savedDraftsList, setSavedDraftsList] = useState<SnapDraft[]>([]);
  const [showDraftsDrawer, setShowDraftsDrawer] = useState<boolean>(false);
  const [draftSaveToast, setDraftSaveToast] = useState<boolean>(false);

  useEffect(() => {
    if (isOpen) {
      setSavedDraftsList(getSavedDrafts());
    }
  }, [isOpen]);

  // Preset default tags matching the clean design
  const displayTags = [
    { id: 'cat-special', name: 'Anniversaries' },
    { id: 'cat-dates', name: 'Dates' },
    { id: 'cat-cute', name: 'First Kiss' },
    { id: 'cat-random', name: 'Random Cute' },
    { id: 'cat-adventures', name: 'Trips' },
  ];

  // Merge custom user categories that aren't already represented
  const mergedTags = [
    ...displayTags,
    ...categories.filter((cat) => !displayTags.some((d) => d.id === cat.id)),
  ];

  // Handle Multi-file upload (supports 1, 2, 3+ photos at once)
  const handleMultipleFiles = async (files: FileList | File[]) => {
    const fileArray = Array.from(files).filter((f) => f.type.startsWith('image/'));
    if (fileArray.length === 0) return;

    for (const file of fileArray) {
      if (file.size > MAX_FILE_SIZE_BYTES) {
        alert(`"${file.name}" is over ${MAX_FILE_SIZE_MB}MB! Please select an image smaller than 20MB.`);
        return;
      }
    }

    setIsCompressing(true);
    try {
      const compressedList: string[] = [];
      for (const file of fileArray) {
        const compressed = await compressSnapPhoto(file);
        if (compressed) {
          compressedList.push(compressed);
        }
      }
      if (compressedList.length > 0) {
        setImageUrls((prev) => [...prev, ...compressedList]);
      }
    } catch (err) {
      console.error('Multi-photo compression failed:', err);
      alert('Failed to process one or more images. Please try again.');
    } finally {
      setIsCompressing(false);
    }
  };

  const handleRemoveImage = (indexToRemove: number) => {
    setImageUrls((prev) => {
      const next = prev.filter((_, idx) => idx !== indexToRemove);
      if (activeCoverIndex >= next.length) {
        setActiveCoverIndex(Math.max(0, next.length - 1));
      }
      return next;
    });
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleMultipleFiles(e.dataTransfer.files);
    }
  };

  // Save to Drafts
  const handleSaveDraft = () => {
    if (imageUrls.length === 0 && !caption.trim() && !description.trim()) {
      alert('Please add a photo, caption, or note before saving a draft.');
      return;
    }

    const saved = saveDraft({
      id: activeDraftId || undefined,
      categoryId: categoryId || 'cat-special',
      imageUrls,
      caption,
      description,
      location,
      snapDate,
    });

    setActiveDraftId(saved.id);
    setSavedDraftsList(getSavedDrafts());
    setDraftSaveToast(true);
    setTimeout(() => setDraftSaveToast(false), 2500);
  };

  // Restore Draft
  const handleRestoreDraft = (draft: SnapDraft) => {
    setActiveDraftId(draft.id);
    setImageUrls(draft.imageUrls || []);
    setActiveCoverIndex(0);
    setCategoryId(draft.categoryId || 'cat-special');
    setCaption(draft.caption || '');
    setDescription(draft.description || '');
    setLocation(draft.location || '');
    setSnapDate(draft.snapDate || getTodayDateString());
    setShowDraftsDrawer(false);
  };

  // Delete Draft
  const handleDeleteDraft = (draftId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    deleteDraft(draftId);
    setSavedDraftsList(getSavedDrafts());
    if (activeDraftId === draftId) {
      setActiveDraftId(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (imageUrls.length === 0 || isDeveloping) return;

    setIsDeveloping(true);

    setTimeout(async () => {
      try {
        confetti({
          particleCount: 90,
          spread: 80,
          origin: { y: 0.6 },
          colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#FFFFFF', '#FFE082', '#FF9FB0'],
        });

        const coverPhoto = imageUrls[activeCoverIndex] || imageUrls[0];

        onSaveSnap({
          categoryId: categoryId || categories[0]?.id || 'cat-special',
          imageUrl: coverPhoto,
          imageUrls: imageUrls,
          caption: caption.trim() || undefined,
          description: description.trim() || undefined,
          location: location.trim() || undefined,
          snapDate: snapDate || getTodayDateString(),
          rotationDeg: getRandomTilt(),
        });

        if (activeDraftId) {
          deleteDraft(activeDraftId);
        }

        setImageUrls([]);
        setActiveCoverIndex(0);
        setCaption('');
        setDescription('');
        setLocation('');
        setActiveDraftId(null);
        setIsDeveloping(false);
        onClose();
      } catch (err) {
        console.error('Error submitting snap:', err);
        setIsDeveloping(false);
      }
    }, 500);
  };

  const handleModalClose = () => {
    if (isDeveloping) return;
    setImageUrls([]);
    setActiveCoverIndex(0);
    setCaption('');
    setDescription('');
    setLocation('');
    setActiveDraftId(null);
    setShowDraftsDrawer(false);
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleModalClose}
            className="fixed inset-0 bg-black/40 backdrop-blur-md cursor-pointer"
          />

          {/* Modal Container */}
          <motion.div
            initial={{ opacity: 0, scale: 0.94, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.94, y: 16 }}
            transition={{ type: 'spring', damping: 25, stiffness: 320 }}
            className="relative w-full max-w-2xl glass-modal rounded-[36px] p-5 sm:p-8 border border-[#FDE2E7] shadow-[0_24px_60px_rgba(244,125,144,0.18)] max-h-[92vh] overflow-y-auto no-scrollbar z-20 text-[#2D1F27] bg-white"
          >
            {/* Top Bar: Icon + Title + Drafts Counter & Close */}
            <div className="flex items-start justify-between mb-6">
              <div className="flex items-center gap-3.5">
                <div className="w-13 h-13 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs flex-shrink-0">
                  <PolaroidCamera3D size={36} />
                </div>

                <div>
                  <h2 className="text-xl sm:text-2xl font-bold font-heading text-[#2D1F27] tracking-tight">
                    Add <span className="font-serif-italic font-bold text-[#F47D90]">Couple Memory</span>
                  </h2>
                  <p className="text-xs sm:text-sm text-[#6B5360] font-medium mt-0.5">
                    Upload multiple photos, save drafts & capture moments
                  </p>
                </div>
              </div>

              {/* Right Action Icons: Drafts & Close */}
              <div className="flex items-center gap-2">
                {savedDraftsList.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setShowDraftsDrawer((prev) => !prev)}
                    className="px-3 py-1.5 rounded-full bg-[#FFF0F3] hover:bg-[#FDE2E7] text-[#F47D90] text-xs font-bold border border-[#FDE2E7] flex items-center gap-1.5 transition-all cursor-pointer shadow-xs"
                    title="View Saved Drafts"
                  >
                    <Bookmark className="w-3.5 h-3.5 text-[#F47D90]" />
                    <span>Drafts ({savedDraftsList.length})</span>
                  </button>
                )}

                <button
                  type="button"
                  onClick={handleModalClose}
                  disabled={isDeveloping}
                  className="w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] flex items-center justify-center transition-all cursor-pointer shadow-xs border border-[#FDE2E7]"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* DRAFTS DRAWER / LIST */}
            <AnimatePresence>
              {showDraftsDrawer && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="mb-5 overflow-hidden"
                >
                  <div className="p-4 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-3 shadow-inner">
                    <div className="flex items-center justify-between">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-[#F47D90] flex items-center gap-1.5">
                        <FileText className="w-3.5 h-3.5 text-[#F47D90]" />
                        <span>Saved Drafts ({savedDraftsList.length})</span>
                      </h4>
                      <button
                        type="button"
                        onClick={() => setShowDraftsDrawer(false)}
                        className="text-[11px] text-[#6B5360] hover:text-[#2D1F27] cursor-pointer"
                      >
                        Close
                      </button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 max-h-48 overflow-y-auto no-scrollbar">
                      {savedDraftsList.map((draft) => (
                        <div
                          key={draft.id}
                          onClick={() => handleRestoreDraft(draft)}
                          className={`p-2.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between gap-2.5 ${
                            activeDraftId === draft.id
                              ? 'bg-[#FFF0F3] border-[#F47D90]'
                              : 'bg-white border-[#FDE2E7] hover:border-[#F47D90]'
                          }`}
                        >
                          <div className="flex items-center gap-2.5 overflow-hidden">
                            {draft.imageUrls && draft.imageUrls.length > 0 ? (
                              <img
                                src={draft.imageUrls[0]}
                                alt="Draft"
                                className="w-10 h-10 rounded-xl object-cover border border-[#FDE2E7] flex-shrink-0"
                              />
                            ) : (
                              <div className="w-10 h-10 rounded-xl bg-[#FFF0F3] flex items-center justify-center text-[#F47D90] flex-shrink-0">
                                <Images className="w-4 h-4" />
                              </div>
                            )}
                            <div className="overflow-hidden">
                              <p className="text-xs font-bold text-[#2D1F27] truncate">
                                {draft.caption || 'Untitled Draft'}
                              </p>
                              <p className="text-[10px] text-[#6B5360] flex items-center gap-1 mt-0.5">
                                <Clock className="w-3 h-3" />
                                {draft.imageUrls?.length || 0} photos • {draft.snapDate}
                              </p>
                            </div>
                          </div>

                          <button
                            type="button"
                            onClick={(e) => handleDeleteDraft(draft.id, e)}
                            className="p-1.5 rounded-lg hover:bg-red-50 text-[#6B5360] hover:text-red-500 cursor-pointer"
                            title="Delete Draft"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* 01 MULTI-PHOTO UPLOAD */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2 text-[#2D1F27] font-heading">
                    <span className="text-[#F47D90]">01</span>
                    <span>MEMORY PHOTOS (UPLOAD MULTIPLE AT ONCE)</span>
                  </label>
                  <span className="text-[11px] text-[#6B5360] font-medium">
                    {imageUrls.length > 0 ? `${imageUrls.length} photo${imageUrls.length > 1 ? 's' : ''} added` : 'Multi-photo supported'}
                  </span>
                </div>

                <input
                  type="file"
                  multiple
                  accept="image/*"
                  onChange={(e) => {
                    if (e.target.files && e.target.files.length > 0) {
                      handleMultipleFiles(e.target.files);
                    }
                  }}
                  className="hidden"
                  id="snap-multi-file-input"
                />

                {imageUrls.length > 0 ? (
                  <div className="p-3.5 rounded-[28px] bg-[#FFF7F9] border-2 border-dashed border-[#FDE2E7] space-y-3">
                    {/* Featured Photo */}
                    <div className="relative aspect-16/10 rounded-[22px] overflow-hidden bg-white border border-[#FDE2E7] shadow-inner">
                      <img
                        src={imageUrls[activeCoverIndex] || imageUrls[0]}
                        alt="Featured Memory"
                        className="w-full h-full object-cover"
                      />
                      <div className="absolute top-3 left-3 px-3 py-1 rounded-full bg-white/95 backdrop-blur-md border border-[#FDE2E7] text-[11px] font-bold text-[#F47D90] flex items-center gap-1.5 shadow-xs">
                        <Sparkles3D size={14} />
                        <span>Cover Photo ({activeCoverIndex + 1} of {imageUrls.length})</span>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleRemoveImage(activeCoverIndex)}
                        className="absolute top-3 right-3 w-8 h-8 rounded-full bg-white/95 backdrop-blur-md border border-red-200 text-red-500 hover:bg-red-50 flex items-center justify-center transition-all cursor-pointer shadow-xs"
                        title="Remove photo"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>

                    {/* Thumbnail Strip */}
                    <div className="flex items-center gap-2.5 overflow-x-auto no-scrollbar py-1">
                      {imageUrls.map((img, idx) => (
                        <div
                          key={idx}
                          onClick={() => setActiveCoverIndex(idx)}
                          className={`relative w-16 h-16 rounded-2xl overflow-hidden border-2 flex-shrink-0 cursor-pointer transition-all ${
                            activeCoverIndex === idx
                              ? 'border-[#F47D90] shadow-tuchi-glow scale-105 ring-2 ring-[#F47D90]/30'
                              : 'border-[#FDE2E7] opacity-75 hover:opacity-100'
                          }`}
                        >
                          <img src={img} alt={`Thumb ${idx + 1}`} className="w-full h-full object-cover" />
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRemoveImage(idx);
                            }}
                            className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-[10px]"
                            title="Delete"
                          >
                            ×
                          </button>
                        </div>
                      ))}

                      {/* Add More Photos */}
                      <label
                        htmlFor="snap-multi-file-input"
                        className="w-16 h-16 rounded-2xl border-2 border-dashed border-[#F47D90] bg-[#FFF0F3] hover:bg-[#FDE2E7] text-[#F47D90] flex flex-col items-center justify-center gap-0.5 flex-shrink-0 cursor-pointer transition-all"
                      >
                        <Plus className="w-5 h-5 text-[#F47D90]" />
                        <span className="text-[9px] font-bold">+ Add</span>
                      </label>
                    </div>
                  </div>
                ) : (
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
                        : 'border-[#FDE2E7] bg-[#FFF7F9] hover:bg-[#FFF0F3]/60'
                    }`}
                  >
                    <div className="relative mb-1">
                      <div className="w-14 h-14 rounded-full bg-white border border-[#FDE2E7] flex items-center justify-center text-[#F47D90] shadow-xs">
                        <Upload className="w-6 h-6 stroke-[2]" />
                      </div>
                    </div>

                    <div>
                      <p className="text-sm sm:text-base font-extrabold text-[#2D1F27]">
                        Drag & drop couple photos here
                      </p>
                      <p className="text-xs text-[#6B5360] font-medium mt-1">
                        Select multiple images together • or{' '}
                        <label
                          htmlFor="snap-multi-file-input"
                          className="text-[#F47D90] underline font-bold cursor-pointer hover:text-[#EE6A80]"
                        >
                          click to browse
                        </label>
                      </p>
                    </div>

                    <label
                      htmlFor="snap-multi-file-input"
                      className="mt-2 inline-flex items-center gap-2 px-6 py-2.5 rounded-full bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs sm:text-sm font-bold shadow-tuchi-glow transition-all cursor-pointer hover:scale-105 active:scale-95"
                    >
                      <PolaroidCamera3D size={20} />
                      <span>Choose Photos Together</span>
                    </label>
                  </div>
                )}
              </div>

              {/* 02 MEMORY TAG / CATEGORY */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2 text-[#2D1F27] font-heading">
                    <span className="text-[#F47D90]">02</span>
                    <span>MEMORY TAG / CATEGORY</span>
                  </label>
                  <button
                    type="button"
                    onClick={onOpenAddCategory}
                    className="text-xs font-bold text-[#F47D90] hover:text-[#EE6A80] flex items-center gap-1 cursor-pointer transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Custom Tag</span>
                  </button>
                </div>

                <div className="flex flex-wrap gap-2.5">
                  {mergedTags.map((tag) => {
                    const isSelected = categoryId === tag.id;

                    return (
                      <button
                        key={tag.id}
                        type="button"
                        onClick={() => setCategoryId(tag.id)}
                        className={`inline-flex items-center gap-2 px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer border ${
                          isSelected
                            ? 'bg-[#F47D90] border-[#F47D90] text-white shadow-tuchi-glow'
                            : 'bg-white border-[#FDE2E7] text-[#6B5360] hover:bg-[#FFF0F3] hover:border-[#F47D90]'
                        }`}
                      >
                        <Category3DIcon categoryName={tag.name} size={18} />
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

              {/* 03 CAPTION */}
              <div>
                <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2 text-[#2D1F27] font-heading">
                  <span className="text-[#F47D90]">03</span>
                  <span>SWEET TITLE / <span className="font-serif-italic font-bold text-[#F47D90]">Caption</span></span>
                </label>
                <div className="relative">
                  <input
                    type="text"
                    value={caption}
                    onChange={(e) => setCaption(e.target.value)}
                    placeholder="e.g. Late night ice cream run 🍦"
                    className="w-full px-4 py-3 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs sm:text-sm font-medium placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90] focus:ring-2 focus:ring-[#F47D90]/20 shadow-xs pr-10"
                  />
                  <Heart className="w-4 h-4 text-[#F47D90]/40 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                </div>
              </div>

              {/* 04 MEMORY STORY */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-extrabold uppercase tracking-wider flex items-center gap-2 text-[#2D1F27] font-heading">
                    <span className="text-[#F47D90]">04</span>
                    <span>MEMORY STORY / WHAT HAPPENED?</span>
                  </label>
                  <span className="text-[11px] text-[#6B5360] font-medium">Optional story</span>
                </div>
                <div className="relative">
                  <textarea
                    rows={3}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Describe what happened at this moment...&#10;e.g. A memorable day full of laughter and joy ✨"
                    className="w-full px-4 py-3 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs sm:text-sm font-medium placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90] focus:ring-2 focus:ring-[#F47D90]/20 shadow-xs resize-none pr-10"
                  />
                  <Heart className="w-4 h-4 text-[#F47D90]/40 absolute right-3.5 bottom-3.5 pointer-events-none" />
                </div>
              </div>

              {/* 05 DATE & 06 LOCATION */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                {/* 05 DATE */}
                <div>
                  <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2 text-[#2D1F27] font-heading">
                    <span className="text-[#F47D90]">05</span>
                    <span>DATE</span>
                  </label>
                  <div className="relative flex items-center bg-[#FFF7F9] border border-[#FDE2E7] rounded-2xl px-3.5 py-2.5 shadow-xs focus-within:border-[#F47D90]">
                    <Calendar className="w-4 h-4 text-[#F47D90] mr-2 flex-shrink-0" />
                    <input
                      type="date"
                      value={snapDate}
                      onChange={(e) => setSnapDate(e.target.value)}
                      className="w-full bg-transparent text-[#2D1F27] text-xs font-semibold focus:outline-none cursor-pointer"
                    />
                    <ChevronDown className="w-4 h-4 text-[#6B5360] pointer-events-none ml-1 flex-shrink-0" />
                  </div>
                </div>

                {/* 06 LOCATION */}
                <div>
                  <label className="block text-xs font-extrabold uppercase tracking-wider mb-2 flex items-center gap-2 text-[#2D1F27] font-heading">
                    <span className="text-[#F47D90]">06</span>
                    <span>LOCATION</span>
                  </label>
                  <div className="relative flex items-center bg-[#FFF7F9] border border-[#FDE2E7] rounded-2xl px-3.5 py-2.5 shadow-xs focus-within:border-[#F47D90]">
                    <MapPin className="w-4 h-4 text-[#F47D90] mr-2 flex-shrink-0" />
                    <input
                      type="text"
                      value={location}
                      onChange={(e) => setLocation(e.target.value)}
                      placeholder="Add location"
                      className="w-full bg-transparent text-[#2D1F27] text-xs font-medium placeholder:text-[#9E8592]/60 focus:outline-none"
                    />
                    <ChevronDown className="w-4 h-4 text-[#6B5360] pointer-events-none ml-1 flex-shrink-0" />
                  </div>
                </div>
              </div>

              {/* DRAFT SAVED TOAST ALERT */}
              {draftSaveToast && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="p-3 rounded-2xl bg-[#FFF0F3] border border-[#F47D90] text-[#F47D90] text-xs font-bold flex items-center gap-2 shadow-xs"
                >
                  <Bookmark className="w-4 h-4 text-[#F47D90]" />
                  <span>Draft saved successfully! You can resume editing anytime.</span>
                </motion.div>
              )}

              {/* ACTION BUTTONS: DRAFT & PUBLISH */}
              <div className="pt-2 flex items-center gap-3">
                <button
                  type="button"
                  onClick={handleSaveDraft}
                  disabled={isDeveloping}
                  className="py-3.5 sm:py-4 px-5 rounded-2xl bg-white hover:bg-[#FFF0F3] text-[#6B5360] border border-[#FDE2E7] font-bold text-xs sm:text-sm transition-all flex items-center justify-center gap-2 cursor-pointer shadow-xs"
                >
                  <Bookmark className="w-4 h-4 text-[#F47D90]" />
                  <span>Save Draft</span>
                </button>

                <motion.button
                  whileHover={{ scale: imageUrls.length > 0 ? 1.015 : 1 }}
                  whileTap={{ scale: imageUrls.length > 0 ? 0.985 : 1 }}
                  type="submit"
                  disabled={imageUrls.length === 0 || isDeveloping}
                  className={`flex-1 py-3.5 sm:py-4 px-6 rounded-2xl font-bold text-sm sm:text-base transition-all flex items-center justify-center gap-2 ${
                    imageUrls.length > 0 && !isDeveloping
                      ? 'bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white cursor-pointer shadow-tuchi-glow'
                      : 'bg-gray-100 text-gray-400 border border-gray-200 cursor-not-allowed'
                  }`}
                >
                  <Sparkles className="w-4 h-4 fill-white text-white" />
                  <span>
                    {isDeveloping
                      ? 'Saving Memory...'
                      : imageUrls.length > 1
                      ? `Save ${imageUrls.length} Photos as Memory ✨`
                      : 'Save Memory ✨'}
                  </span>
                </motion.button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
