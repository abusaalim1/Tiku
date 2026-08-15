import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { CoupleProfile } from '../types';
import { calculateUpcomingAnniversaries, formatDate, calculateDaysTogether } from '../utils/dateUtils';
import {
  Heart,
  Calendar,
  Gift,
  PartyPopper,
  Sparkles,
  ChevronRight,
  Clock,
  Flame,
  Star,
  Cake,
} from 'lucide-react';

interface AnniversaryCountdownWidgetProps {
  profile: CoupleProfile;
  onNavigateToMemories?: () => void;
  onNavigateToUs?: () => void;
}

export const AnniversaryCountdownWidget: React.FC<AnniversaryCountdownWidgetProps> = ({
  profile,
  onNavigateToMemories,
  onNavigateToUs,
}) => {
  const [selectedType, setSelectedType] = useState<'annual' | 'monthly'>('annual');

  const anniversaries = useMemo(
    () => calculateUpcomingAnniversaries(profile.startDate),
    [profile.startDate]
  );

  const daysTogether = calculateDaysTogether(profile.startDate);
  const { annual, monthly, isAnyAnniversaryToday } = anniversaries;
  const currentItem = selectedType === 'annual' ? annual : monthly;

  const triggerConfetti = () => {
    confetti({
      particleCount: 75,
      spread: 80,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#FDE2E7', '#FFF0F3', '#EE6A80', '#FFFFFF'],
    });
  };

  // Progress percentage calculation towards the next anniversary
  const progressPercent = useMemo(() => {
    if (selectedType === 'annual') {
      // 365 days cycle
      const remaining = Math.max(0, Math.min(365, annual.daysRemaining));
      return Math.round(((365 - remaining) / 365) * 100);
    } else {
      // ~30 days cycle
      const remaining = Math.max(0, Math.min(30, monthly.daysRemaining));
      return Math.round(((30 - remaining) / 30) * 100);
    }
  }, [selectedType, annual.daysRemaining, monthly.daysRemaining]);

  const isApproaching = currentItem.daysRemaining <= 7 && currentItem.daysRemaining > 0;

  return (
    <section className="relative rounded-[32px] bg-gradient-to-br from-[#FFF5F7] via-white to-[#FFF0F4] border border-[#FDE2E7] shadow-[0_10px_30px_rgba(244,125,144,0.12)] p-5 sm:p-6 overflow-hidden">
      {/* Cute Decorative Stickers / Glow */}
      <div className="absolute -top-6 -right-6 w-32 h-32 bg-[#FDE2E7]/60 rounded-full blur-2xl pointer-events-none" />
      <div className="absolute -bottom-6 -left-6 w-32 h-32 bg-[#FFF0F3]/80 rounded-full blur-2xl pointer-events-none" />

      {/* If today is the Anniversary, show joyful celebration banner */}
      {isAnyAnniversaryToday ? (
        <div className="relative z-10 space-y-4">
          <div className="flex items-center justify-between">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#F47D90] text-white text-xs font-extrabold shadow-xs">
              <PartyPopper className="w-3.5 h-3.5 animate-bounce" />
              <span>Today is the Day! 🎉</span>
            </div>
            <span className="text-2xl animate-pulse">💖</span>
          </div>

          <div className="text-center sm:text-left space-y-1.5">
            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#3E2431]">
              Happy {annual.isToday ? annual.label : monthly.label}! {annual.isToday ? '💍' : '🌸'}
            </h3>
            <p className="text-xs sm:text-sm text-[#6E495B] font-medium leading-relaxed">
              Celebrating another unforgettable chapter of love with{' '}
              <span className="font-bold text-[#F47D90]">
                {profile.userName || 'You'} & {profile.partnerName || 'Partner'}
              </span>
              . Today marks a milestone in your story!
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={triggerConfetti}
              className="py-2.5 px-5 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white text-xs sm:text-sm font-bold shadow-xs flex items-center gap-2 cursor-pointer transition-all"
            >
              <Sparkles className="w-4 h-4 fill-current" />
              <span>Celebrate with Confetti! 🎊</span>
            </motion.button>

            {onNavigateToMemories && (
              <button
                onClick={onNavigateToMemories}
                className="py-2.5 px-4 rounded-full bg-white hover:bg-[#FFF0F3] text-[#3E2431] border border-[#FDE2E7] text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-2xs"
              >
                <span>View Love Timeline</span>
                <ChevronRight className="w-3.5 h-3.5 text-[#F47D90]" />
              </button>
            )}
          </div>
        </div>
      ) : (
        /* Normal Countdown State */
        <div className="relative z-10 space-y-4">
          {/* Top Row: Title, Subtitle, & Annual/Monthly Toggle Pills */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90] shadow-2xs flex-shrink-0">
                {selectedType === 'annual' ? (
                  <Gift className="w-5 h-5" />
                ) : (
                  <Cake className="w-5 h-5" />
                )}
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-base sm:text-lg font-bold font-heading text-[#3E2431]">
                    Anniversary Countdown
                  </h3>
                  {isApproaching && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#F47D90] text-white text-[10px] font-extrabold animate-pulse shadow-xs">
                      <Flame className="w-3 h-3 fill-current" /> Coming Soon!
                    </span>
                  )}
                </div>
                <p className="text-xs text-[#7A5C68] font-medium">
                  Counting down every special milestone together
                </p>
              </div>
            </div>

            {/* Toggle Pills: Annual vs Monthly */}
            <div className="flex items-center gap-1 p-1 bg-white border border-[#FDE2E7] rounded-2xl shadow-2xs self-start sm:self-auto">
              <button
                type="button"
                onClick={() => setSelectedType('annual')}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  selectedType === 'annual'
                    ? 'bg-[#3E2431] text-white shadow-xs'
                    : 'text-[#7A5C68] hover:text-[#3E2431] hover:bg-[#FFF0F3]'
                }`}
              >
                <Star className="w-3.5 h-3.5 text-[#F47D90]" />
                <span>Annual ({annual.daysRemaining}d)</span>
              </button>

              <button
                type="button"
                onClick={() => setSelectedType('monthly')}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  selectedType === 'monthly'
                    ? 'bg-[#3E2431] text-white shadow-xs'
                    : 'text-[#7A5C68] hover:text-[#3E2431] hover:bg-[#FFF0F3]'
                }`}
              >
                <Heart className="w-3.5 h-3.5 text-[#F47D90]" />
                <span>Monthly ({monthly.daysRemaining}d)</span>
              </button>
            </div>
          </div>

          {/* Main Countdown Display Card */}
          <div className="bg-white border border-[#FDE2E7] rounded-2xl p-4 sm:p-5 shadow-xs grid grid-cols-1 sm:grid-cols-12 gap-4 items-center">
            {/* Left Box: Big Countdown Number & Label */}
            <div className="sm:col-span-5 flex items-center gap-3.5">
              <div className="bg-gradient-to-br from-[#FFF0F3] to-[#FDE2E7] border border-[#FDE2E7] rounded-2xl px-4 py-2.5 text-center shadow-xs flex-shrink-0 min-w-[84px]">
                <span className="text-3xl sm:text-4xl font-extrabold font-heading text-[#F47D90] leading-none block">
                  {currentItem.daysRemaining}
                </span>
                <span className="text-[10px] font-extrabold uppercase text-[#7A5C68] tracking-wider block mt-1">
                  {currentItem.daysRemaining === 1 ? 'Day Left' : 'Days Left'}
                </span>
              </div>

              <div className="space-y-1">
                <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#3E2431] bg-[#FFF0F3] border border-[#FDE2E7] px-2.5 py-0.5 rounded-full">
                  <span>{currentItem.badgeEmoji}</span> {currentItem.countLabel}
                </span>
                <h4 className="text-sm sm:text-base font-bold text-[#3E2431] font-heading">
                  {currentItem.label}
                </h4>
              </div>
            </div>

            {/* Middle/Right Box: Target Date Details & Quick Link */}
            <div className="sm:col-span-7 flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:border-l sm:border-[#FDE2E7] sm:pl-5">
              <div className="space-y-1 text-left">
                <div className="flex items-center gap-1.5 text-xs text-[#7A5C68] font-medium">
                  <Calendar className="w-3.5 h-3.5 text-[#F47D90]" />
                  <span>Target Date:</span>
                  <span className="font-bold text-[#3E2431]">
                    {formatDate(currentItem.targetDate)}
                  </span>
                </div>
                <p className="text-[11px] text-[#7A5C68]">
                  Start Date: <span className="font-semibold text-[#3E2431]">{formatDate(profile.startDate)}</span> ({daysTogether} days together)
                </p>
              </div>

              {/* Action: View Timeline or Edit Date */}
              <div className="flex items-center gap-2">
                {onNavigateToMemories && (
                  <button
                    onClick={onNavigateToMemories}
                    className="text-xs font-bold text-[#F47D90] hover:text-[#3E2431] bg-[#FFF0F3] hover:bg-[#FDE2E7] px-3 py-1.5 rounded-full border border-[#FDE2E7] transition-all cursor-pointer flex items-center gap-1 shadow-2xs whitespace-nowrap"
                  >
                    <span>Timeline</span>
                    <ChevronRight className="w-3 h-3" />
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Progress Bar Towards Anniversary */}
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between text-[11px] font-medium text-[#7A5C68]">
              <span>Cycle Progress</span>
              <span className="font-bold text-[#F47D90]">{progressPercent}% complete</span>
            </div>
            <div className="w-full bg-[#FFF0F3] h-2 rounded-full overflow-hidden border border-[#FDE2E7]/60">
              <div
                className="h-full bg-gradient-to-r from-[#F8A0AF] to-[#F47D90] rounded-full transition-all duration-500"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
