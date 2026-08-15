import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { CoupleProfile, DailyPromptEntry, PromptCategory } from '../types';
import { PROMPT_CATEGORIES_METADATA } from '../data/dailyPromptsData';
import { formatDate } from '../utils/dateUtils';
import {
  X,
  BookOpen,
  Calendar,
  Heart,
  Camera,
  Search,
  Trash2,
  ImagePlus,
  Sparkles,
  Flame,
  CheckCircle2,
  MessageSquareHeart,
} from 'lucide-react';

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
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          transition={{ duration: 0.25 }}
          className="w-full max-w-3xl bg-white rounded-[32px] shadow-tuchi-lg border border-white/90 overflow-hidden flex flex-col max-h-[88vh]"
        >
          {/* HEADER */}
          <div className="p-5 sm:p-6 border-b border-[#FDE2E7] flex items-center justify-between bg-white">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-[#F47D90] text-white flex items-center justify-center shadow-xs">
                <BookOpen className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-lg sm:text-xl font-bold font-heading text-[#3E2431]">
                  Couple Prompt Journal
                </h3>
                <p className="text-xs text-[#7A5C68]">
                  Your archive of answered questions, reflections & micro-dates
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#3E2431] flex items-center justify-center transition-colors shadow-2xs border border-[#FDE2E7] cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* STREAK & SUMMARY STATS BAR */}
          <div className="px-5 sm:px-6 py-3 bg-[#FFF7F9] border-b border-[#FDE2E7] flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-3">
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-orange-100 text-orange-800 font-extrabold border border-orange-200">
                <Flame className="w-4 h-4 text-orange-500 fill-orange-500" />
                <span>{streakInfo.streak} Day Streak</span>
              </div>
              <div className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#E8F8F0] text-[#1E7250] font-extrabold border border-[#BEE9D5]">
                <CheckCircle2 className="w-3.5 h-3.5 text-[#2A9D6F]" />
                <span>{entries.length} Total Prompts Answered</span>
              </div>
            </div>

            {/* SEARCH */}
            <div className="relative w-full sm:w-60">
              <Search className="w-3.5 h-3.5 text-[#F47D90] absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search past answers..."
                className="w-full pl-8 pr-3 py-1.5 rounded-xl bg-white border border-[#FDE2E7] text-xs text-[#3E2431] focus:outline-none focus:border-[#F47D90]"
              />
            </div>
          </div>

          {/* CATEGORY FILTER CHIPS */}
          <div className="px-5 sm:px-6 py-2.5 flex items-center gap-1.5 overflow-x-auto no-scrollbar border-b border-[#FDE2E7] bg-white">
            <button
              type="button"
              onClick={() => setActiveCategory('all')}
              className={`px-3 py-1 rounded-full text-xs font-bold flex-shrink-0 transition-all cursor-pointer ${
                activeCategory === 'all'
                  ? 'bg-[#3E2431] text-white shadow-xs'
                  : 'bg-[#FFF0F3] text-[#7A5C68] hover:bg-[#FDE2E7]'
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
                  className={`px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1 flex-shrink-0 transition-all cursor-pointer border ${
                    isSelected
                      ? 'bg-[#F47D90] text-white border-[#F47D90] shadow-xs'
                      : 'bg-white text-[#7A5C68] border-[#FDE2E7] hover:bg-[#FFF0F3]'
                  }`}
                >
                  <span>{meta.emoji}</span>
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
                    className="p-5 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-3.5 relative overflow-hidden"
                  >
                    {/* Header */}
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span
                          className="px-2.5 py-0.5 rounded-full text-[11px] font-extrabold border flex items-center gap-1"
                          style={{
                            backgroundColor: meta.bgColor,
                            color: meta.color,
                            borderColor: meta.borderColor,
                          }}
                        >
                          <span>{meta.emoji}</span>
                          <span>{entry.type === 'activity' ? 'Date Activity' : meta.label}</span>
                        </span>

                        <span className="text-xs font-bold text-[#7A5C68] flex items-center gap-1">
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
                            className="px-3 py-1 rounded-full bg-white border border-[#FDE2E7] hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-bold flex items-center gap-1 shadow-2xs cursor-pointer transition-colors"
                          >
                            <Camera className="w-3 h-3 text-[#F47D90]" />
                            <span>Save as Polaroid Snap</span>
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
                            className="p-1.5 rounded-full text-neutral-400 hover:text-red-500 hover:bg-red-50 transition-colors cursor-pointer"
                            title="Delete prompt memory"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Question */}
                    <h4 className="text-sm sm:text-base font-bold text-[#3E2431] leading-snug">
                      "{entry.question}"
                    </h4>

                    {/* Photo if attached */}
                    {entry.imageUrl && (
                      <div className="max-w-xs rounded-2xl overflow-hidden shadow-xs border border-white">
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
                        <div className="p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-2xs space-y-1">
                          <div className="flex items-center gap-2 text-xs font-bold text-[#F47D90]">
                            <div className="w-5 h-5 rounded-full bg-[#FFF0F3] overflow-hidden flex items-center justify-center text-[10px] border border-[#FDE2E7]">
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
                          <p className="text-xs text-[#3E2431] font-medium leading-relaxed italic">
                            "{entry.userAnswer}"
                          </p>
                        </div>
                      )}

                      {/* Partner Answer */}
                      {entry.partnerAnswer && (
                        <div className="p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-2xs space-y-1">
                          <div className="flex items-center gap-2 text-xs font-bold text-[#3E2431]">
                            <div className="w-5 h-5 rounded-full bg-[#FDE2E7] overflow-hidden flex items-center justify-center text-[10px] border border-[#FDE2E7]">
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
                          <p className="text-xs text-[#3E2431] font-medium leading-relaxed italic">
                            "{entry.partnerAnswer}"
                          </p>
                        </div>
                      )}

                      {/* Joint Answer */}
                      {entry.jointAnswer && (
                        <div className="sm:col-span-2 p-3.5 rounded-2xl bg-white border border-[#FDE2E7] shadow-2xs space-y-1">
                          <div className="flex items-center gap-1.5 text-xs font-bold text-[#F47D90]">
                            <Heart className="w-3.5 h-3.5 fill-current" />
                            <span>Our Joint Reflection:</span>
                          </div>
                          <p className="text-xs text-[#3E2431] font-medium leading-relaxed italic">
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
                <div className="w-14 h-14 rounded-full bg-[#FFF0F3] text-[#F47D90] flex items-center justify-center mx-auto border border-[#FDE2E7]">
                  <MessageSquareHeart className="w-7 h-7" />
                </div>
                <h4 className="text-base font-bold font-heading text-[#3E2431]">
                  No prompt memories found
                </h4>
                <p className="text-xs text-[#7A5C68] max-w-xs mx-auto">
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
