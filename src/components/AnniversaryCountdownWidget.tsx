import React, { useState, useMemo } from 'react';
import { motion } from 'motion/react';
import confetti from 'canvas-confetti';
import { CoupleProfile } from '../types';
import { calculateUpcomingAnniversaries, formatDate, calculateDaysTogether } from '../utils/dateUtils';
import {
  Calendar,
  PartyPopper,
  Sparkles,
  ChevronRight,
  Flame,
  Star,
  Heart,
} from 'lucide-react';
import { GiftBox3D, Cake3D, Heart3D, Sparkles3D, PartyPopper3D } from './Romance3DIcons';

interface AnniversaryCountdownWidgetProps {
  profile: CoupleProfile;
  onNavigateToMemories?: () => void;
  onNavigateToUs?: () => void;
}

export const AnniversaryCountdownWidget: React.FC<AnniversaryCountdownWidgetProps> = ({
  profile,
  onNavigateToMemories,
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
      particleCount: 90,
      spread: 80,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#FFFFFF', '#FFE082', '#FF9FB0'],
    });
  };

  // Progress percentage calculation towards the next anniversary
  const progressPercent = useMemo(() => {
    if (selectedType === 'annual') {
      const remaining = Math.max(0, Math.min(365, annual.daysRemaining));
      return Math.round(((365 - remaining) / 365) * 100);
    } else {
      const remaining = Math.max(0, Math.min(30, monthly.daysRemaining));
      return Math.round(((30 - remaining) / 30) * 100);
    }
  }, [selectedType, annual.daysRemaining, monthly.daysRemaining]);

  const isApproaching = currentItem.daysRemaining <= 7 && currentItem.daysRemaining > 0;

  return (
    <section className="relative rounded-[32px] glass-panel border border-[#FDE2E7] shadow-tuchi p-5 sm:p-6 overflow-hidden glossy-top text-[#2D1F27] bg-white/95">
      {/* Decorative Glow */}
      <div className="absolute -top-6 -right-6 w-48 h-48 bg-[#FDE2E7]/70 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute -bottom-6 -left-6 w-48 h-48 bg-[#FFF0F3] rounded-full blur-3xl pointer-events-none -z-10" />

      {/* If today is the Anniversary, show joyful celebration banner */}
      {isAnyAnniversaryToday ? (
        <div className="relative z-10 space-y-4">
          <div className="flex items-center justify-between">
            <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#F47D90] text-white text-xs font-extrabold shadow-tuchi-glow">
              <PartyPopper className="w-3.5 h-3.5 animate-bounce" />
              <span>Today is the Day! 🎉</span>
            </div>
            <PartyPopper3D size={36} />
          </div>

          <div className="text-left space-y-1.5">
            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#2D1F27]">
              Happy {annual.isToday ? annual.label : monthly.label}! <span className="font-serif-italic font-bold text-[#F47D90]">💍</span>
            </h3>
            <p className="text-xs sm:text-sm text-[#6B5360] font-medium leading-relaxed">
              Celebrating another milestone with{' '}
              <span className="font-bold text-[#F47D90]">
                {profile.userName || 'You'} & {profile.partnerName || 'Partner'}
              </span>
              . Here is to endless beautiful memories together!
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <motion.button
              whileHover={{ scale: 1.03, y: -1 }}
              whileTap={{ scale: 0.97 }}
              onClick={triggerConfetti}
              className="py-2.5 px-5 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] text-white text-xs sm:text-sm font-extrabold shadow-tuchi-glow flex items-center gap-2 cursor-pointer transition-all"
            >
              <Sparkles className="w-4 h-4 fill-current" />
              <span>Celebrate with Confetti! 🎊</span>
            </motion.button>

            {onNavigateToMemories && (
              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={onNavigateToMemories}
                className="py-2.5 px-4 rounded-2xl bg-[#FFF0F3] hover:bg-[#FDE2E7] text-[#2D1F27] border border-[#FDE2E7] text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-xs"
              >
                <span>View Love Timeline</span>
                <ChevronRight className="w-3.5 h-3.5 text-[#F47D90]" />
              </motion.button>
            )}
          </div>
        </div>
      ) : (
        /* Normal Countdown State */
        <div className="relative z-10 space-y-4">
          {/* Top Row: Title, Subtitle, & Annual/Monthly Toggle Pills */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <motion.div
                whileHover={{ scale: 1.08, rotate: [0, -5, 5, 0] }}
                transition={{ duration: 0.3 }}
                className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs flex-shrink-0"
              >
                {selectedType === 'annual' ? (
                  <GiftBox3D size={32} />
                ) : (
                  <Cake3D size={32} />
                )}
              </motion.div>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-base sm:text-lg font-bold font-heading text-[#2D1F27]">
                    Anniversary <span className="font-serif-italic font-bold text-[#F47D90]">Countdown</span>
                  </h3>
                  {isApproaching && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#F47D90] text-white text-[10px] font-extrabold animate-pulse shadow-tuchi-glow">
                      <Flame className="w-3 h-3 fill-current" /> Soon!
                    </span>
                  )}
                </div>
                <p className="text-xs text-[#6B5360] font-medium">
                  Counting down every special milestone together
                </p>
              </div>
            </div>

            {/* Toggle Pills: Annual vs Monthly */}
            <div className="flex items-center gap-1 p-1 bg-[#FFF0F3] border border-[#FDE2E7] rounded-2xl shadow-xs self-start sm:self-auto">
              <button
                type="button"
                onClick={() => setSelectedType('annual')}
                className={`relative px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  selectedType === 'annual'
                    ? 'text-white'
                    : 'text-[#6B5360] hover:text-[#2D1F27]'
                }`}
              >
                {selectedType === 'annual' && (
                  <motion.div
                    layoutId="activeAnnivPill"
                    transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                    className="absolute inset-0 bg-[#F47D90] rounded-xl shadow-tuchi-glow -z-10"
                  />
                )}
                <Star className="w-3.5 h-3.5" />
                <span>Annual ({annual.daysRemaining}d)</span>
              </button>

              <button
                type="button"
                onClick={() => setSelectedType('monthly')}
                className={`relative px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  selectedType === 'monthly'
                    ? 'text-white'
                    : 'text-[#6B5360] hover:text-[#2D1F27]'
                }`}
              >
                {selectedType === 'monthly' && (
                  <motion.div
                    layoutId="activeAnnivPill"
                    transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                    className="absolute inset-0 bg-[#F47D90] rounded-xl shadow-tuchi-glow -z-10"
                  />
                )}
                <Heart className="w-3.5 h-3.5" />
                <span>Monthly ({monthly.daysRemaining}d)</span>
              </button>
            </div>
          </div>

          {/* Main Countdown Display Card */}
          <div className="bg-[#FFF7F9] border border-[#FDE2E7] rounded-3xl p-4 sm:p-5 shadow-xs grid grid-cols-1 sm:grid-cols-12 gap-4 items-center">
            {/* Left Box: Big Countdown Number & Label */}
            <div className="sm:col-span-5 flex items-center gap-3.5">
              <div className="bg-white border border-[#FDE2E7] rounded-2xl px-4 py-3 text-center shadow-xs flex-shrink-0 min-w-[88px]">
                <span className="text-3xl sm:text-4xl font-extrabold font-heading text-[#F47D90] leading-none block">
                  {currentItem.daysRemaining}
                </span>
                <span className="text-[10px] font-extrabold uppercase text-[#9E8592] tracking-wider block mt-1">
                  {currentItem.daysRemaining === 1 ? 'Day Left' : 'Days Left'}
                </span>
              </div>

              <div className="space-y-1">
                <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#F47D90] bg-[#FFF0F3] border border-[#FDE2E7] px-2.5 py-0.5 rounded-full">
                  <span>{currentItem.badgeEmoji}</span> {currentItem.countLabel}
                </span>
                <h4 className="text-sm sm:text-base font-bold text-[#2D1F27] font-heading">
                  {currentItem.label}
                </h4>
              </div>
            </div>

            {/* Middle/Right Box: Target Date Details & Quick Link */}
            <div className="sm:col-span-7 flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:border-l sm:border-[#FDE2E7] sm:pl-5">
              <div className="space-y-1 text-left">
                <div className="flex items-center gap-1.5 text-xs text-[#6B5360] font-medium">
                  <Calendar className="w-3.5 h-3.5 text-[#F47D90]" />
                  <span>Target Date:</span>
                  <span className="font-bold text-[#2D1F27]">
                    {formatDate(currentItem.targetDate)}
                  </span>
                </div>
                <div className="flex items-center gap-1 text-[11px] text-[#6B5360]">
                  <Heart3D size={14} />
                  <span>Start: <strong className="text-[#2D1F27]">{formatDate(profile.startDate)}</strong> ({daysTogether} days together)</span>
                </div>
              </div>

              {/* Action: View Timeline */}
              <div className="flex items-center gap-2">
                {onNavigateToMemories && (
                  <motion.button
                    whileHover={{ scale: 1.03 }}
                    whileTap={{ scale: 0.97 }}
                    onClick={onNavigateToMemories}
                    className="text-xs font-bold text-[#2D1F27] hover:text-[#F47D90] bg-white hover:bg-[#FFF0F3] px-3.5 py-2 rounded-2xl border border-[#FDE2E7] transition-all cursor-pointer flex items-center gap-1 shadow-xs whitespace-nowrap"
                  >
                    <span>Timeline</span>
                    <ChevronRight className="w-3 h-3 text-[#F47D90]" />
                  </motion.button>
                )}
              </div>
            </div>
          </div>

          {/* Progress Bar Towards Anniversary */}
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between text-[11px] font-bold text-[#6B5360]">
              <span>Milestone Progress</span>
              <span className="text-[#F47D90] font-extrabold">{progressPercent}% complete</span>
            </div>
            <div className="w-full bg-[#FFF0F3] h-2.5 rounded-full overflow-hidden p-0.5 border border-[#FDE2E7]">
              <motion.div
                className="h-full bg-gradient-to-r from-[#FFA6B8] via-[#F47D90] to-[#EE6A80] rounded-full shadow-tuchi-glow"
                initial={false}
                animate={{ width: `${progressPercent}%` }}
                transition={{ duration: 0.5, ease: 'easeOut' }}
              />
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
