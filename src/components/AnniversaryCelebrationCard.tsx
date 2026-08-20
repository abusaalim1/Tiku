import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { CoupleProfile, UpcomingAnniversarySummary } from '../types';
import { formatDate, calculateDaysTogether } from '../utils/dateUtils';
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
  CheckCircle2,
  Lock,
  X,
} from 'lucide-react';
import {
  Trophy3D,
  PartyPopper3D,
  Crown3D,
  LockedHeart3D,
  CoupleHearts3D,
  GiftBox3D,
  Cake3D,
  Heart3D,
  Sparkles3D,
} from './Romance3DIcons';

interface MilestoneItem {
  days: number;
  label: string;
  badge: string;
  description: string;
  icon: 'heart' | 'cake' | 'gift' | 'trophy' | 'crown';
}

const MILESTONES_LIST: MilestoneItem[] = [
  { days: 30, label: '1 Month', badge: '🌱 First Spark', description: 'One month of sweet memories together.', icon: 'heart' },
  { days: 50, label: '50 Days', badge: '✨ Golden Blossom', description: '50 days of building our private world.', icon: 'heart' },
  { days: 100, label: '100 Days', badge: '💯 Century of Love', description: 'A timeless milestone of true companionship.', icon: 'trophy' },
  { days: 180, label: 'Half Year (6M)', badge: '🎀 Sweet Harmony', description: 'Half a year of adventures, laughs, and kisses.', icon: 'gift' },
  { days: 365, label: '1 Full Year (365d)', badge: '💍 1st Anniversary', description: '365 days of unconditional love and joy.', icon: 'crown' },
  { days: 500, label: '500 Days', badge: '⭐ 500 Days of Magic', description: 'Over a year and a half deeply in love.', icon: 'trophy' },
  { days: 730, label: '2 Years (730d)', badge: '👑 Royal Bond', description: 'Two extraordinary years side by side.', icon: 'crown' },
  { days: 1000, label: '1,000 Days', badge: '💎 Millennium Love', description: 'A grand thousand-day chapter of romance.', icon: 'trophy' },
  { days: 1095, label: '3 Years (1095d)', badge: '🕊️ Everlasting Journey', description: 'Three years of cherished couple moments.', icon: 'crown' },
];

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
  const [activeTab, setActiveTab] = useState<'monthly' | 'annual' | 'milestones'>('milestones');
  const [celebratingMilestone, setCelebratingMilestone] = useState<MilestoneItem | null>(null);

  const { monthly, annual, nextClosest, isAnyAnniversaryToday } = anniversaries;
  const daysTogether = calculateDaysTogether(profile.startDate);

  const triggerConfetti = () => {
    confetti({
      particleCount: 100,
      spread: 90,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#FFFFFF', '#FFE082', '#FF9FB0'],
    });
  };

  const handleMilestoneClick = (item: MilestoneItem, isUnlocked: boolean) => {
    if (isUnlocked) {
      setCelebratingMilestone(item);
      triggerConfetti();
    }
  };

  // If today is any anniversary, automatically display celebratory banner
  if (isAnyAnniversaryToday) {
    const todayItem = annual.isToday ? annual : monthly;

    return (
      <motion.section
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="relative overflow-hidden rounded-[32px] p-6 sm:p-7 bg-gradient-to-r from-[#F47D90] via-[#EE6A80] to-[#E05370] text-white shadow-tuchi-glow border-2 border-white/80"
      >
        <div className="absolute top-2 right-4 text-white/20 text-4xl select-none animate-pulse">
          ✨
        </div>
        <div className="absolute bottom-2 left-4 text-white/20 text-3xl select-none">
          💖
        </div>

        <div className="relative z-10 flex flex-col sm:flex-row items-center justify-between gap-5 text-center sm:text-left">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-white/25 backdrop-blur-md text-white text-xs font-extrabold uppercase tracking-wider border border-white/30 shadow-xs">
              <PartyPopper className="w-4 h-4 text-yellow-200 animate-bounce" />
              <span>Happy Anniversary Today! 🎉</span>
            </div>

            <h3 className="text-2xl sm:text-3xl font-extrabold font-heading tracking-tight text-white drop-shadow-sm">
              Today is our {todayItem.countLabel} Anniversary! {todayItem.badgeEmoji}
            </h3>

            <p className="text-xs sm:text-sm text-white/95 font-medium max-w-lg leading-relaxed">
              Happy {todayItem.label} to {profile.userName || 'You'} & {profile.partnerName || 'Partner'}! Celebrating another chapter of love and memories together.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row items-center gap-3 flex-shrink-0">
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={triggerConfetti}
              className="py-3 px-5 rounded-2xl bg-white text-[#F47D90] hover:bg-[#FFF0F3] text-xs sm:text-sm font-extrabold shadow-lg flex items-center gap-2 cursor-pointer transition-all border border-white"
            >
              <Sparkles className="w-4 h-4 fill-current" />
              <span>Celebrate! 🎊</span>
            </motion.button>

            {onNavigateToMemories && (
              <button
                onClick={onNavigateToMemories}
                className="py-2.5 px-4 rounded-2xl bg-white/20 hover:bg-white/30 text-white text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer backdrop-blur-md border border-white/20"
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
    <section className="glass-panel rounded-[32px] p-5 sm:p-6 border border-[#FDE2E7] shadow-tuchi relative overflow-hidden glossy-top space-y-4 text-[#2D1F27] bg-white/95">
      {/* Top Header & Tab Toggle */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs flex-shrink-0">
            <Trophy3D size={32} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base font-extrabold font-heading text-[#2D1F27]">
                Milestone <span className="font-serif-italic font-bold text-[#F47D90]">Timeline</span>
              </h3>
              {isUrgent && (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#F47D90] text-white text-[10px] font-extrabold animate-pulse shadow-xs">
                  <Flame className="w-3 h-3 fill-current" /> Approaching!
                </span>
              )}
            </div>
            <p className="text-xs text-[#6B5360] font-medium">
              We have been together for <span className="font-extrabold text-[#F47D90]">{daysTogether} days</span>
            </p>
          </div>
        </div>

        {/* Tab Pills */}
        <div className="flex items-center gap-1 p-1 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] shadow-xs self-stretch sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveTab('milestones')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'milestones'
                ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            <Award className="w-3.5 h-3.5" />
            <span>Milestones</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('monthly')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'monthly'
                ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            <Heart className="w-3.5 h-3.5" />
            <span>Monthly ({monthly.daysRemaining}d)</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('annual')}
            className={`flex-1 sm:flex-none px-3 py-1.5 rounded-xl text-xs font-extrabold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeTab === 'annual'
                ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            <Gift className="w-3.5 h-3.5" />
            <span>Annual ({annual.daysRemaining}d)</span>
          </button>
        </div>
      </div>

      {/* Tab Content */}
      <AnimatePresence mode="wait">
        {activeTab === 'milestones' ? (
          <motion.div
            key="milestones-tab"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.2 }}
            className="space-y-2.5 pt-1"
          >
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5">
              {MILESTONES_LIST.map((m) => {
                const isUnlocked = daysTogether >= m.days;
                const daysRemainingForMilestone = Math.max(0, m.days - daysTogether);

                return (
                  <motion.div
                    key={m.days}
                    whileHover={isUnlocked ? { scale: 1.02, y: -2 } : {}}
                    whileTap={isUnlocked ? { scale: 0.98 } : {}}
                    onClick={() => handleMilestoneClick(m, isUnlocked)}
                    className={`p-3.5 rounded-2xl border transition-all cursor-pointer relative overflow-hidden ${
                      isUnlocked
                        ? 'bg-gradient-to-br from-white via-[#FFF7F9] to-[#FFF0F3] border-[#FDE2E7] shadow-xs hover:border-[#F47D90]'
                        : 'bg-[#FAF5F0]/50 border-neutral-200/60 opacity-65'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <div className="w-9 h-9 rounded-xl bg-white border border-[#FDE2E7] flex items-center justify-center shadow-xs flex-shrink-0">
                          {isUnlocked ? (
                            m.icon === 'crown' ? (
                              <Crown3D size={24} />
                            ) : m.icon === 'trophy' ? (
                              <Trophy3D size={24} />
                            ) : (
                              <Heart3D size={22} />
                            )
                          ) : (
                            <LockedHeart3D size={22} />
                          )}
                        </div>
                        <div>
                          <div className="text-xs font-bold text-[#2D1F27] leading-tight">
                            {m.label}
                          </div>
                          <div className="text-[10px] text-[#F47D90] font-extrabold">
                            {m.badge}
                          </div>
                        </div>
                      </div>

                      {isUnlocked ? (
                        <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold flex items-center gap-0.5">
                          <CheckCircle2 className="w-2.5 h-2.5" /> Unlocked
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-500 text-[10px] font-bold flex items-center gap-0.5">
                          <Lock className="w-2.5 h-2.5" /> in {daysRemainingForMilestone}d
                        </span>
                      )}
                    </div>
                  </motion.div>
                );
              })}
            </div>
          </motion.div>
        ) : (
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.2 }}
            className="p-4 sm:p-5 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] flex flex-col sm:flex-row items-center justify-between gap-4 shadow-xs"
          >
            {/* Left info */}
            <div className="space-y-1.5 text-center sm:text-left w-full sm:w-auto">
              <div className="flex items-center justify-center sm:justify-start gap-2">
                <span className="text-xs font-bold text-[#F47D90] uppercase tracking-wider">
                  {activeTab === 'monthly' ? 'Next Monthly Milestone' : 'Next Yearly Anniversary'}
                </span>
                <span className="text-xs font-bold px-2.5 py-0.5 rounded-full bg-[#FFF0F3] text-[#F47D90] border border-[#FDE2E7]">
                  {activeTab === 'monthly' ? monthly.badgeEmoji : annual.badgeEmoji} {activeTab === 'monthly' ? monthly.countLabel : annual.countLabel}
                </span>
              </div>

              <h4 className="text-lg sm:text-xl font-bold font-heading text-[#2D1F27]">
                {activeTab === 'monthly' ? monthly.label : annual.label}
              </h4>

              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-xs text-[#6B5360]">
                <span className="flex items-center gap-1 font-semibold">
                  <Calendar className="w-3.5 h-3.5 text-[#F47D90]" />
                  {formatDate(activeTab === 'monthly' ? monthly.targetDate : annual.targetDate)}
                </span>
                <span className="flex items-center gap-1 font-semibold text-[#F47D90]">
                  <Clock className="w-3.5 h-3.5 text-[#F47D90]" />
                  {(activeTab === 'monthly' ? monthly.daysRemaining : annual.daysRemaining)} {((activeTab === 'monthly' ? monthly.daysRemaining : annual.daysRemaining) === 1 ? 'day left' : 'days left')}
                </span>
              </div>
            </div>

            {/* Right Countdown Number & Confetti Sparkle Action */}
            <div className="flex items-center gap-3 w-full sm:w-auto justify-center sm:justify-end border-t sm:border-t-0 border-[#FDE2E7] pt-3 sm:pt-0">
              <div className="text-center px-4 py-2.5 rounded-2xl bg-white border border-[#FDE2E7] min-w-[90px] shadow-xs">
                <div className="text-2xl sm:text-3xl font-extrabold font-heading text-[#F47D90]">
                  {activeTab === 'monthly' ? monthly.daysRemaining : annual.daysRemaining}
                </div>
                <span className="text-[10px] font-extrabold uppercase text-[#6B5360]">
                  {(activeTab === 'monthly' ? monthly.daysRemaining : annual.daysRemaining) === 1 ? 'Day to go' : 'Days to go'}
                </span>
              </div>

              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={triggerConfetti}
                title="Pop celebration confetti"
                className="py-3 px-4 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-extrabold flex items-center gap-1.5 shadow-tuchi-glow cursor-pointer transition-all"
              >
                <Sparkles className="w-4 h-4 fill-current" />
                <span>Celebrate</span>
              </motion.button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* FULL SCREEN CELEBRATION MODAL ON MILESTONE TAP */}
      <AnimatePresence>
        {celebratingMilestone && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.85, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.85, y: 20 }}
              className="w-full max-w-sm glass-modal rounded-[36px] p-6 border border-[#FDE2E7] shadow-[0_24px_60px_rgba(244,125,144,0.25)] text-center space-y-4 relative bg-white"
            >
              <button
                onClick={() => setCelebratingMilestone(null)}
                className="absolute top-4 right-4 p-2 rounded-full bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>

              <div className="w-20 h-20 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-tuchi-glow">
                <PartyPopper3D size={48} />
              </div>

              <div className="space-y-1">
                <span className="text-xs font-extrabold text-[#F47D90] uppercase tracking-wider">
                  Milestone Unlocked! 🎉
                </span>
                <h3 className="text-2xl font-extrabold font-heading text-[#2D1F27]">
                  {celebratingMilestone.label}
                </h3>
                <p className="text-xs font-bold text-[#F47D90]">
                  {celebratingMilestone.badge}
                </p>
                <p className="text-xs text-[#6B5360] pt-1 leading-relaxed">
                  {celebratingMilestone.description}
                </p>
              </div>

              <div className="p-3 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] text-xs text-[#2D1F27] font-semibold">
                Together for <strong>{daysTogether} days</strong> and counting! 💖
              </div>

              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={() => {
                  triggerConfetti();
                  setCelebratingMilestone(null);
                }}
                className="w-full py-3 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] text-white font-extrabold text-xs shadow-tuchi-glow cursor-pointer"
              >
                Yay! Keep Growing Love ✨
              </motion.button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </section>
  );
};
