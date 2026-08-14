import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Category, CoupleProfile, Milestone, Snap } from '../types';
import { calculateDaysTogether, calculateMilestones, formatDate, formatMonthYear } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import {
  Sparkles,
  Calendar,
  Award,
  Shuffle,
  Heart,
  MapPin,
  Lock,
  Unlock,
  CheckCircle2,
  Clock,
  PartyPopper,
  MessageSquareHeart,
  Save,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { PolaroidCard } from './PolaroidCard';

interface MemoriesTimelineViewProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  milestoneNotes?: Record<string, string>;
  onSaveMilestoneNote?: (milestoneId: string, note: string) => void;
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
}

export const MemoriesTimelineView: React.FC<MemoriesTimelineViewProps> = ({
  profile,
  categories,
  snaps,
  milestoneNotes = {},
  onSaveMilestoneNote,
  onSelectSnap,
  onToggleFavorite,
}) => {
  const daysTogether = calculateDaysTogether(profile.startDate);
  const milestones: Milestone[] = useMemo(() => calculateMilestones(profile.startDate), [profile.startDate]);

  // Selected milestone for viewing details/notes
  const [selectedMilestoneId, setSelectedMilestoneId] = useState<string | null>(null);
  const [editingNote, setEditingNote] = useState<string>('');
  const [savedNoteSuccess, setSavedNoteSuccess] = useState<boolean>(false);
  const [activeFilter, setActiveFilter] = useState<'all' | 'unlocked' | 'locked'>('all');

  // Random Memory Flashback
  const [randomSnapIndex, setRandomSnapIndex] = useState<number>(0);
  const randomSnap = snaps.length > 0 ? snaps[randomSnapIndex % snaps.length] : null;

  const handleShuffleMemory = () => {
    if (snaps.length > 1) {
      const nextIdx = Math.floor(Math.random() * snaps.length);
      setRandomSnapIndex(nextIdx);
    }
  };

  const handleTriggerConfetti = () => {
    confetti({
      particleCount: 60,
      spread: 70,
      origin: { y: 0.6 },
      colors: ['#FF4B72', '#FFDEE5', '#E8AFC0', '#BFE3D0', '#FFD700'],
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

  // Category Map
  const categoryMap = useMemo(() => {
    const map = new Map<string, Category>();
    categories.forEach((c) => map.set(c.id, c));
    return map;
  }, [categories]);

  // Group snaps chronologically by Month/Year
  const snapsByMonth = useMemo(() => {
    const sorted = [...snaps].sort(
      (a, b) => new Date(b.snapDate).getTime() - new Date(a.snapDate).getTime()
    );

    const groups: { [key: string]: Snap[] } = {};
    sorted.forEach((snap) => {
      const key = formatMonthYear(snap.snapDate) || 'Memories';
      if (!groups[key]) groups[key] = [];
      groups[key].push(snap);
    });

    return groups;
  }, [snaps]);

  const filteredMilestones = milestones.filter((m) => {
    if (activeFilter === 'unlocked') return m.reached;
    if (activeFilter === 'locked') return !m.reached;
    return true;
  });

  const unlockedCount = milestones.filter((m) => m.reached).length;
  const lockedCount = milestones.length - unlockedCount;

  return (
    <div className="pb-28 pt-4 px-4 sm:px-6 max-w-5xl mx-auto space-y-8">
      {/* HEADER */}
      <div className="text-center space-y-1.5">
        <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-white/80 backdrop-blur-md text-[#8C3F5D] text-xs font-extrabold border border-[#FFCCD5] shadow-xs">
          <Calendar className="w-3.5 h-3.5 text-[#FF4B72]" /> Love Milestones & Timeline
        </div>
        <h2 className="text-3xl sm:text-4xl font-extrabold font-heading text-[#3E2431] tracking-tight">
          Relationship Timeline
        </h2>
        <p className="text-xs sm:text-sm text-[#6E495B] font-medium max-w-md mx-auto">
          Milestones unlock automatically based on your relationship start date ({formatDate(profile.startDate)})
        </p>
      </div>

      {/* RELATIONSHIP DAYS BANNER WITH LOCK SUMMARY */}
      <section className="glass-panel rounded-3xl p-6 sm:p-7 border border-white/90 shadow-tuchi relative overflow-hidden glossy-top">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
          <div className="space-y-1">
            <span className="text-xs font-bold text-[#8C3F5D] uppercase tracking-wider">Current Relationship Journey</span>
            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#3E2431]">
              Day {daysTogether} of Us 💖
            </h3>
            <p className="text-xs text-[#6E495B]">
              {unlockedCount} Milestones unlocked • {lockedCount} locked for future dates
            </p>
          </div>

          {/* Unlocked / Locked Pill Toggles */}
          <div className="flex items-center gap-2 p-1.5 rounded-2xl bg-white/80 border border-[#FFCCD5]/80 shadow-xs">
            <button
              onClick={() => setActiveFilter('all')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer ${
                activeFilter === 'all'
                  ? 'bg-[#3E2431] text-white shadow-xs'
                  : 'text-[#6E495B] hover:text-[#3E2431]'
              }`}
            >
              All ({milestones.length})
            </button>
            <button
              onClick={() => setActiveFilter('unlocked')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center gap-1 ${
                activeFilter === 'unlocked'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'text-emerald-700 hover:bg-emerald-50'
              }`}
            >
              <Unlock className="w-3 h-3" /> Unlocked ({unlockedCount})
            </button>
            <button
              onClick={() => setActiveFilter('locked')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center gap-1 ${
                activeFilter === 'locked'
                  ? 'bg-[#8C3F5D] text-white shadow-xs'
                  : 'text-[#8C3F5D] hover:bg-[#FFE5EC]'
              }`}
            >
              <Lock className="w-3 h-3" /> Locked ({lockedCount})
            </button>
          </div>
        </div>
      </section>

      {/* MILESTONES GRID (DATE-LOCKED & UNLOCKED) */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold font-heading text-[#3E2431] flex items-center gap-2">
            <Award className="w-5 h-5 text-[#FF4B72]" />
            <span>Relationship Milestones Vault</span>
          </h3>
          <span className="text-xs font-bold text-[#6E495B]">
            {unlockedCount} / {milestones.length} Unlocked
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredMilestones.map((m) => {
            const isSelected = selectedMilestoneId === m.id;
            const progress = Math.min(100, Math.round((daysTogether / m.days) * 100));
            const hasSavedNote = Boolean(milestoneNotes[m.id]);

            return (
              <motion.div
                key={m.id}
                layout
                whileHover={{ y: -3 }}
                className={`glass-panel rounded-3xl p-5 border transition-all cursor-pointer flex flex-col justify-between relative overflow-hidden ${
                  m.reached
                    ? 'border-emerald-200/90 shadow-xs hover:border-emerald-300'
                    : 'border-white/90 opacity-90 hover:opacity-100'
                }`}
                onClick={() => handleOpenMilestone(m)}
              >
                {/* Top Status Header */}
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div
                    className={`w-11 h-11 rounded-2xl flex items-center justify-center border shadow-xs ${
                      m.reached
                        ? 'bg-gradient-to-br from-emerald-100 to-emerald-200 border-white text-emerald-800'
                        : 'bg-gradient-to-br from-[#FFE5EC] to-[#FFD0DC] border-white text-[#8C3F5D]'
                    }`}
                  >
                    {m.reached ? (
                      <CategoryIcon iconName={m.iconName} className="w-5 h-5 text-emerald-700" />
                    ) : (
                      <Lock className="w-5 h-5 text-[#8C3F5D]" />
                    )}
                  </div>

                  {m.reached ? (
                    <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-[11px] font-extrabold border border-emerald-200 shadow-2xs">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Unlocked
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-amber-50 text-amber-800 text-[11px] font-extrabold border border-amber-200 shadow-2xs">
                      <Clock className="w-3.5 h-3.5 text-amber-600" /> In {m.daysRemaining} days
                    </span>
                  )}
                </div>

                {/* Milestone Info */}
                <div className="space-y-1">
                  <div className="flex items-center justify-between">
                    <h4 className="text-base font-bold font-heading text-[#3E2431]">{m.label}</h4>
                    <span className="text-xs font-extrabold text-[#8C3F5D]">Day {m.days}</span>
                  </div>
                  <p className="text-xs text-[#6E495B] font-medium">{m.subtitle}</p>
                </div>

                {/* Date & Progress */}
                <div className="pt-3 mt-3 border-t border-[#FFDEE5]/60 space-y-2">
                  <div className="flex items-center justify-between text-[11px] font-bold">
                    <span className="text-[#6E495B]">Target Date:</span>
                    <span className={m.reached ? 'text-emerald-700' : 'text-[#8C3F5D]'}>
                      {formatDate(m.targetDate)}
                    </span>
                  </div>

                  {/* Progress Bar */}
                  <div className="w-full bg-[#FAF5F0] h-2 rounded-full overflow-hidden border border-[#FFDEE5]">
                    <div
                      className={`h-full rounded-full transition-all duration-700 ${
                        m.reached
                          ? 'bg-gradient-to-r from-emerald-400 to-emerald-500'
                          : 'bg-gradient-to-r from-[#FFB6C1] to-[#FF4B72]'
                      }`}
                      style={{ width: `${progress}%` }}
                    />
                  </div>

                  {/* Badges / Note tag */}
                  <div className="flex items-center justify-between pt-1">
                    <span className="text-[10px] font-bold text-[#8C3F5D] bg-[#FFE5EC] px-2 py-0.5 rounded-full border border-[#FFCCD5]">
                      {m.reached ? m.rewardBadge : `🔒 Locks until Day ${m.days}`}
                    </span>
                    {hasSavedNote && (
                      <span className="text-[10px] font-bold text-emerald-700 flex items-center gap-1">
                        <MessageSquareHeart className="w-3 h-3" /> Note written
                      </span>
                    )}
                  </div>
                </div>

                {/* Expand / Collapse Indicator */}
                <div className="pt-2 flex justify-center text-[#8C3F5D]/70">
                  {isSelected ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </div>

                {/* EXPANDED DETAILS / NOTE WRITER */}
                <AnimatePresence>
                  {isSelected && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      className="pt-3 border-t border-[#FFDEE5] space-y-3 cursor-default"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {m.reached ? (
                        <div className="space-y-2.5">
                          <div className="p-3 rounded-2xl bg-emerald-50/90 border border-emerald-200 text-xs text-emerald-900 space-y-1">
                            <span className="font-extrabold flex items-center gap-1">
                              <PartyPopper className="w-3.5 h-3.5 text-emerald-600" /> Milestone Question for Us:
                            </span>
                            <p className="italic font-medium text-[#3E2431]">"{m.promptQuestion}"</p>
                          </div>

                          {/* Private Note for Milestone */}
                          <div>
                            <label className="block text-[11px] font-bold text-[#6E495B] mb-1 flex items-center gap-1">
                              <MessageSquareHeart className="w-3 h-3 text-[#FF4B72]" />
                              <span>Your Special Memory Note for Day {m.days}:</span>
                            </label>
                            <textarea
                              rows={3}
                              value={editingNote}
                              onChange={(e) => setEditingNote(e.target.value)}
                              placeholder="Write a sweet reflection or memory about reaching this milestone..."
                              className="w-full p-2.5 rounded-xl bg-white border border-[#FFDEE5] text-xs text-[#3E2431] focus:outline-none focus:border-[#FF4B72] resize-none"
                            />
                          </div>

                          <div className="flex items-center justify-between gap-2">
                            {savedNoteSuccess ? (
                              <span className="text-[11px] font-bold text-emerald-600">✓ Note saved in vault!</span>
                            ) : <div />}

                            <button
                              type="button"
                              onClick={() => handleSaveNote(m.id)}
                              className="py-1.5 px-3 rounded-full bg-[#3E2431] hover:bg-[#8C3F5D] text-white text-xs font-bold flex items-center gap-1 cursor-pointer transition-all shadow-xs"
                            >
                              <Save className="w-3 h-3" /> Save Note
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="p-3 rounded-2xl bg-amber-50/90 border border-amber-200 text-center space-y-1.5">
                          <div className="w-8 h-8 rounded-full bg-amber-100 text-amber-800 flex items-center justify-center mx-auto">
                            <Lock className="w-4 h-4" />
                          </div>
                          <h5 className="text-xs font-extrabold text-amber-900">Milestone Locked</h5>
                          <p className="text-[11px] text-amber-800 font-medium">
                            This special milestone and prompt will automatically unlock on{' '}
                            <span className="font-bold">{formatDate(m.targetDate)}</span> when you reach Day {m.days}!
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
      </section>

      {/* RANDOM FLASHBACK MEMORY CARD */}
      {randomSnap && (
        <section className="glass-panel rounded-3xl p-6 border border-white/90 shadow-tuchi relative overflow-hidden glossy-top">
          <div className="flex flex-col sm:flex-row items-center gap-6">
            <div
              onClick={() => onSelectSnap(randomSnap)}
              className="w-full sm:w-52 aspect-4/3 rounded-2xl overflow-hidden shadow-xs cursor-pointer hover:scale-105 transition-transform flex-shrink-0 relative group"
            >
              <img
                src={randomSnap.imageUrl}
                alt="Memory Flashback"
                className="w-full h-full object-cover"
              />
              <div className="absolute inset-0 bg-black/20 group-hover:bg-transparent transition-colors" />
              <span className="absolute bottom-2 left-2 px-2.5 py-1 rounded-full bg-white/90 backdrop-blur-md text-[10px] font-bold text-[#3E2431] shadow-xs">
                Click to view photo
              </span>
            </div>

            <div className="flex-1 space-y-3 text-center sm:text-left">
              <div className="flex items-center justify-center sm:justify-between gap-2">
                <span className="px-3.5 py-1 rounded-full bg-[#BFE3D0] text-[#3E2431] text-xs font-extrabold flex items-center gap-1.5 shadow-2xs border border-white">
                  <Sparkles className="w-3.5 h-3.5 text-emerald-800" /> Memory Flashback
                </span>
                <button
                  onClick={handleShuffleMemory}
                  className="hidden sm:flex items-center gap-1.5 text-xs font-bold text-[#8C3F5D] hover:text-[#3E2431] transition-colors cursor-pointer"
                >
                  <Shuffle className="w-3.5 h-3.5 text-[#FF4B72]" /> Another Memory
                </button>
              </div>

              <h3 className="text-xl font-bold font-heading text-[#3E2431]">
                {randomSnap.caption || 'A sweet moment saved in time'}
              </h3>

              {randomSnap.description && (
                <p className="text-xs text-[#6E495B] line-clamp-2 italic">
                  "{randomSnap.description}"
                </p>
              )}

              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-xs text-[#6E495B] font-medium">
                <span className="flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5 text-[#FF4B72]" />
                  {formatDate(randomSnap.snapDate)}
                </span>
                {randomSnap.location && (
                  <span className="flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-[#FF4B72]" />
                    {randomSnap.location}
                  </span>
                )}
              </div>

              <div className="pt-1 sm:hidden">
                <button
                  onClick={handleShuffleMemory}
                  className="py-2 px-4 rounded-full bg-white border border-[#FFDEE5] text-xs font-bold text-[#3E2431] inline-flex items-center gap-1.5 cursor-pointer shadow-xs"
                >
                  <Shuffle className="w-3.5 h-3.5 text-[#FF4B72]" /> Shuffle Memory
                </button>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* CHRONOLOGICAL MEMORY TIMELINE */}
      <section className="space-y-6">
        <h3 className="text-xl font-bold font-heading text-[#3E2431] flex items-center gap-2">
          <Heart className="w-5 h-5 text-[#FF4B72]" /> Memory History
        </h3>

        {Object.keys(snapsByMonth).length > 0 ? (
          Object.keys(snapsByMonth).map((monthYear) => {
            const monthSnaps = snapsByMonth[monthYear];
            return (
              <div key={monthYear} className="space-y-4">
                {/* Month Header */}
                <div className="sticky top-2 z-10 inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/90 backdrop-blur-md border border-white/90 text-[#3E2431] text-xs font-bold shadow-xs">
                  <Calendar className="w-3.5 h-3.5 text-[#FF4B72]" />
                  <span>{monthYear}</span>
                  <span className="text-[10px] text-[#6E495B]">({monthSnaps.length})</span>
                </div>

                {/* Month Snaps Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 pt-1">
                  {monthSnaps.map((snap) => (
                    <PolaroidCard
                      key={snap.id}
                      snap={snap}
                      category={categoryMap.get(snap.categoryId)}
                      onSelect={onSelectSnap}
                      onToggleFavorite={onToggleFavorite}
                    />
                  ))}
                </div>
              </div>
            );
          })
        ) : (
          <div className="text-center py-10 glass-panel rounded-3xl p-6 border border-white/90">
            <p className="text-xs text-[#6E495B]">No memories saved yet. Start by adding your first snap from Home!</p>
          </div>
        )}
      </section>
    </div>
  );
};
