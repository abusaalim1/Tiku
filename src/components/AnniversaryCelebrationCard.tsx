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
      colors: ['#F47D90', '#FDE2E7', '#FFF0F3', '#EE6A80', '#FFFFFF'],
    });
  };

  // If today is any anniversary, automatically display celebratory banner
  if (isAnyAnniversaryToday) {
    const todayItem = annual.isToday ? annual : monthly;

    return (
      <motion.section
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="relative overflow-hidden rounded-3xl p-6 sm:p-7 bg-gradient-to-r from-[#F47D90] via-[#EE6A80] to-[#3E2431] text-white shadow-tuchi-glow border-2 border-white/80"
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
              className="py-3 px-5 rounded-full bg-white text-[#F47D90] hover:bg-[#FFF0F3] text-xs sm:text-sm font-black shadow-lg flex items-center gap-2 cursor-pointer transition-all border border-white"
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
    <section className="glass-panel rounded-3xl p-5 sm:p-6 border border-[#FDE2E7] shadow-tuchi relative overflow-hidden glossy-top space-y-4">
      {/* Top Header & Tab Toggle */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] via-white to-[#FDE2E7] border border-[#FDE2E7] text-[#F47D90] flex items-center justify-center shadow-xs flex-shrink-0">
            <PartyPopper className="w-5 h-5 text-[#F47D90]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-extrabold font-heading text-[#3E2431]">
                Anniversary Countdown
              </h3>
              {isUrgent && (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#F47D90] text-white text-[10px] font-black animate-pulse shadow-xs">
                  <Flame className="w-3 h-3 fill-current" /> Approaching!
                </span>
              )}
            </div>
            <p className="text-[11px] text-[#7A5C68] font-medium">
              Next celebration: <span className="font-bold text-[#F47D90]">{nextClosest.label}</span> in {nextClosest.daysRemaining} {nextClosest.daysRemaining === 1 ? 'day' : 'days'}
            </p>
          </div>
        </div>

        {/* Pill Toggle for Monthly vs Annual */}
        <div className="flex items-center gap-1.5 p-1 rounded-2xl bg-white border border-[#FDE2E7] shadow-xs self-stretch sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveTab('monthly')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'monthly'
                ? 'bg-[#3E2431] text-white shadow-xs'
                : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <Heart className="w-3.5 h-3.5 text-[#F47D90]" />
            <span>Monthly ({monthly.daysRemaining}d)</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('annual')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'annual'
                ? 'bg-[#3E2431] text-white shadow-xs'
                : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <Gift className="w-3.5 h-3.5 text-[#F47D90]" />
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
          className="p-4 sm:p-5 rounded-2xl bg-white border border-[#FDE2E7] flex flex-col sm:flex-row items-center justify-between gap-4 shadow-xs"
        >
          {/* Left info */}
          <div className="space-y-1.5 text-center sm:text-left w-full sm:w-auto">
            <div className="flex items-center justify-center sm:justify-start gap-2">
              <span className="text-xs font-bold text-[#F47D90] uppercase tracking-wider">
                {activeTab === 'monthly' ? 'Next Monthly Milestone' : 'Next Yearly Anniversary'}
              </span>
              <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#FFF0F3] text-[#F47D90] border border-[#FDE2E7]">
                {currentView.badgeEmoji} {currentView.countLabel}
              </span>
            </div>

            <h4 className="text-lg sm:text-xl font-bold font-heading text-[#3E2431]">
              {currentView.label}
            </h4>

            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-xs text-[#7A5C68]">
              <span className="flex items-center gap-1 font-semibold">
                <Calendar className="w-3.5 h-3.5 text-[#F47D90]" />
                {formatDate(currentView.targetDate)}
              </span>
              <span className="flex items-center gap-1 font-semibold text-[#F47D90]">
                <Clock className="w-3.5 h-3.5 text-[#F47D90]" />
                {currentView.daysRemaining} {currentView.daysRemaining === 1 ? 'day left' : 'days left'}
              </span>
            </div>
          </div>

          {/* Right Countdown Number & Confetti Sparkle Action */}
          <div className="flex items-center gap-3 w-full sm:w-auto justify-center sm:justify-end border-t sm:border-t-0 border-[#FDE2E7] pt-3 sm:pt-0">
            <div className="text-center px-4 py-2 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] min-w-[90px]">
              <div className="text-2xl sm:text-3xl font-black font-heading text-[#F47D90]">
                {currentView.daysRemaining}
              </div>
              <span className="text-[10px] font-extrabold uppercase text-[#7A5C68]">
                {currentView.daysRemaining === 1 ? 'Day to go' : 'Days to go'}
              </span>
            </div>

            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={triggerConfetti}
              title="Pop celebration confetti"
              className="py-3 px-3.5 rounded-2xl bg-[#3E2431] hover:bg-[#F47D90] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-all"
            >
              <Sparkles className="w-4 h-4 text-[#FDE2E7]" />
              <span className="hidden sm:inline">Sparkle</span>
            </motion.button>
          </div>
        </motion.div>
      </AnimatePresence>
    </section>
  );
};
