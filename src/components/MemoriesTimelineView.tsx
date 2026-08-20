import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Category, CoupleProfile, Milestone, Snap } from '../types';
import { calculateDaysTogether, calculateMilestones, formatDate } from '../utils/dateUtils';
import {
  Heart3D,
  Sparkles3D,
  CalendarHeart3D,
  LoveLetter3D,
  GiftBox3D,
  Rose3D,
  Bouquet3D,
  Lock3D,
  Unlock3D,
} from './Romance3DIcons';
import {
  Calendar,
  Heart,
  Check,
  ChevronDown,
  ChevronUp,
  PartyPopper,
  Save,
  Bookmark,
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
      particleCount: 75,
      spread: 80,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#FFFFFF', '#FFB5C5'],
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

  // Custom milestone 3D icon helper
  const renderMilestone3DIcon = (id: string, size = 32) => {
    if (id.includes('month-1') || id.includes('honeymoon')) return <Rose3D size={size} />;
    if (id.includes('day-50')) return <Sparkles3D size={size} />;
    if (id.includes('day-100') || id.includes('century')) return <Bouquet3D size={size} />;
    if (id.includes('year-1')) return <GiftBox3D size={size} />;
    if (id.includes('day-500')) return <LoveLetter3D size={size} />;
    if (id.includes('day-1000')) return <CalendarHeart3D size={size} />;
    return <Heart3D size={size} />;
  };

  return (
    <div className="pb-32 pt-3 px-3.5 sm:px-6 max-w-4xl mx-auto space-y-6 text-[#2D1F27]">
      {/* 1. TOP HEADER BAR */}
      <header className="flex items-center justify-between px-1">
        {/* Brand Logo with Pink Heart */}
        <div className="flex items-center gap-1.5 cursor-pointer" onClick={onNavigateToUs}>
          <span className="text-3xl font-extrabold font-display-heavy text-[#2D1F27] tracking-tight flex items-center">
            Tiku
            <span className="text-[#F47D90] text-2xl ml-1">♥</span>
          </span>
        </div>

        {/* Right Actions: Calendar Icon + Couple Avatar Dropdown */}
        <div className="flex items-center gap-3">
          {/* Calendar Button */}
          <motion.button
            whileHover={{ scale: 1.08 }}
            whileTap={{ scale: 0.92 }}
            onClick={onNavigateToUs}
            className="w-10 h-10 rounded-full bg-white border border-[#FDE2E7] flex items-center justify-center text-[#6B5360] hover:text-[#2D1F27] shadow-xs cursor-pointer"
            title="Start Date & Settings"
          >
            <Calendar className="w-5 h-5 text-[#F47D90]" />
          </motion.button>

          {/* Couple Avatar Dropdown Pill */}
          <motion.div
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
            onClick={onNavigateToUs}
            className="flex items-center gap-1.5 p-1 pr-2.5 bg-white border border-[#FDE2E7] rounded-full shadow-xs cursor-pointer"
          >
            <div className="w-8 h-8 rounded-full overflow-hidden border border-[#F47D90] bg-[#FFF0F3]">
              <img src={heroPhotoUrl} alt="Couple" className="w-full h-full object-cover" />
            </div>
            <ChevronDown className="w-4 h-4 text-[#6B5360]" />
          </motion.div>
        </div>
      </header>

      {/* 2. HERO SECTION: OUR JOURNEY */}
      <section className="relative rounded-[36px] glass-panel border border-[#FDE2E7] shadow-[0_12px_32px_rgba(244,125,144,0.12)] p-6 sm:p-8 overflow-hidden bg-white">
        <div className="absolute top-1/2 left-1/3 w-64 h-64 bg-[#FFF0F3]/60 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 grid grid-cols-1 sm:grid-cols-12 gap-6 items-center">
          {/* Left Column: Headings & Start Date */}
          <div className="sm:col-span-7 space-y-3.5 text-left">
            <span className="text-[11px] font-extrabold text-[#F47D90] uppercase tracking-widest flex items-center gap-1.5">
              <span>—</span> LOVE MILESTONES & TIMELINE <span>—</span>
            </span>

            <div>
              <h2 className="text-3xl sm:text-4xl font-bold font-heading text-[#2D1F27] tracking-tight flex items-center gap-2">
                Our <span className="font-serif-italic font-bold text-[#F47D90]">Journey</span> <span className="text-2xl text-[#F47D90]">♡</span>
              </h2>
              <p className="text-xs sm:text-sm text-[#6B5360] font-medium mt-1.5 leading-relaxed">
                Milestones automatically unlock on anniversary and relationship dates
              </p>
            </div>

            {/* Clickable Start Date Pill */}
            <motion.div
              whileHover={{ scale: 1.04 }}
              whileTap={{ scale: 0.96 }}
              onClick={onNavigateToUs}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#FFF0F3] hover:bg-[#FDE2E7] text-[#F47D90] border border-[#FDE2E7] text-xs font-bold shadow-xs cursor-pointer transition-all"
            >
              <Calendar className="w-4 h-4 text-[#F47D90]" />
              <span>Started: {formatDate(profile.startDate)}</span>
            </motion.div>
          </div>

          {/* Right Column: Romance Centerpiece */}
          <div className="sm:col-span-5 flex justify-center sm:justify-end relative items-center">
            <motion.div
              whileHover={{ scale: 1.05, rotate: 3 }}
              className="relative cursor-pointer p-6 bg-gradient-to-tr from-[#FFF0F3] to-white rounded-[32px] border border-[#FDE2E7] shadow-tuchi-glow flex items-center justify-center"
              onClick={onNavigateToUs}
            >
              <Heart3D size={80} />
            </motion.div>
          </div>
        </div>
      </section>

      {/* 3. CURRENT RELATIONSHIP JOURNEY CARD */}
      <section className="glass-panel rounded-[32px] border border-[#FDE2E7] shadow-tuchi p-6 space-y-5 bg-white">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          {/* Left: Day Count */}
          <div className="space-y-1 text-center sm:text-left">
            <span className="text-[11px] font-extrabold text-[#6B5360] uppercase tracking-wider">
              CURRENT RELATIONSHIP JOURNEY
            </span>
            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#2D1F27] flex items-center justify-center sm:justify-start gap-1.5">
              Day <span className="font-serif-italic font-bold text-[#F47D90]">{daysTogether}</span> of Us <span className="text-2xl">💖</span>
            </h3>
            <p className="text-xs text-[#6B5360] font-medium">
              {unlockedCount} Milestones unlocked • {lockedCount} locked for future dates
            </p>
          </div>

          {/* Right: Cute Glass Card */}
          <div className="bg-[#FFF7F9] border border-[#FDE2E7] rounded-2xl p-3.5 sm:p-4 shadow-inner text-center max-w-[210px] sm:max-w-[230px] rotate-1">
            <p className="font-serif-italic text-lg text-[#F47D90] font-bold leading-none">Every single day</p>
            <p className="text-[11px] text-[#6B5360] font-semibold mt-0.5 leading-tight">
              is another golden page of our love story 💛
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
                ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                : 'bg-[#FFF7F9] text-[#6B5360] hover:text-[#2D1F27] border border-[#FDE2E7]'
            }`}
          >
            All ({milestones.length})
          </button>

          {/* Unlocked */}
          <button
            onClick={() => setActiveFilter('unlocked')}
            className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer ${
              activeFilter === 'unlocked'
                ? 'bg-emerald-600 text-white shadow-xs'
                : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-200'
            }`}
          >
            <Unlock3D size={14} />
            <span>Unlocked ({unlockedCount})</span>
          </button>

          {/* Locked */}
          <button
            onClick={() => setActiveFilter('locked')}
            className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-2xl text-xs font-bold transition-all cursor-pointer ${
              activeFilter === 'locked'
                ? 'bg-[#F47D90] text-white shadow-xs'
                : 'bg-[#FFF7F9] text-[#F47D90] hover:bg-[#FFF0F3] border border-[#FDE2E7]'
            }`}
          >
            <Lock3D size={14} />
            <span>Locked ({lockedCount})</span>
          </button>
        </div>
      </section>

      {/* 4. SECTION TITLE: RELATIONSHIP MILESTONES VAULT */}
      <div className="flex items-center justify-between px-1 pt-2">
        <h3 className="text-lg sm:text-xl font-bold font-heading text-[#2D1F27] flex items-center gap-2">
          <Bookmark className="w-5 h-5 text-[#F47D90] fill-[#F47D90]/20" />
          <span>Relationship Milestones <span className="font-serif-italic font-bold text-[#F47D90]">Vault</span></span>
        </h3>
        <span className="text-xs sm:text-sm font-bold text-[#F47D90]">
          {unlockedCount} / {milestones.length} Unlocked
        </span>
      </div>

      {/* 5. MILESTONE CARDS LIST WITH CONNECTING THREAD */}
      <div className="space-y-4 relative">
        {filteredMilestones.map((m) => {
          const isSelected = selectedMilestoneId === m.id;
          const progress = Math.min(100, Math.round((daysTogether / m.days) * 100));

          return (
            <motion.div
              key={m.id}
              layout
              className={`glass-panel rounded-[32px] p-6 border transition-all shadow-tuchi space-y-4 relative overflow-hidden bg-white ${
                m.reached ? 'border-[#FDE2E7]' : 'border-[#FDE2E7] opacity-90'
              }`}
            >
              {/* Top Row: Icon + Title & Subtitle + Unlocked/Locked Badge */}
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3.5">
                  {/* Rounded Squircle 3D Icon */}
                  <div
                    className={`w-13 h-13 rounded-2xl flex items-center justify-center border flex-shrink-0 shadow-xs ${
                      m.reached
                        ? 'bg-gradient-to-tr from-[#FFF0F3] to-white border-[#FDE2E7] shadow-tuchi-glow'
                        : 'bg-[#FFF7F9] border-[#FDE2E7]'
                    }`}
                  >
                    {renderMilestone3DIcon(m.id, 30)}
                  </div>

                  <div>
                    <h4 className="text-base sm:text-lg font-bold font-heading text-[#2D1F27]">
                      {m.label}
                    </h4>
                    <p className="text-xs text-[#6B5360] font-medium mt-0.5">
                      {m.subtitle}
                    </p>
                  </div>
                </div>

                {/* Badge (Unlocked or Locked) */}
                <div>
                  {m.reached ? (
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-extrabold shadow-2xs">
                      <Check className="w-3.5 h-3.5 stroke-[3]" />
                      <span>Unlocked</span>
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#FFF7F9] text-[#6B5360] border border-[#FDE2E7] text-xs font-bold">
                      <span>Day {m.days}</span>
                      <Lock3D size={14} />
                    </span>
                  )}
                </div>
              </div>

              {/* Sub-Card: Target Date & Completed Date */}
              <div className="bg-[#FFF7F9] border border-[#FDE2E7] rounded-2xl p-3.5 sm:p-4 flex items-center justify-between text-xs">
                <div className="space-y-0.5">
                  <p className="text-[11px] text-[#6B5360] font-medium">Target Date</p>
                  <p className="font-bold text-[#2D1F27]">{formatDate(m.targetDate)}</p>
                </div>

                <div className="w-8 h-8 rounded-xl bg-white border border-[#FDE2E7] flex items-center justify-center text-[#F47D90] shadow-xs">
                  <Calendar className="w-4 h-4" />
                </div>

                <div className="space-y-0.5 text-right">
                  <p className="text-[11px] text-[#6B5360] font-medium">
                    {m.reached ? 'Completed on' : 'Countdown'}
                  </p>
                  <p className={`font-bold ${m.reached ? 'text-[#2D1F27]' : 'text-[#F47D90]'}`}>
                    {m.reached ? formatDate(m.targetDate) : `In ${m.daysRemaining} days`}
                  </p>
                </div>
              </div>

              {/* Pink Progress Bar with Heart Marker */}
              <div className="relative pt-1">
                <div className="w-full bg-[#FFF0F3] h-2.5 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-[#F47D90] to-[#EE6A80] rounded-full transition-all duration-500 shadow-tuchi-glow"
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

              {/* Badges Row */}
              <div className="flex items-center justify-between pt-1">
                <span className="inline-flex items-center gap-1.5 text-[11px] font-bold text-[#F47D90] bg-[#FFF0F3] border border-[#FDE2E7] px-3 py-1 rounded-full">
                  <Sparkles3D size={12} />
                  <span>{m.rewardBadge || 'Sweet Spark'}</span>
                </span>

                {m.reached ? (
                  <span className="font-serif-italic text-sm sm:text-base font-bold text-[#2D1F27] bg-[#FFF0F3] border border-[#FDE2E7] px-3 py-0.5 rounded-full rotate-1">
                    Completed! ✨
                  </span>
                ) : (
                  <span className="text-[11px] font-bold text-[#6B5360] bg-[#FFF7F9] border border-[#FDE2E7] px-2.5 py-0.5 rounded-full flex items-center gap-1">
                    <Lock3D size={12} /> Locks until Day {m.days}
                  </span>
                )}
              </div>

              {/* Expand Toggle Button */}
              <div
                onClick={() => handleOpenMilestone(m)}
                className="pt-2 flex justify-center text-[#6B5360] hover:text-[#F47D90] cursor-pointer transition-colors"
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
                        <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-800 space-y-1">
                          <span className="font-extrabold flex items-center gap-1.5 text-emerald-900">
                            <PartyPopper className="w-4 h-4 text-emerald-600" /> Milestone Reflection Prompt:
                          </span>
                          <p className="italic font-medium font-serif-italic text-emerald-800">"{m.promptQuestion}"</p>
                        </div>

                        {/* Private Note for Milestone */}
                        <div>
                          <label className="block text-xs font-bold text-[#6B5360] mb-1.5 flex items-center gap-1.5">
                            <LoveLetter3D size={16} />
                            <span>Your Special Memory Note for Day {m.days}:</span>
                          </label>
                          <textarea
                            rows={3}
                            value={editingNote}
                            onChange={(e) => setEditingNote(e.target.value)}
                            placeholder="Write a sweet reflection or memory about reaching this milestone..."
                            className="w-full p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-xs sm:text-sm font-medium text-[#2D1F27] placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90] resize-none shadow-xs"
                          />
                        </div>

                        <div className="flex items-center justify-between gap-2">
                          {savedNoteSuccess ? (
                            <span className="text-xs font-bold text-emerald-600">✓ Note saved in vault!</span>
                          ) : (
                            <div />
                          )}

                          <button
                            type="button"
                            onClick={() => handleSaveNote(m.id)}
                            className="py-2 px-4 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-bold flex items-center gap-1.5 cursor-pointer transition-all shadow-tuchi-glow"
                          >
                            <Save className="w-3.5 h-3.5" />
                            <span>Save Note</span>
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 text-center space-y-1.5">
                        <div className="w-9 h-9 rounded-full bg-amber-100 text-amber-700 flex items-center justify-center mx-auto">
                          <Lock3D size={18} />
                        </div>
                        <h5 className="text-xs font-extrabold text-amber-900">Milestone Locked</h5>
                        <p className="text-xs text-amber-700 font-medium">
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
