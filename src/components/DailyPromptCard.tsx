import React, { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import {
  CoupleProfile,
  DailyPromptEntry,
  PromptCategory,
  PromptTemplate,
  Snap,
  Category,
} from '../types';
import {
  getDailyPromptForDate,
  getRandomPrompt,
  PROMPT_CATEGORIES_METADATA,
  calculatePromptStreak,
} from '../data/dailyPromptsData';
import { getTodayDateString, getRandomTilt } from '../utils/dateUtils';
import { compressSnapPhoto, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_MB } from '../utils/imageUtils';
import { PromptSelectorModal } from './PromptSelectorModal';
import { DailyPromptsHistoryModal } from './DailyPromptsHistoryModal';
import {
  Sparkles,
  Heart,
  Shuffle,
  BookOpen,
  Send,
  Camera,
  ImagePlus,
  X,
  Edit2,
  CheckCircle2,
  Flame,
  Users,
  MessageSquareHeart,
  Calendar,
  Layers,
  Check,
  Upload,
} from 'lucide-react';

interface DailyPromptCardProps {
  profile: CoupleProfile;
  categories: Category[];
  dailyPrompts: DailyPromptEntry[];
  onSavePromptEntry: (entry: DailyPromptEntry) => void;
  onDeletePromptEntry?: (entryId: string) => void;
  onSaveSnap?: (newSnap: {
    categoryId: string;
    imageUrl: string;
    caption?: string;
    description?: string;
    location?: string;
    snapDate: string;
    rotationDeg: number;
  }) => void;
}

export const DailyPromptCard: React.FC<DailyPromptCardProps> = ({
  profile,
  categories,
  dailyPrompts,
  onSavePromptEntry,
  onDeletePromptEntry,
  onSaveSnap,
}) => {
  const todayStr = getTodayDateString();

  // Find if today already has a saved entry
  const todayEntry = useMemo(
    () => dailyPrompts.find((p) => p.date === todayStr),
    [dailyPrompts, todayStr]
  );

  // Active prompt template state
  const [activePrompt, setActivePrompt] = useState<PromptTemplate>(() => {
    if (todayEntry) {
      return {
        id: todayEntry.promptId,
        category: todayEntry.category,
        type: todayEntry.type,
        text: todayEntry.question,
        emoji: PROMPT_CATEGORIES_METADATA[todayEntry.category]?.emoji || '💖',
      };
    }
    return getDailyPromptForDate(todayStr);
  });

  // Mode: 'both' (user + partner) or 'joint' (single shared reflection)
  const [answerMode, setAnswerMode] = useState<'both' | 'joint'>('both');
  const [userAnswer, setUserAnswer] = useState<string>('');
  const [partnerAnswer, setPartnerAnswer] = useState<string>('');
  const [jointAnswer, setJointAnswer] = useState<string>('');
  const [imageUrl, setImageUrl] = useState<string>('');
  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [isCompressingPhoto, setIsCompressingPhoto] = useState<boolean>(false);
  const [alsoSaveAsSnap, setAlsoSaveAsSnap] = useState<boolean>(true);
  const [selectedSnapCategory, setSelectedSnapCategory] = useState<string>(
    categories[0]?.id || 'cat-dates'
  );

  // Modals state
  const [isSelectorOpen, setIsSelectorOpen] = useState<boolean>(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState<boolean>(false);

  // Streak calculations
  const streakInfo = useMemo(
    () => calculatePromptStreak(dailyPrompts),
    [dailyPrompts]
  );

  // Sync inputs when today's entry changes
  useEffect(() => {
    if (todayEntry) {
      setUserAnswer(todayEntry.userAnswer || '');
      setPartnerAnswer(todayEntry.partnerAnswer || '');
      setJointAnswer(todayEntry.jointAnswer || '');
      setImageUrl(todayEntry.imageUrl || '');
      if (todayEntry.jointAnswer && !todayEntry.userAnswer && !todayEntry.partnerAnswer) {
        setAnswerMode('joint');
      }
      setActivePrompt({
        id: todayEntry.promptId,
        category: todayEntry.category,
        type: todayEntry.type,
        text: todayEntry.question,
        emoji: PROMPT_CATEGORIES_METADATA[todayEntry.category]?.emoji || '💖',
      });
    }
  }, [todayEntry]);

  const handleShuffle = () => {
    const next = getRandomPrompt(activePrompt.id);
    setActivePrompt(next);
    setIsEditing(true);
  };

  const handleSelectPrompt = (prompt: PromptTemplate) => {
    setActivePrompt(prompt);
    setIsEditing(true);
  };

  const handleSelectCustomPrompt = (customText: string, category: PromptCategory) => {
    setActivePrompt({
      id: `custom-${Date.now()}`,
      category,
      type: 'question',
      text: customText,
      emoji: PROMPT_CATEGORIES_METADATA[category]?.emoji || '💖',
    });
    setIsEditing(true);
  };

  const handlePhotoUpload = async (file: File) => {
    if (file && file.type.startsWith('image/')) {
      if (file.size > MAX_FILE_SIZE_BYTES) {
        alert(`Photo size is over ${MAX_FILE_SIZE_MB}MB. Please choose an image smaller than 20MB.`);
        return;
      }
      setIsCompressingPhoto(true);
      try {
        const compressed = await compressSnapPhoto(file);
        if (compressed) {
          setImageUrl(compressed);
        }
      } catch (err) {
        console.error('Error compressing prompt photo:', err);
      } finally {
        setIsCompressingPhoto(false);
      }
    }
  };

  const handleSaveAnswers = () => {
    const hasAnyAnswer =
      userAnswer.trim() || partnerAnswer.trim() || jointAnswer.trim() || imageUrl;

    if (!hasAnyAnswer) {
      alert('Please write at least one answer or upload a photo before saving!');
      return;
    }

    const entryId = todayEntry?.id || `dp-${todayStr}`;

    const newEntry: DailyPromptEntry = {
      id: entryId,
      date: todayStr,
      promptId: activePrompt.id,
      category: activePrompt.category,
      type: activePrompt.type,
      question: activePrompt.text,
      userAnswer: answerMode === 'both' ? userAnswer.trim() : undefined,
      partnerAnswer: answerMode === 'both' ? partnerAnswer.trim() : undefined,
      jointAnswer: answerMode === 'joint' ? jointAnswer.trim() : undefined,
      imageUrl: imageUrl || undefined,
      isCompleted: true,
      completedAt: new Date().toISOString(),
      createdAt: todayEntry?.createdAt || new Date().toISOString(),
    };

    onSavePromptEntry(newEntry);
    setIsEditing(false);

    // Confetti celebration!
    confetti({
      particleCount: 75,
      spread: 80,
      origin: { y: 0.6 },
      colors: ['#FF4B72', '#FFDEE5', '#E8AFC0', '#BFE3D0', '#FFD700'],
    });

    // Also auto-save as polaroid snap if checked
    if (alsoSaveAsSnap && onSaveSnap && imageUrl) {
      const summaryText =
        answerMode === 'both'
          ? `${profile.userName || 'You'}: "${userAnswer}" | ${profile.partnerName || 'Partner'}: "${partnerAnswer}"`
          : jointAnswer;

      onSaveSnap({
        categoryId: selectedSnapCategory,
        imageUrl: imageUrl,
        caption: `Daily Prompt: ${activePrompt.text}`,
        description: summaryText,
        location: 'Daily Prompt Journal',
        snapDate: todayStr,
        rotationDeg: getRandomTilt(),
      });
    }
  };

  const handleConvertToSnapFromHistory = (entry: DailyPromptEntry) => {
    if (!onSaveSnap) return;

    if (entry.imageUrl) {
      const summaryText =
        entry.userAnswer || entry.partnerAnswer
          ? `${profile.userName || 'You'}: "${entry.userAnswer || ''}" • ${profile.partnerName || 'Partner'}: "${entry.partnerAnswer || ''}"`
          : entry.jointAnswer || '';

      onSaveSnap({
        categoryId: categories[0]?.id || 'cat-dates',
        imageUrl: entry.imageUrl,
        caption: `Prompt: ${entry.question}`,
        description: summaryText,
        location: 'Couple Daily Prompt',
        snapDate: entry.date,
        rotationDeg: getRandomTilt(),
      });
      alert('✓ Saved to your Polaroid Memories Gallery!');
    } else {
      alert('This prompt entry has no photo attached. You can edit it to upload a cute snap first!');
    }
  };

  const meta = PROMPT_CATEGORIES_METADATA[activePrompt.category] || PROMPT_CATEGORIES_METADATA.romantic;
  const isCompleted = todayEntry?.isCompleted && !isEditing;

  return (
    <>
      <motion.section
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel rounded-3xl p-5 sm:p-7 border border-white/95 shadow-tuchi relative overflow-hidden glossy-top space-y-5"
      >
        {/* TOP BADGE & QUICK ACTIONS */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span
              className="px-3 py-1 rounded-full text-xs font-extrabold flex items-center gap-1.5 border shadow-2xs"
              style={{
                backgroundColor: meta.bgColor,
                color: meta.color,
                borderColor: meta.borderColor,
              }}
            >
              <span>{meta.emoji}</span>
              <span>{activePrompt.type === 'activity' ? 'Date Activity' : meta.label}</span>
            </span>

            {/* Streak Counter Badge */}
            {streakInfo.streak > 0 && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-orange-50 text-orange-700 text-xs font-extrabold border border-orange-200 shadow-2xs">
                <Flame className="w-3.5 h-3.5 text-orange-500 fill-orange-500" />
                <span>{streakInfo.streak} Day Streak!</span>
              </span>
            )}
          </div>

          {/* Action Buttons: Shuffle, Topics, History */}
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={handleShuffle}
              className="px-3 py-1.5 rounded-full bg-white/90 hover:bg-white text-[#3E2431] text-xs font-bold border border-neutral-200/90 flex items-center gap-1 cursor-pointer transition-all shadow-2xs"
              title="Shuffle to another question"
            >
              <Shuffle className="w-3.5 h-3.5 text-[#FF4B72]" />
              <span className="hidden sm:inline">Shuffle</span>
            </button>

            <button
              type="button"
              onClick={() => setIsSelectorOpen(true)}
              className="px-3 py-1.5 rounded-full bg-white/90 hover:bg-white text-[#3E2431] text-xs font-bold border border-neutral-200/90 flex items-center gap-1 cursor-pointer transition-all shadow-2xs"
              title="Browse topics or write your own"
            >
              <Sparkles className="w-3.5 h-3.5 text-[#8C3F5D]" />
              <span className="hidden sm:inline">Topics</span>
            </button>

            <button
              type="button"
              onClick={() => setIsHistoryOpen(true)}
              className="px-3 py-1.5 rounded-full bg-[#3E2431] hover:bg-[#8C3F5D] text-white text-xs font-bold flex items-center gap-1 cursor-pointer transition-all shadow-xs"
              title="View past prompt memories"
            >
              <BookOpen className="w-3.5 h-3.5 text-[#FFDEE5]" />
              <span>Journal ({dailyPrompts.length})</span>
            </button>
          </div>
        </div>

        {/* PROMPT QUESTION / ACTIVITY TEXT */}
        <div className="space-y-1.5">
          <div className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-[#8C3F5D]">
            <Calendar className="w-3.5 h-3.5 text-[#FF4B72]" />
            <span>Today's Couple Prompt</span>
          </div>

          <h3 className="text-xl sm:text-2xl font-extrabold font-heading text-[#3E2431] leading-tight">
            "{activePrompt.text}"
          </h3>

          {activePrompt.subtext && (
            <p className="text-xs sm:text-sm text-[#6E495B] font-medium italic">
              {activePrompt.subtext}
            </p>
          )}

          {activePrompt.suggestedAction && (
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl bg-purple-50 border border-purple-200 text-purple-900 text-xs font-bold mt-1">
              <span>✨ Tip: {activePrompt.suggestedAction}</span>
            </div>
          )}
        </div>

        {/* IF COMPLETED FOR TODAY: SHOW COMPLETED SUMMARY CARD */}
        {isCompleted ? (
          <div className="space-y-4 pt-1">
            {/* Completion Banner */}
            <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0" />
                <div>
                  <p className="text-xs font-extrabold text-emerald-900">
                    Prompt Answered for Today! 💖
                  </p>
                  <p className="text-[11px] text-emerald-700 font-medium">
                    Saved in your Couple Vault and Memories Journal.
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="px-3 py-1.5 rounded-xl bg-white hover:bg-emerald-100 text-emerald-900 text-xs font-bold border border-emerald-200 flex items-center gap-1 cursor-pointer transition-colors shadow-2xs"
              >
                <Edit2 className="w-3.5 h-3.5" />
                <span>Edit</span>
              </button>
            </div>

            {/* Display Answers in couple dialogue style */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              {todayEntry?.userAnswer && (
                <div className="p-4 rounded-2xl bg-white/90 border border-[#FFCCD5] shadow-xs space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold text-[#8C3F5D]">
                    <div className="w-6 h-6 rounded-full bg-[#FFDEE5] overflow-hidden flex items-center justify-center text-[11px]">
                      {profile.userPhotoUrl ? (
                        <img src={profile.userPhotoUrl} alt="You" className="w-full h-full object-cover" />
                      ) : (
                        (profile.userName || 'You')[0].toUpperCase()
                      )}
                    </div>
                    <span>{profile.userName || 'You'}'s Reflection:</span>
                  </div>
                  <p className="text-xs sm:text-sm text-[#3E2431] font-medium leading-relaxed italic">
                    "{todayEntry.userAnswer}"
                  </p>
                </div>
              )}

              {todayEntry?.partnerAnswer && (
                <div className="p-4 rounded-2xl bg-white/90 border border-[#E8AFC0] shadow-xs space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold text-[#4A2C3A]">
                    <div className="w-6 h-6 rounded-full bg-[#E8AFC0] overflow-hidden flex items-center justify-center text-[11px]">
                      {profile.partnerPhotoUrl ? (
                        <img src={profile.partnerPhotoUrl} alt="Partner" className="w-full h-full object-cover" />
                      ) : (
                        (profile.partnerName || 'Partner')[0].toUpperCase()
                      )}
                    </div>
                    <span>{profile.partnerName || 'Partner'}'s Reflection:</span>
                  </div>
                  <p className="text-xs sm:text-sm text-[#3E2431] font-medium leading-relaxed italic">
                    "{todayEntry.partnerAnswer}"
                  </p>
                </div>
              )}

              {todayEntry?.jointAnswer && (
                <div className="sm:col-span-2 p-4 rounded-2xl bg-white/90 border border-[#FFCCD5] shadow-xs space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold text-[#FF4B72]">
                    <Heart className="w-4 h-4 fill-current" />
                    <span>Our Shared Reflection:</span>
                  </div>
                  <p className="text-xs sm:text-sm text-[#3E2431] font-medium leading-relaxed italic">
                    "{todayEntry.jointAnswer}"
                  </p>
                </div>
              )}
            </div>

            {/* Photo if attached */}
            {todayEntry?.imageUrl && (
              <div className="flex items-center gap-3 p-3 rounded-2xl bg-white/80 border border-neutral-200/90">
                <div className="w-16 h-16 rounded-xl overflow-hidden shadow-xs flex-shrink-0">
                  <img src={todayEntry.imageUrl} alt="Attached snap" className="w-full h-full object-cover" />
                </div>
                <div className="space-y-0.5">
                  <p className="text-xs font-bold text-[#3E2431]">Attached Memory Photo 📸</p>
                  <p className="text-[11px] text-[#6E495B]">Included in today's daily prompt memory</p>
                </div>
              </div>
            )}
          </div>
        ) : (
          /* ANSWERING FORM (EXPANDED) */
          <div className="space-y-4 pt-1">
            {/* Toggle: Individual Answers vs Joint Reflection */}
            <div className="flex items-center justify-between gap-2 border-b border-[#FFCCD5]/60 pb-3">
              <span className="text-xs font-bold text-[#6E495B]">How would you like to answer?</span>
              <div className="flex items-center gap-1.5 p-1 rounded-xl bg-white border border-[#FFCCD5]/80 shadow-2xs text-xs">
                <button
                  type="button"
                  onClick={() => setAnswerMode('both')}
                  className={`px-3 py-1 rounded-lg font-bold transition-all cursor-pointer flex items-center gap-1 ${
                    answerMode === 'both'
                      ? 'bg-[#3E2431] text-white shadow-xs'
                      : 'text-[#6E495B] hover:text-[#3E2431]'
                  }`}
                >
                  <Users className="w-3 h-3" />
                  <span>Both of Us</span>
                </button>
                <button
                  type="button"
                  onClick={() => setAnswerMode('joint')}
                  className={`px-3 py-1 rounded-lg font-bold transition-all cursor-pointer flex items-center gap-1 ${
                    answerMode === 'joint'
                      ? 'bg-[#FF4B72] text-white shadow-xs'
                      : 'text-[#6E495B] hover:text-[#3E2431]'
                  }`}
                >
                  <Heart className="w-3 h-3" />
                  <span>Joint Note</span>
                </button>
              </div>
            </div>

            {/* INPUT FIELDS */}
            {answerMode === 'both' ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                {/* User Answer Field */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-[#8C3F5D] flex items-center gap-2">
                    <div className="w-5 h-5 rounded-full bg-[#FFDEE5] overflow-hidden flex items-center justify-center text-[10px]">
                      {profile.userPhotoUrl ? (
                        <img src={profile.userPhotoUrl} alt="You" className="w-full h-full object-cover" />
                      ) : (
                        (profile.userName || 'You')[0].toUpperCase()
                      )}
                    </div>
                    <span>{profile.userName || 'You'}'s Answer</span>
                  </label>
                  <textarea
                    rows={3}
                    value={userAnswer}
                    onChange={(e) => setUserAnswer(e.target.value)}
                    placeholder={`Write ${profile.userName || 'your'} answer here...`}
                    className="w-full p-3 rounded-2xl bg-white/95 border border-[#FFCCD5] text-xs font-medium text-[#3E2431] focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] shadow-2xs resize-none"
                  />
                </div>

                {/* Partner Answer Field */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-[#4A2C3A] flex items-center gap-2">
                    <div className="w-5 h-5 rounded-full bg-[#E8AFC0] overflow-hidden flex items-center justify-center text-[10px]">
                      {profile.partnerPhotoUrl ? (
                        <img src={profile.partnerPhotoUrl} alt="Partner" className="w-full h-full object-cover" />
                      ) : (
                        (profile.partnerName || 'Partner')[0].toUpperCase()
                      )}
                    </div>
                    <span>{profile.partnerName || 'Partner'}'s Answer</span>
                  </label>
                  <textarea
                    rows={3}
                    value={partnerAnswer}
                    onChange={(e) => setPartnerAnswer(e.target.value)}
                    placeholder={`Write ${profile.partnerName || "partner's"} answer here...`}
                    className="w-full p-3 rounded-2xl bg-white/95 border border-[#E8AFC0] text-xs font-medium text-[#3E2431] focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] shadow-2xs resize-none"
                  />
                </div>
              </div>
            ) : (
              /* Joint Reflection Input */
              <div className="space-y-1.5">
                <label className="text-xs font-bold text-[#FF4B72] flex items-center gap-1.5">
                  <Heart className="w-3.5 h-3.5 fill-current" />
                  <span>Our Shared Answer & Reflection</span>
                </label>
                <textarea
                  rows={3}
                  value={jointAnswer}
                  onChange={(e) => setJointAnswer(e.target.value)}
                  placeholder="Write what the two of you discussed or experienced together today..."
                  className="w-full p-3 rounded-2xl bg-white/95 border border-[#FFCCD5] text-xs font-medium text-[#3E2431] focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] shadow-2xs resize-none"
                />
              </div>
            )}

            {/* PHOTO ATTACHMENT SECTION */}
            <div className="pt-1">
              {imageUrl ? (
                <div className="relative inline-block rounded-2xl overflow-hidden border-2 border-white shadow-xs group">
                  <img src={imageUrl} alt="Attached" className="w-32 h-32 object-cover" />
                  <button
                    type="button"
                    onClick={() => setImageUrl('')}
                    className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-black/70 hover:bg-black text-white flex items-center justify-center transition-colors cursor-pointer"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                  <span className="absolute bottom-1 left-1 px-2 py-0.5 rounded-full bg-white/90 text-[10px] font-bold text-[#3E2431]">
                    Photo attached
                  </span>
                </div>
              ) : (
                <label className="flex items-center gap-2 p-3 rounded-2xl bg-white/80 border border-dashed border-[#E8AFC0] hover:bg-[#FFE5EC]/40 transition-colors cursor-pointer text-xs font-bold text-[#8C3F5D] w-fit">
                  <ImagePlus className="w-4 h-4 text-[#FF4B72]" />
                  <span>{isCompressingPhoto ? 'Processing image...' : 'Attach a couple photo for today (optional)'}</span>
                  <input
                    type="file"
                    accept="image/*"
                    disabled={isCompressingPhoto}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) handlePhotoUpload(file);
                    }}
                    className="hidden"
                  />
                </label>
              )}
            </div>

            {/* SAVE BUTTON & POLAROID CONVERSION CHECKBOX */}
            <div className="pt-2 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 border-t border-[#FFCCD5]/60">
              {imageUrl && onSaveSnap && (
                <label className="flex items-center gap-2 text-xs font-bold text-[#3E2431] cursor-pointer">
                  <input
                    type="checkbox"
                    checked={alsoSaveAsSnap}
                    onChange={(e) => setAlsoSaveAsSnap(e.target.checked)}
                    className="w-4 h-4 rounded text-[#FF4B72] accent-[#FF4B72]"
                  />
                  <span>Also save photo as Polaroid Memory in Gallery</span>
                </label>
              )}

              <div className="flex items-center justify-end gap-2 ml-auto">
                {isEditing && todayEntry && (
                  <button
                    type="button"
                    onClick={() => setIsEditing(false)}
                    className="px-4 py-2.5 rounded-2xl text-xs font-bold text-[#6E495B] hover:bg-white/80 cursor-pointer"
                  >
                    Cancel
                  </button>
                )}

                <motion.button
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  type="button"
                  onClick={handleSaveAnswers}
                  className="px-6 py-2.5 rounded-2xl bg-gradient-to-r from-[#3E2431] via-[#6E495B] to-[#FF4B72] text-white text-xs sm:text-sm font-extrabold shadow-tuchi flex items-center justify-center gap-2 cursor-pointer transition-transform"
                >
                  <Send className="w-3.5 h-3.5 text-[#FFDEE5]" />
                  <span>Save to Couple Memories</span>
                </motion.button>
              </div>
            </div>
          </div>
        )}
      </motion.section>

      {/* TOPIC SELECTOR MODAL */}
      <PromptSelectorModal
        isOpen={isSelectorOpen}
        onClose={() => setIsSelectorOpen(false)}
        onSelectPrompt={handleSelectPrompt}
        onSelectCustomPrompt={handleSelectCustomPrompt}
        currentPromptId={activePrompt.id}
      />

      {/* PROMPT JOURNAL HISTORY MODAL */}
      <DailyPromptsHistoryModal
        isOpen={isHistoryOpen}
        onClose={() => setIsHistoryOpen(false)}
        entries={dailyPrompts}
        profile={profile}
        streakInfo={streakInfo}
        onConvertToSnap={handleConvertToSnapFromHistory}
        onDeleteEntry={onDeletePromptEntry}
      />
    </>
  );
};
