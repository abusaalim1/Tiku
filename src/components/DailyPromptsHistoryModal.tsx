import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { CoupleProfile, DailyPromptEntry, PromptCategory } from '../types';
import { PROMPT_CATEGORIES_METADATA } from '../data/dailyPromptsData';
import { formatDate } from '../utils/dateUtils';
import {
  X,
  Calendar,
  Heart,
  Camera,
  Search,
  Trash2,
  Flame,
  CheckCircle2,
} from 'lucide-react';
import { BookJournal3D, Category3DIcon, Sparkles3D } from './Romance3DIcons';

interface DailyPromptsHistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  entries: DailyPromptEntry[];
  profile: CoupleProfile;
  streakInfo: { streak: number; completedToday: boolean; totalAnswered: number };
  onConvertToSnap?: (entry: DailyPromptEntry) => void;
  onDeleteEntry?: (entryId: string) => void;
}

export const DailyPromptsHistoryModal: React.FC<DailyPromptsHistoryModalProps> = ({
  isOpen,
  onClose,
  entries,
  profile,
  streakInfo,
  onConvertToSnap,
  onDeleteEntry,
}) => {
  const [activeCategory, setActiveCategory] = useState<PromptCategory | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState('');

  if (!isOpen) return null;

  const filteredEntries = entries.filter((entry) => {
    if (activeCategory !== 'all' && entry.category !== activeCategory) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchQ = entry.question.toLowerCase().includes(q);
      const matchUser = entry.userAnswer?.toLowerCase().includes(q);
      const matchPartner = entry.partnerAnswer?.toLowerCase().includes(q);
      const matchJoint = entry.jointAnswer?.toLowerCase().includes(q);
      return matchQ || matchUser || matchPartner || matchJoint;
    }
    return true;
  });

  const categoryKeys = Object.keys(PROMPT_CATEGORIES_METADATA) as PromptCategory[];

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          transition={{ duration: 0.25 }}
          className="w-full max-w-3xl glass-modal rounded-[36px] shadow-[0_24px_60px_rgba(244,125,144,0.18)] border border-[#FDE2E7] overflow-hidden flex flex-col max-h-[88vh] text-[#2D1F27] bg-white"
        >
          {/* HEADER */}
          <div className="p-5 sm:p-6 border-b border-[#FDE2E7] flex items-center justify-between bg-[#FFF7F9]">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs flex-shrink-0">
                <BookJournal3D size={32} />
              </div>
              <div>
                <h3 className="text-lg sm:text-xl font-bold font-heading text-[#2D1F27]">
                  Couple Prompt <span className="font-serif-italic font-bold text-[#F47D90]">Journal</span>
                </h3>
                <p className="text-xs text-[#6B5360]">
                  Your archive of answered questions, reflections & micro-dates
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] flex items-center justify-center transition-colors shadow-xs border border-[#FDE2E7] cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* STREAK & SUMMARY STATS BAR */}
          <div className="px-5 sm:px-6 py-3 bg-white border-b border-[#FDE2E7] flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-2.5">
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 text-amber-700 font-extrabold border border-amber-200 shadow-xs">
                <Flame className="w-4 h-4 text-amber-500 fill-amber-500" />
                <span>{streakInfo.streak} Day Streak</span>
              </div>
              <div className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 font-extrabold border border-emerald-200 shadow-xs">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                <span>{entries.length} Prompts Answered</span>
              </div>
            </div>

            {/* SEARCH */}
            <div className="relative w-full sm:w-60">
              <Search className="w-3.5 h-3.5 text-[#F47D90] absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search past answers..."
                className="w-full pl-9 pr-3 py-2 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-xs text-[#2D1F27] placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90] shadow-xs"
              />
            </div>
          </div>

          {/* CATEGORY FILTER CHIPS */}
          <div className="px-5 sm:px-6 py-2.5 flex items-center gap-1.5 overflow-x-auto no-scrollbar border-b border-[#FDE2E7] bg-[#FFF7F9]">
            <button
              type="button"
              onClick={() => setActiveCategory('all')}
              className={`px-3.5 py-1.5 rounded-2xl text-xs font-extrabold flex-shrink-0 transition-all cursor-pointer ${
                activeCategory === 'all'
                  ? 'bg-[#F47D90] text-white shadow-tuchi-glow'
                  : 'bg-white text-[#6B5360] hover:text-[#2D1F27] border border-[#FDE2E7]'
              }`}
            >
              All ({entries.length})
            </button>
            {categoryKeys.map((catKey) => {
              const meta = PROMPT_CATEGORIES_METADATA[catKey];
              const count = entries.filter((e) => e.category === catKey).length;
              const isSelected = activeCategory === catKey;
              return (
                <button
                  key={catKey}
                  type="button"
                  onClick={() => setActiveCategory(catKey)}
                  className={`px-3 py-1.5 rounded-2xl text-xs font-bold flex items-center gap-1.5 flex-shrink-0 transition-all cursor-pointer border ${
                    isSelected
                      ? 'bg-[#F47D90] text-white border-[#F47D90] shadow-tuchi-glow'
                      : 'bg-white text-[#6B5360] border-[#FDE2E7] hover:text-[#2D1F27]'
                  }`}
                >
                  <Category3DIcon categoryName={catKey} size={16} />
                  <span>{meta.label}</span>
                  <span className="opacity-70 text-[10px]">({count})</span>
                </button>
              );
            })}
          </div>

          {/* ENTRIES LIST */}
          <div className="flex-1 overflow-y-auto p-5 sm:p-6 space-y-4">
            {filteredEntries.length > 0 ? (
              filteredEntries.map((entry) => {
                const meta = PROMPT_CATEGORIES_METADATA[entry.category] || PROMPT_CATEGORIES_METADATA.romantic;

                return (
                  <motion.div
                    key={entry.id}
                    layout
                    className="p-5 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-3.5 relative overflow-hidden shadow-xs"
                  >
                    {/* Header */}
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="px-3 py-1 rounded-full text-[11px] font-extrabold border bg-white border-[#FDE2E7] text-[#F47D90] flex items-center gap-1.5 shadow-xs">
                          <Category3DIcon categoryName={entry.category} size={16} />
                          <span>{entry.type === 'activity' ? 'Date Activity' : meta.label}</span>
                        </span>

                        <span className="text-xs font-bold text-[#6B5360] flex items-center gap-1">
                          <Calendar className="w-3.5 h-3.5 text-[#F47D90]" />
                          {formatDate(entry.date)}
                        </span>
                      </div>

                      {/* Actions */}
                      <div className="flex items-center gap-2">
                        {onConvertToSnap && (
                          <button
                            type="button"
                            onClick={() => onConvertToSnap(entry)}
                            className="px-3 py-1 rounded-xl bg-white border border-[#FDE2E7] hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-bold flex items-center gap-1 shadow-xs cursor-pointer transition-colors"
                          >
                            <Camera className="w-3 h-3" />
                            <span>Save as Polaroid</span>
                          </button>
                        )}

                        {onDeleteEntry && (
                          <button
                            type="button"
                            onClick={() => {
                              if (confirm('Delete this prompt memory from your journal?')) {
                                onDeleteEntry(entry.id);
                              }
                            }}
                            className="p-1.5 rounded-full text-[#6B5360] hover:text-red-500 hover:bg-red-50 transition-colors cursor-pointer"
                            title="Delete prompt memory"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Question */}
                    <h4 className="text-sm sm:text-base font-bold text-[#2D1F27] leading-snug">
                      "{entry.question}"
                    </h4>

                    {/* Photo if attached */}
                    {entry.imageUrl && (
                      <div className="max-w-xs rounded-2xl overflow-hidden shadow-xs border border-[#FDE2E7]">
                        <img
                          src={entry.imageUrl}
                          alt="Prompt memory photo"
                          className="w-full h-44 object-cover"
                        />
                      </div>
                    )}

                    {/* Answers Grid */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                      {/* User Answer */}
                      {entry.userAnswer && (
                        <div className="p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-xs space-y-1">
                          <div className="flex items-center gap-2 text-xs font-bold text-[#F47D90]">
                            <div className="w-5 h-5 rounded-full bg-[#FFF0F3] overflow-hidden flex items-center justify-center text-[10px] border border-[#FDE2E7] text-[#2D1F27]">
                              {profile.userPhotoUrl ? (
                                <img
                                  src={profile.userPhotoUrl}
                                  alt="You"
                                  className="w-full h-full object-cover"
                                />
                              ) : (
                                (profile.userName || 'You')[0].toUpperCase()
                              )}
                            </div>
                            <span>{profile.userName || 'You'}'s Answer:</span>
                          </div>
                          <p className="text-xs text-[#2D1F27] font-medium leading-relaxed italic font-serif-italic">
                            "{entry.userAnswer}"
                          </p>
                        </div>
                      )}

                      {/* Partner Answer */}
                      {entry.partnerAnswer && (
                        <div className="p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-xs space-y-1">
                          <div className="flex items-center gap-2 text-xs font-bold text-[#F47D90]">
                            <div className="w-5 h-5 rounded-full bg-[#FFF0F3] overflow-hidden flex items-center justify-center text-[10px] border border-[#FDE2E7] text-[#2D1F27]">
                              {profile.partnerPhotoUrl ? (
                                <img
                                  src={profile.partnerPhotoUrl}
                                  alt="Partner"
                                  className="w-full h-full object-cover"
                                />
                              ) : (
                                (profile.partnerName || 'Partner')[0].toUpperCase()
                              )}
                            </div>
                            <span>{profile.partnerName || 'Partner'}'s Answer:</span>
                          </div>
                          <p className="text-xs text-[#2D1F27] font-medium leading-relaxed italic font-serif-italic">
                            "{entry.partnerAnswer}"
                          </p>
                        </div>
                      )}

                      {/* Joint Answer */}
                      {entry.jointAnswer && (
                        <div className="sm:col-span-2 p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-xs space-y-1">
                          <div className="flex items-center gap-1.5 text-xs font-bold text-[#F47D90]">
                            <Heart className="w-3.5 h-3.5 fill-[#F47D90]" />
                            <span>Our Joint Reflection:</span>
                          </div>
                          <p className="text-xs text-[#2D1F27] font-medium leading-relaxed italic font-serif-italic">
                            "{entry.jointAnswer}"
                          </p>
                        </div>
                      )}
                    </div>
                  </motion.div>
                );
              })
            ) : (
              <div className="text-center py-16 space-y-3">
                <div className="w-16 h-16 rounded-3xl bg-[#FFF0F3] text-[#F47D90] flex items-center justify-center mx-auto border border-[#FDE2E7] shadow-xs">
                  <BookJournal3D size={36} />
                </div>
                <h4 className="text-base font-bold font-heading text-[#2D1F27]">
                  No prompt memories found
                </h4>
                <p className="text-xs text-[#6B5360] max-w-xs mx-auto">
                  Answer today’s Daily Prompt on the Home page to start building your romantic journal!
                </p>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
