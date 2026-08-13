import React, { useState, useMemo } from 'react';
import { Category, CoupleProfile, Snap } from '../types';
import { calculateDaysTogether, calculateMilestones, formatDate, formatMonthYear } from '../utils/dateUtils';
import { CategoryIcon } from '../utils/categoryIcons';
import { Sparkles, Calendar, Award, Shuffle, Heart, MapPin } from 'lucide-react';
import { PolaroidCard } from './PolaroidCard';

interface MemoriesTimelineViewProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  onSelectSnap: (snap: Snap) => void;
  onToggleFavorite: (snapId: string, e: React.MouseEvent) => void;
}

export const MemoriesTimelineView: React.FC<MemoriesTimelineViewProps> = ({
  profile,
  categories,
  snaps,
  onSelectSnap,
  onToggleFavorite,
}) => {
  const daysTogether = calculateDaysTogether(profile.startDate);
  const milestones = calculateMilestones(profile.startDate);

  // Random Memory Flashback
  const [randomSnapIndex, setRandomSnapIndex] = useState<number>(0);
  const randomSnap = snaps.length > 0 ? snaps[randomSnapIndex % snaps.length] : null;

  const handleShuffleMemory = () => {
    if (snaps.length > 1) {
      const nextIdx = Math.floor(Math.random() * snaps.length);
      setRandomSnapIndex(nextIdx);
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

  return (
    <div className="pb-24 pt-4 px-4 sm:px-6 max-w-5xl mx-auto space-y-8">
      {/* HEADER */}
      <div className="text-center space-y-1">
        <div className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#FFDEE5] text-[#4A2C3A] text-xs font-bold">
          <Calendar className="w-3.5 h-3.5" /> Love Timeline
        </div>
        <h2 className="text-3xl font-bold font-heading text-[#4A2C3A]">Relationship Milestones</h2>
        <p className="text-xs text-[#7A5566]">Track your special days and relive past moments</p>
      </div>

      {/* RANDOM FLASHBACK MEMORY CARD */}
      {randomSnap && (
        <section className="glass-panel rounded-3xl p-6 border border-[#FFDEE5] shadow-tuchi relative overflow-hidden">
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
              <span className="absolute bottom-2 left-2 px-2 py-0.5 rounded-full bg-white/90 text-[10px] font-bold text-[#4A2C3A]">
                Click to view
              </span>
            </div>

            <div className="flex-1 space-y-3 text-center sm:text-left">
              <div className="flex items-center justify-center sm:justify-between gap-2">
                <span className="px-3 py-1 rounded-full bg-[#BFE3D0] text-[#4A2C3A] text-xs font-bold flex items-center gap-1">
                  <Sparkles className="w-3.5 h-3.5" /> Memory Flashback
                </span>
                <button
                  onClick={handleShuffleMemory}
                  className="hidden sm:flex items-center gap-1 text-xs font-bold text-[#E8AFC0] hover:text-[#4A2C3A] transition-colors cursor-pointer"
                >
                  <Shuffle className="w-3.5 h-3.5" /> Another Memory
                </button>
              </div>

              <h3 className="text-xl font-bold font-heading text-[#4A2C3A]">
                {randomSnap.caption || 'A sweet moment saved in time'}
              </h3>

              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-xs text-[#7A5566] font-medium">
                <span className="flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5 text-[#E8AFC0]" />
                  {formatDate(randomSnap.snapDate)}
                </span>
                {randomSnap.location && (
                  <span className="flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-[#E8AFC0]" />
                    {randomSnap.location}
                  </span>
                )}
              </div>

              <div className="pt-1 sm:hidden">
                <button
                  onClick={handleShuffleMemory}
                  className="py-2 px-4 rounded-full bg-white border border-[#FFDEE5] text-xs font-bold text-[#4A2C3A] inline-flex items-center gap-1 cursor-pointer"
                >
                  <Shuffle className="w-3.5 h-3.5" /> Shuffle Memory
                </button>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* MILESTONES PROGRESS TRACKER */}
      <section className="glass-panel rounded-3xl p-6 border border-[#FFDEE5] shadow-tuchi space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold font-heading text-[#4A2C3A] flex items-center gap-2">
            <Award className="w-5 h-5 text-[#D9A15E]" /> Milestones
          </h3>
          <span className="text-xs font-bold text-[#7A5566]">
            {milestones.filter((m) => m.reached).length} of {milestones.length} Unlocked
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {milestones.map((m, idx) => {
            const isCurrentNext = !m.reached && (idx === 0 || milestones[idx - 1]?.reached);
            const progress = Math.min(100, Math.round((daysTogether / m.days) * 100));

            return (
              <div
                key={m.days}
                className={`p-4 rounded-2xl border transition-all ${
                  m.reached
                    ? 'bg-white/90 border-[#BFE3D0] shadow-xs'
                    : isCurrentNext
                    ? 'bg-[#FFDEE5]/30 border-[#E8AFC0] shadow-xs ring-1 ring-[#E8AFC0]'
                    : 'bg-white/40 border-[#FFDEE5] opacity-70'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="w-8 h-8 rounded-full bg-[#FFDEE5] flex items-center justify-center text-[#4A2C3A]">
                    <CategoryIcon iconName={m.iconName} className="w-4 h-4" />
                  </div>
                  {m.reached ? (
                    <span className="px-2.5 py-0.5 rounded-full bg-[#BFE3D0] text-[#4A2C3A] text-[10px] font-bold">
                      Unlocked
                    </span>
                  ) : (
                    <span className="text-[10px] font-bold text-[#7A5566]">
                      {m.days - daysTogether} days left
                    </span>
                  )}
                </div>

                <h4 className="text-sm font-bold font-heading text-[#4A2C3A]">{m.label}</h4>
                <p className="text-[11px] text-[#7A5566] font-medium">Day {m.days}</p>

                {/* Progress bar */}
                <div className="w-full bg-[#FFF6F0] h-1.5 rounded-full overflow-hidden mt-2.5 border border-[#FFDEE5]">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      m.reached ? 'bg-[#BFE3D0]' : 'bg-[#E8AFC0]'
                    }`}
                    style={{ width: `${progress}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* CHRONOLOGICAL MEMORY TIMELINE */}
      <section className="space-y-6">
        <h3 className="text-xl font-bold font-heading text-[#4A2C3A] flex items-center gap-2">
          <Heart className="w-5 h-5 text-[#E8AFC0]" /> Memory History
        </h3>

        {Object.keys(snapsByMonth).length > 0 ? (
          Object.keys(snapsByMonth).map((monthYear) => {
            const monthSnaps = snapsByMonth[monthYear];
            return (
              <div key={monthYear} className="space-y-4">
                {/* Month Header */}
                <div className="sticky top-2 z-10 inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/90 backdrop-blur-md border border-[#FFDEE5] text-[#4A2C3A] text-xs font-bold shadow-xs">
                  <Calendar className="w-3.5 h-3.5 text-[#E8AFC0]" />
                  <span>{monthYear}</span>
                  <span className="text-[10px] text-[#7A5566]">({monthSnaps.length})</span>
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
          <div className="text-center py-10 glass-panel rounded-3xl p-6 border border-[#FFDEE5]">
            <p className="text-xs text-[#7A5566]">No memories saved yet. Start by adding your first snap!</p>
          </div>
        )}
      </section>
    </div>
  );
};
