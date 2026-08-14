import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { CoupleProfile, UpcomingAnniversarySummary } from '../types';
import { formatDate } from '../utils/dateUtils';
import {
  PartyPopper,
  Calendar,
  Sparkles,
  Heart,
  Flame,
  Gift,
  Clock,
  ChevronRight,
  Award,
} from 'lucide-react';

interface AnniversaryCelebrationCardProps {
  profile: CoupleProfile;
  anniversaries: UpcomingAnniversarySummary;
  onNavigateToMemories?: () => void;
}

export const AnniversaryCelebrationCard: React.FC<AnniversaryCelebrationCardProps> = ({
  profile,
  anniversaries,
  onNavigateToMemories,
}) => {
  const [activeTab, setActiveTab] = useState<'monthly' | 'annual'>('monthly');

  const { monthly, annual, nextClosest, isAnyAnniversaryToday } = anniversaries;
  const currentView = activeTab === 'monthly' ? monthly : annual;

  const triggerConfetti = () => {
    confetti({
      particleCount: 70,
      spread: 80,
      origin: { y: 0.6 },
      colors: ['#FF4B72', '#FFCCD5', '#E8AFC0', '#FFD700', '#BFE3D0'],
    });
  };

  // If today is any anniversary, automatically display celebratory banner
  if (isAnyAnniversaryToday) {
    const todayItem = annual.isToday ? annual : monthly;

    return (
      <motion.section
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="relative overflow-hidden rounded-3xl p-6 sm:p-7 bg-gradient-to-r from-[#FF4B72] via-[#E85D75] to-[#8C3F5D] text-white shadow-tuchi-glow border-2 border-white/80"
      >
        {/* Floating background decorative hearts */}
        <div className="absolute top-2 right-4 text-white/20 text-4xl select-none animate-pulse">
          ✨
        </div>
        <div className="absolute bottom-2 left-4 text-white/20 text-3xl select-none">
          💖
        </div>

        <div className="relative z-10 flex flex-col sm:flex-row items-center justify-between gap-5 text-center sm:text-left">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-white/25 backdrop-blur-md text-white text-xs font-black uppercase tracking-wider border border-white/30 shadow-xs">
              <PartyPopper className="w-4 h-4 text-yellow-300 animate-bounce" />
              <span>Happy Anniversary Today! 🎉</span>
            </div>

            <h3 className="text-2xl sm:text-3xl font-black font-heading tracking-tight text-white drop-shadow-sm">
              Today is our {todayItem.countLabel} Anniversary! {todayItem.badgeEmoji}
            </h3>

            <p className="text-xs sm:text-sm text-white/90 font-medium max-w-lg leading-relaxed">
              Happy {todayItem.label} to {profile.userName || 'You'} & {profile.partnerName || 'Partner'}! Celebrating another beautiful chapter of love, laughter, and memories together.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row items-center gap-3 flex-shrink-0">
            <motion.button
              whileHover={{ scale: 1.06 }}
              whileTap={{ scale: 0.94 }}
              onClick={triggerConfetti}
              className="py-3 px-5 rounded-full bg-white text-[#FF4B72] hover:bg-yellow-50 text-xs sm:text-sm font-black shadow-lg flex items-center gap-2 cursor-pointer transition-all border border-white"
            >
              <Sparkles className="w-4 h-4 fill-current" />
              <span>Celebrate! 🎊</span>
            </motion.button>

            {onNavigateToMemories && (
              <button
                onClick={onNavigateToMemories}
                className="py-2.5 px-4 rounded-full bg-white/20 hover:bg-white/30 text-white text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer backdrop-blur-md border border-white/20"
              >
                <span>View Timeline</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      </motion.section>
    );
  }

  const isUrgent = nextClosest.daysRemaining <= 7;

  return (
    <section className="glass-panel rounded-3xl p-5 sm:p-6 border border-white/90 shadow-tuchi relative overflow-hidden glossy-top space-y-4">
      {/* Top Header & Tab Toggle */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-[#FFE5EC] via-[#FFF0F3] to-[#FFCCD5] border border-white text-[#8C3F5D] flex items-center justify-center shadow-xs flex-shrink-0">
            <PartyPopper className="w-5 h-5 text-[#FF4B72]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-extrabold font-heading text-[#3E2431]">
                Anniversary Countdown
              </h3>
              {isUrgent && (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#FF4B72] text-white text-[10px] font-black animate-pulse shadow-xs">
                  <Flame className="w-3 h-3 fill-current" /> Approaching!
                </span>
              )}
            </div>
            <p className="text-[11px] text-[#6E495B] font-medium">
              Next celebration: <span className="font-bold text-[#8C3F5D]">{nextClosest.label}</span> in {nextClosest.daysRemaining} {nextClosest.daysRemaining === 1 ? 'day' : 'days'}
            </p>
          </div>
        </div>

        {/* Pill Toggle for Monthly vs Annual */}
        <div className="flex items-center gap-1.5 p-1 rounded-2xl bg-white/80 border border-[#FFCCD5]/80 shadow-xs self-stretch sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveTab('monthly')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'monthly'
                ? 'bg-[#3E2431] text-white shadow-xs'
                : 'text-[#6E495B] hover:text-[#3E2431]'
            }`}
          >
            <Heart className="w-3.5 h-3.5 text-[#FF4B72]" />
            <span>Monthly ({monthly.daysRemaining}d)</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('annual')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'annual'
                ? 'bg-[#3E2431] text-white shadow-xs'
                : 'text-[#6E495B] hover:text-[#3E2431]'
            }`}
          >
            <Gift className="w-3.5 h-3.5 text-[#8C3F5D]" />
            <span>Annual ({annual.daysRemaining}d)</span>
          </button>
        </div>
      </div>

      {/* Detail Countdown Box */}
      <AnimatePresence mode="wait">
        <motion.div
          key={activeTab}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.2 }}
          className="p-4 sm:p-5 rounded-2xl bg-white/90 border border-white/90 flex flex-col sm:flex-row items-center justify-between gap-4 shadow-xs"
        >
          {/* Left info */}
          <div className="space-y-1.5 text-center sm:text-left w-full sm:w-auto">
            <div className="flex items-center justify-center sm:justify-start gap-2">
              <span className="text-xs font-bold text-[#8C3F5D] uppercase tracking-wider">
                {activeTab === 'monthly' ? 'Next Monthly Milestone' : 'Next Yearly Anniversary'}
              </span>
              <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#FFE5EC] text-[#8C3F5D] border border-[#FFCCD5]">
                {currentView.badgeEmoji} {currentView.countLabel}
              </span>
            </div>

            <h4 className="text-lg sm:text-xl font-bold font-heading text-[#3E2431]">
              {currentView.label}
            </h4>

            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-xs text-[#6E495B]">
              <span className="flex items-center gap-1 font-semibold">
                <Calendar className="w-3.5 h-3.5 text-[#FF4B72]" />
                {formatDate(currentView.targetDate)}
              </span>
              <span className="flex items-center gap-1 font-semibold text-[#8C3F5D]">
                <Clock className="w-3.5 h-3.5 text-[#8C3F5D]" />
                {currentView.daysRemaining} {currentView.daysRemaining === 1 ? 'day left' : 'days left'}
              </span>
            </div>
          </div>

          {/* Right Countdown Number & Confetti Sparkle Action */}
          <div className="flex items-center gap-3 w-full sm:w-auto justify-center sm:justify-end border-t sm:border-t-0 border-[#FFDEE5] pt-3 sm:pt-0">
            <div className="text-center px-4 py-2 rounded-2xl bg-[#FFF0F3] border border-[#FFCCD5] min-w-[90px]">
              <div className="text-2xl sm:text-3xl font-black font-heading text-[#FF4B72]">
                {currentView.daysRemaining}
              </div>
              <span className="text-[10px] font-extrabold uppercase text-[#8C3F5D]">
                {currentView.daysRemaining === 1 ? 'Day to go' : 'Days to go'}
              </span>
            </div>

            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={triggerConfetti}
              title="Pop celebration confetti"
              className="py-3 px-3.5 rounded-2xl bg-[#3E2431] hover:bg-[#8C3F5D] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-all"
            >
              <Sparkles className="w-4 h-4 text-[#FFDEE5]" />
              <span className="hidden sm:inline">Sparkle</span>
            </motion.button>
          </div>
        </motion.div>
      </AnimatePresence>
    </section>
  );
};
