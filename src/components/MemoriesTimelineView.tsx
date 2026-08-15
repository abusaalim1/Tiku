import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Category, CoupleProfile, Milestone, Snap } from '../types';
import { calculateDaysTogether, calculateMilestones, formatDate } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import {
  Calendar,
  Heart,
  Lock,
  Unlock,
  Check,
  ChevronDown,
  ChevronUp,
  PartyPopper,
  MessageSquareHeart,
  Save,
  Bookmark,
  Sparkles,
  Star,
  Gift,
  Compass,
  Trophy,
} from 'lucide-react';

interface MemoriesTimelineViewProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  milestoneNotes?: Record<string, string>;
  onSaveMilestoneNote?: (milestoneId: string, note: string) => void;
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
  onNavigateToUs?: () => void;
}

export const MemoriesTimelineView: React.FC<MemoriesTimelineViewProps> = ({
  profile,
  categories,
  snaps,
  milestoneNotes = {},
  onSaveMilestoneNote,
  onSelectSnap,
  onToggleFavorite,
  onNavigateToUs,
}) => {
  const daysTogether = calculateDaysTogether(profile.startDate) || 1168;
  const milestones: Milestone[] = useMemo(() => calculateMilestones(profile.startDate), [profile.startDate]);

  // Selected milestone for viewing details/notes
  const [selectedMilestoneId, setSelectedMilestoneId] = useState<string | null>(null);
  const [editingNote, setEditingNote] = useState<string>('');
  const [savedNoteSuccess, setSavedNoteSuccess] = useState<boolean>(false);
  const [activeFilter, setActiveFilter] = useState<'all' | 'unlocked' | 'locked'>('all');

  const heroPhotoUrl =
    profile.userPhotoUrl && profile.partnerPhotoUrl
      ? profile.userPhotoUrl
      : snaps[0]?.imageUrl ||
        'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=800&auto=format&fit=crop&q=80';

  const handleTriggerConfetti = () => {
    confetti({
      particleCount: 65,
      spread: 75,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#BFE3D0', '#FFD700'],
    });
  };

  const handleOpenMilestone = (m: Milestone) => {
    setSelectedMilestoneId(m.id === selectedMilestoneId ? null : m.id);
    setEditingNote(milestoneNotes[m.id] || '');
    setSavedNoteSuccess(false);
    if (m.reached) {
      handleTriggerConfetti();
    }
  };

  const handleSaveNote = (milestoneId: string) => {
    if (onSaveMilestoneNote) {
      onSaveMilestoneNote(milestoneId, editingNote);
      setSavedNoteSuccess(true);
      setTimeout(() => setSavedNoteSuccess(false), 2500);
    }
  };

  const filteredMilestones = milestones.filter((m) => {
    if (activeFilter === 'unlocked') return m.reached;
    if (activeFilter === 'locked') return !m.reached;
    return true;
  });

  const unlockedCount = milestones.filter((m) => m.reached).length;
  const lockedCount = milestones.length - unlockedCount;

  // Custom milestone icon helper
  const getMilestoneIcon = (iconName: string, id: string) => {
    if (id.includes('month-1') || id.includes('honeymoon')) return Heart;
    if (id.includes('day-50')) return Star;
    if (id.includes('day-100') || id.includes('century')) return Sparkles;
    if (id.includes('year-1')) return Trophy;
    if (id.includes('day-500')) return Gift;
    if (id.includes('day-1000')) return Compass;
    return Heart;
  };

  return (
    <div className="pb-32 pt-3 px-3.5 sm:px-6 max-w-4xl mx-auto space-y-6">
      {/* 1. TOP HEADER BAR */}
      <header className="flex items-center justify-between px-1">
        {/* Brand Logo with Pink Heart */}
        <div className="flex items-center gap-1.5 cursor-pointer" onClick={onNavigateToUs}>
          <span className="text-3xl font-extrabold font-heading text-[#3E2431] tracking-tight flex items-center">
            Tuchi
            <span className="text-[#F47D90] text-xl ml-0.5 animate-pulse-soft">♥</span>
          </span>
        </div>

        {/* Right Actions: Calendar Icon + Couple Avatar Dropdown */}
        <div className="flex items-center gap-3">
          {/* Calendar Button */}
          <motion.button
            whileHover={{ scale: 1.08 }}
            whileTap={{ scale: 0.92 }}
            onClick={onNavigateToUs}
            className="w-10 h-10 rounded-full bg-white/90 border border-[#FDE2E7] flex items-center justify-center text-[#3E2431] shadow-xs cursor-pointer"
            title="Start Date & Settings"
          >
            <Calendar className="w-5 h-5 text-[#3E2431]" />
          </motion.button>

          {/* Couple Avatar Dropdown Pill */}
          <motion.div
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
            onClick={onNavigateToUs}
            className="flex items-center gap-1.5 p-1 pr-2.5 bg-white/90 border border-[#FDE2E7] rounded-full shadow-xs cursor-pointer"
          >
            <div className="w-8 h-8 rounded-full overflow-hidden border border-white bg-[#FDE2E7]">
              <img src={heroPhotoUrl} alt="Couple" className="w-full h-full object-cover" />
            </div>
            <ChevronDown className="w-4 h-4 text-[#7A5C68]" />
          </motion.div>
        </div>
      </header>

      {/* 2. HERO SECTION: OUR JOURNEY & TILTED POLAROID */}
      <section className="relative rounded-[36px] bg-gradient-to-br from-[#FFF0F3] via-[#FFF7F9] to-[#FDE2E7] border border-[#FDE2E7] shadow-[0_12px_36px_rgba(244,125,144,0.12)] p-6 sm:p-8 overflow-hidden">
        {/* Subtle decorative glow & doodles */}
        <div className="absolute top-1/2 left-1/3 text-[#FDE2E7] pointer-events-none select-none opacity-40">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2">
            <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
          </svg>
        </div>

        <div className="relative z-10 grid grid-cols-1 sm:grid-cols-12 gap-6 items-center">
          {/* Left Column: Headings & Start Date */}
          <div className="sm:col-span-7 space-y-3.5 text-left">
            <span className="text-[11px] font-extrabold text-[#F47D90] uppercase tracking-widest flex items-center gap-1.5">
              <span>—</span> LOVE MILESTONES & TIMELINE <span>—</span>
            </span>

            <div>
              <h2 className="text-3xl sm:text-4xl font-bold font-heading text-[#3E2431] tracking-tight flex items-center gap-2">
                Our Journey <span className="text-2xl sm:text-3xl text-[#F47D90] font-normal">♡</span>
              </h2>
              <p className="text-xs sm:text-sm text-[#7A5C68] font-medium mt-1.5 leading-relaxed">
                Milestones unlock automatically based on our relationship start date
              </p>
            </div>

            {/* Clickable Start Date Pill */}
            <motion.div
              whileHover={{ scale: 1.04 }}
              whileTap={{ scale: 0.96 }}
              onClick={onNavigateToUs}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white hover:bg-[#FFF0F3] text-[#3E2431] border border-[#FDE2E7] text-xs font-bold shadow-xs cursor-pointer transition-all"
            >
              <Calendar className="w-4 h-4 text-[#F47D90]" />
              <span>{formatDate(profile.startDate)}</span>
            </motion.div>
          </div>

          {/* Right Column: Tilted Polaroid with Washi Tape & Dried Flower Twig */}
          <div className="sm:col-span-5 flex justify-center sm:justify-end relative">
            <motion.div
              whileHover={{ rotate: 0, scale: 1.03 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              className="relative -rotate-2 cursor-pointer group"
              onClick={onNavigateToUs}
            >
              {/* Pink Washi Tape Top Center */}
              <div className="absolute -top-3 left-1/2 -translate-x-1/2 z-20 w-16 h-5 bg-[#FDE2E7]/80 backdrop-blur-xs border border-white/60 -rotate-2 rounded-xs shadow-2xs" />

              {/* Polaroid Frame */}
              <div className="bg-white p-3 pb-7 rounded-[20px] shadow-[0_16px_36px_rgba(62,36,49,0.1)] border border-[#FDE2E7] max-w-[210px] sm:max-w-[230px]">
                <div className="w-44 sm:w-48 aspect-square rounded-[14px] overflow-hidden bg-[#FFF7F9]">
                  <img
                    src={heroPhotoUrl}
                    alt="Our Journey"
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  />
                </div>

                {/* Cursive Handwriting Label */}
                <div className="text-center mt-2.5 font-handwriting text-base font-bold text-[#3E2431]">
                  You + Me 💖
                </div>
              </div>

              {/* Delicate Botanical Twig Illustration on Right */}
              <div className="absolute -bottom-4 -right-6 z-20 select-none pointer-events-none text-[#C49A70] opacity-85">
                <svg width="44" height="60" viewBox="0 0 24 36" fill="none" stroke="currentColor" strokeWidth="1.2">
                  <path d="M12 36V2" />
                  <path d="M12 28C8 26 6 22 7 18" />
                  <path d="M12 22C16 20 18 16 17 12" />
                  <path d="M12 14C8 12 7 8 8 5" />
                  <circle cx="7" cy="18" r="1.5" fill="#FFF0DB" />
                  <circle cx="17" cy="12" r="1.5" fill="#FFF0DB" />
                  <circle cx="8" cy="5" r="1.5" fill="#FFF0DB" />
                  <circle cx="12" cy="2" r="1.5" fill="#FFF0DB" />
                </svg>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* 3. CURRENT RELATIONSHIP JOURNEY CARD WITH WASHI NOTE & FILTER PILLS */}
      <section className="rounded-[32px] bg-gradient-to-r from-[#FFF0F3] via-[#FFF7F9] to-[#FDE2E7] border border-[#FDE2E7] shadow-[0_8px_24px_rgba(244,125,144,0.12)] p-6 space-y-5">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          {/* Left: Day Count */}
          <div className="space-y-1 text-center sm:text-left">
            <span className="text-[11px] font-extrabold text-[#7A5C68] uppercase tracking-wider">
              CURRENT RELATIONSHIP JOURNEY
            </span>
            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#3E2431] flex items-center justify-center sm:justify-start gap-1.5">
              Day {daysTogether} of Us <span className="text-2xl">💖</span>
            </h3>
            <p className="text-xs text-[#7A5C68] font-medium">
              {unlockedCount} Milestones unlocked • {lockedCount} locked for future dates
            </p>
          </div>

          {/* Right: Cute Stamped Washi Card */}
          <div className="bg-white/90 border border-[#FDE2E7] rounded-2xl p-3.5 sm:p-4 shadow-2xs text-center max-w-[210px] sm:max-w-[230px] rotate-1">
            <p className="font-handwriting text-lg text-[#F47D90] font-bold leading-none">Every day</p>
            <p className="text-[11px] text-[#7A5C68] font-semibold mt-0.5 leading-tight">
              is another chapter of our love story 💛
            </p>
          </div>
        </div>

        {/* Filter Pills Bar */}
        <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-[#FDE2E7]">
          {/* All */}
          <button
            onClick={() => setActiveFilter('all')}
            className={`px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer ${
              activeFilter === 'all'
                ? 'bg-[#3E2431] text-white shadow-xs'
                : 'bg-white/80 text-[#7A5C68] hover:bg-white border border-[#FDE2E7]'
            }`}
          >
            All ({milestones.length})
          </button>

          {/* Unlocked */}
          <button
            onClick={() => setActiveFilter('unlocked')}
            className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer ${
              activeFilter === 'unlocked'
                ? 'bg-[#2A9D6F] text-white shadow-xs'
                : 'bg-white/80 text-[#2A9D6F] hover:bg-[#E8F8F0] border border-[#BEE9D5]'
            }`}
          >
            <Unlock className="w-3.5 h-3.5" />
            <span>Unlocked ({unlockedCount})</span>
          </button>

          {/* Locked */}
          <button
            onClick={() => setActiveFilter('locked')}
            className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer ${
              activeFilter === 'locked'
                ? 'bg-[#F47D90] text-white shadow-xs'
                : 'bg-white/80 text-[#F47D90] hover:bg-[#FFF0F3] border border-[#FDE2E7]'
            }`}
          >
            <Lock className="w-3.5 h-3.5" />
            <span>Locked ({lockedCount})</span>
          </button>
        </div>
      </section>

      {/* 4. SECTION TITLE: RELATIONSHIP MILESTONES VAULT */}
      <div className="flex items-center justify-between px-1 pt-2">
        <h3 className="text-lg sm:text-xl font-bold font-heading text-[#3E2431] flex items-center gap-2">
          <Bookmark className="w-5 h-5 text-[#F47D90] fill-[#F47D90]/20" />
          <span>Relationship Milestones Vault</span>
        </h3>
        <span className="text-xs sm:text-sm font-bold text-[#F47D90]">
          {unlockedCount} / {milestones.length} Unlocked
        </span>
      </div>

      {/* 5. MILESTONE CARDS LIST */}
      <div className="space-y-4">
        {filteredMilestones.map((m) => {
          const isSelected = selectedMilestoneId === m.id;
          const progress = Math.min(100, Math.round((daysTogether / m.days) * 100));
          const hasSavedNote = Boolean(milestoneNotes[m.id]);
          const IconComp = getMilestoneIcon(m.iconName, m.id);

          return (
            <motion.div
              key={m.id}
              layout
              className={`bg-white/95 rounded-[32px] p-6 border transition-all shadow-[0_8px_24px_rgba(244,125,144,0.12)] space-y-4 relative overflow-hidden ${
                m.reached ? 'border-[#FDE2E7]' : 'border-[#FDE2E7] opacity-95'
              }`}
            >
              {/* Botanical Watermark in Bottom Left */}
              <div className="absolute bottom-2 left-3 pointer-events-none opacity-25 select-none text-[#FDE2E7]">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2">
                  <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 3.5 2 7a7 7 0 0 1-10 11Z" />
                  <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12" />
                </svg>
              </div>

              {/* Top Row: Icon + Title & Subtitle + Unlocked/Locked Badge */}
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3.5">
                  {/* Rounded Squircle Icon */}
                  <div
                    className={`w-12 h-12 rounded-2xl flex items-center justify-center border flex-shrink-0 shadow-2xs ${
                      m.reached
                        ? m.id.includes('day-50')
                          ? 'bg-[#FFF9E6] border-[#FFEBAA] text-[#E5A100]'
                          : 'bg-[#FFF0F3] border-[#FDE2E7] text-[#F47D90]'
                        : 'bg-[#FFF0F3] border-[#FDE2E7] text-[#7A5C68]'
                    }`}
                  >
                    <IconComp className="w-6 h-6 stroke-[1.8]" />
                  </div>

                  <div>
                    <h4 className="text-base sm:text-lg font-bold font-heading text-[#3E2431]">
                      {m.label}
                    </h4>
                    <p className="text-xs text-[#7A5C68] font-medium mt-0.5">
                      {m.subtitle}
                    </p>
                  </div>
                </div>

                {/* Badge (Unlocked or Locked) */}
                <div>
                  {m.reached ? (
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#E8F8F0] text-[#2A9D6F] border border-[#BEE9D5] text-xs font-extrabold shadow-2xs">
                      <Check className="w-3.5 h-3.5 stroke-[3]" />
                      <span>Unlocked</span>
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#FFF0F3] text-[#7A5C68] border border-[#FDE2E7] text-xs font-bold">
                      <span>Day {m.days}</span>
                      <Lock className="w-3.5 h-3.5 text-[#7A5C68]" />
                    </span>
                  )}
                </div>
              </div>

              {/* Sub-Card: Target Date & Completed Date */}
              <div className="bg-[#FFF7F9] border border-[#FDE2E7] rounded-2xl p-3.5 sm:p-4 flex items-center justify-between text-xs">
                <div className="space-y-0.5">
                  <p className="text-[11px] text-[#7A5C68] font-medium">Target Date</p>
                  <p className="font-bold text-[#3E2431]">{formatDate(m.targetDate)}</p>
                </div>

                <div className="w-8 h-8 rounded-xl bg-white border border-[#FDE2E7] flex items-center justify-center text-[#F47D90] shadow-2xs">
                  <Calendar className="w-4 h-4" />
                </div>

                <div className="space-y-0.5 text-right">
                  <p className="text-[11px] text-[#7A5C68] font-medium">
                    {m.reached ? 'Completed on' : 'Countdown'}
                  </p>
                  <p className={`font-bold ${m.reached ? 'text-[#3E2431]' : 'text-[#F47D90]'}`}>
                    {m.reached ? formatDate(m.targetDate) : `In ${m.daysRemaining} days`}
                  </p>
                </div>
              </div>

              {/* Pink Progress Bar with Heart Marker */}
              <div className="relative pt-1">
                <div className="w-full bg-[#FFF0F3] h-2 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-[#FDE2E7] to-[#F47D90] rounded-full transition-all duration-500"
                    style={{ width: `${progress}%` }}
                  />
                </div>
                {m.reached && (
                  <div
                    className="absolute -top-1 text-sm select-none pointer-events-none -translate-x-1/2"
                    style={{ left: `${Math.max(5, Math.min(97, progress))}%` }}
                  >
                    💖
                  </div>
                )}
              </div>

              {/* Badges Row: Reward Badge & Completed status */}
              <div className="flex items-center justify-between pt-1">
                <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#F47D90] bg-[#FFF0F3] border border-[#FDE2E7] px-3 py-1 rounded-full">
                  <span>🌸</span> {m.rewardBadge || 'Sweet Spark'}
                </span>

                {m.reached ? (
                  <span className="font-handwriting text-sm sm:text-base font-bold text-[#3E2431] bg-[#FFF7F9] border border-[#FDE2E7] px-3 py-0.5 rounded-full rotate-1">
                    Completed! ✨
                  </span>
                ) : (
                  <span className="text-[11px] font-bold text-[#7A5C68] bg-[#FFF7F9] border border-[#FDE2E7] px-2.5 py-0.5 rounded-full">
                    🔒 Locks until Day {m.days}
                  </span>
                )}
              </div>

              {/* Expand Toggle Button */}
              <div
                onClick={() => handleOpenMilestone(m)}
                className="pt-2 flex justify-center text-[#7A5C68] hover:text-[#F47D90] cursor-pointer transition-colors"
              >
                {isSelected ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
              </div>

              {/* EXPANDED DETAILS / NOTE WRITER */}
              <AnimatePresence>
                {isSelected && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    className="pt-3 border-t border-[#FDE2E7] space-y-3.5 cursor-default"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {m.reached ? (
                      <div className="space-y-3">
                        <div className="p-3.5 rounded-2xl bg-[#E8F8F0] border border-[#BEE9D5] text-xs text-[#1E7250] space-y-1">
                          <span className="font-extrabold flex items-center gap-1.5">
                            <PartyPopper className="w-4 h-4 text-[#2A9D6F]" /> Milestone Reflection Prompt:
                          </span>
                          <p className="italic font-medium text-[#3E2431]">"{m.promptQuestion}"</p>
                        </div>

                        {/* Private Note for Milestone */}
                        <div>
                          <label className="block text-xs font-bold text-[#7A5C68] mb-1.5 flex items-center gap-1.5">
                            <MessageSquareHeart className="w-4 h-4 text-[#F47D90]" />
                            <span>Your Special Memory Note for Day {m.days}:</span>
                          </label>
                          <textarea
                            rows={3}
                            value={editingNote}
                            onChange={(e) => setEditingNote(e.target.value)}
                            placeholder="Write a sweet reflection or memory about reaching this milestone..."
                            className="w-full p-3 rounded-2xl bg-white border border-[#FDE2E7] text-xs sm:text-sm font-medium text-[#3E2431] focus:outline-none focus:border-[#F47D90] focus:ring-2 focus:ring-[#FFF0F3] resize-none"
                          />
                        </div>

                        <div className="flex items-center justify-between gap-2">
                          {savedNoteSuccess ? (
                            <span className="text-xs font-bold text-[#2A9D6F]">✓ Note saved in vault!</span>
                          ) : (
                            <div />
                          )}

                          <button
                            type="button"
                            onClick={() => handleSaveNote(m.id)}
                            className="py-2 px-4 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white text-xs font-bold flex items-center gap-1.5 cursor-pointer transition-all shadow-xs"
                          >
                            <Save className="w-3.5 h-3.5" />
                            <span>Save Note</span>
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="p-4 rounded-2xl bg-[#FFF9E6] border border-[#FFEBAA] text-center space-y-1.5">
                        <div className="w-9 h-9 rounded-full bg-[#FFF0DB] text-[#8C5E3C] flex items-center justify-center mx-auto">
                          <Lock className="w-4 h-4" />
                        </div>
                        <h5 className="text-xs font-extrabold text-[#3E2431]">Milestone Locked</h5>
                        <p className="text-xs text-[#7A5C68] font-medium">
                          This special milestone and prompt will automatically unlock on{' '}
                          <span className="font-bold text-[#F47D90]">{formatDate(m.targetDate)}</span> when you reach Day {m.days}!
                        </p>
                      </div>
                    )}
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
};
