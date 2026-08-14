import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { PromptCategory, PromptTemplate } from '../types';
import {
  PROMPT_CATEGORIES_METADATA,
  PROMPT_TEMPLATES,
} from '../data/dailyPromptsData';
import {
  X,
  Sparkles,
  Search,
  Check,
  Edit3,
  Flame,
  Shuffle,
  Heart,
  Plus,
} from 'lucide-react';

interface PromptSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectPrompt: (prompt: PromptTemplate) => void;
  onSelectCustomPrompt: (customText: string, category: PromptCategory) => void;
  currentPromptId?: string;
}

export const PromptSelectorModal: React.FC<PromptSelectorModalProps> = ({
  isOpen,
  onClose,
  onSelectPrompt,
  onSelectCustomPrompt,
  currentPromptId,
}) => {
  const [activeCategory, setActiveCategory] = useState<PromptCategory | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCustomMode, setIsCustomMode] = useState(false);
  const [customText, setCustomText] = useState('');
  const [customCategory, setCustomCategory] = useState<PromptCategory>('romantic');

  if (!isOpen) return null;

  const filteredTemplates = PROMPT_TEMPLATES.filter((p) => {
    if (activeCategory !== 'all' && p.category !== activeCategory) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchText = p.text.toLowerCase().includes(q);
      const matchSub = p.subtext?.toLowerCase().includes(q);
      return matchText || matchSub;
    }
    return true;
  });

  const handleCustomSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customText.trim()) return;
    onSelectCustomPrompt(customText.trim(), customCategory);
    setCustomText('');
    setIsCustomMode(false);
    onClose();
  };

  const categoryKeys = Object.keys(PROMPT_CATEGORIES_METADATA) as PromptCategory[];

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          transition={{ duration: 0.25 }}
          className="w-full max-w-2xl bg-white rounded-[32px] shadow-tuchi-lg border border-white/90 overflow-hidden flex flex-col max-h-[88vh]"
        >
          {/* MODAL HEADER */}
          <div className="p-5 sm:p-6 border-b border-neutral-100 flex items-center justify-between bg-gradient-to-r from-[#FFF0F3] via-white to-[#FAF5F0]">
            <div className="flex items-center gap-2.5">
              <div className="w-10 h-10 rounded-2xl bg-[#FF4B72] text-white flex items-center justify-center shadow-xs">
                <Sparkles className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-lg sm:text-xl font-bold font-heading text-[#3E2431]">
                  Choose Daily Prompt
                </h3>
                <p className="text-xs text-[#6E495B]">
                  Browse romantic questions, fun activities, or write your own
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="w-9 h-9 rounded-full bg-white hover:bg-neutral-100 text-[#3E2431] flex items-center justify-center transition-colors shadow-2xs border border-neutral-200 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* TOGGLE BAR: BANK VS CUSTOM */}
          <div className="px-5 sm:px-6 pt-4 pb-2 flex items-center justify-between gap-2 border-b border-neutral-100">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setIsCustomMode(false)}
                className={`px-4 py-2 rounded-full text-xs font-extrabold transition-all cursor-pointer ${
                  !isCustomMode
                    ? 'bg-[#3E2431] text-white shadow-xs'
                    : 'bg-neutral-100 text-[#6E495B] hover:bg-neutral-200/70'
                }`}
              >
                Curated Bank ({PROMPT_TEMPLATES.length})
              </button>
              <button
                type="button"
                onClick={() => setIsCustomMode(true)}
                className={`px-4 py-2 rounded-full text-xs font-extrabold transition-all cursor-pointer flex items-center gap-1.5 ${
                  isCustomMode
                    ? 'bg-[#FF4B72] text-white shadow-xs'
                    : 'bg-neutral-100 text-[#6E495B] hover:bg-neutral-200/70'
                }`}
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>Write Custom Question</span>
              </button>
            </div>
          </div>

          {/* MAIN CONTENT AREA */}
          {!isCustomMode ? (
            <div className="flex-1 overflow-y-auto p-5 sm:p-6 space-y-4">
              {/* SEARCH BAR */}
              <div className="relative">
                <Search className="w-4 h-4 text-[#8C3F5D] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search questions or keywords..."
                  className="w-full pl-10 pr-4 py-2.5 rounded-2xl bg-neutral-50 border border-neutral-200 text-xs font-semibold text-[#3E2431] focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] shadow-2xs"
                />
              </div>

              {/* CATEGORY FILTER CHIPS */}
              <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-1">
                <button
                  type="button"
                  onClick={() => setActiveCategory('all')}
                  className={`px-3 py-1.5 rounded-full text-xs font-bold flex-shrink-0 transition-all cursor-pointer ${
                    activeCategory === 'all'
                      ? 'bg-[#8C3F5D] text-white'
                      : 'bg-neutral-100 text-[#6E495B] hover:bg-neutral-200'
                  }`}
                >
                  All ({PROMPT_TEMPLATES.length})
                </button>
                {categoryKeys.map((catKey) => {
                  const meta = PROMPT_CATEGORIES_METADATA[catKey];
                  const count = PROMPT_TEMPLATES.filter((p) => p.category === catKey).length;
                  const isSelected = activeCategory === catKey;
                  return (
                    <button
                      key={catKey}
                      type="button"
                      onClick={() => setActiveCategory(catKey)}
                      className={`px-3 py-1.5 rounded-full text-xs font-bold flex items-center gap-1.5 flex-shrink-0 transition-all cursor-pointer border ${
                        isSelected
                          ? 'bg-[#3E2431] text-white border-[#3E2431] shadow-xs'
                          : 'bg-white text-[#6E495B] border-neutral-200 hover:bg-neutral-50'
                      }`}
                    >
                      <span>{meta.emoji}</span>
                      <span>{meta.label}</span>
                      <span className="opacity-70 text-[10px]">({count})</span>
                    </button>
                  );
                })}
              </div>

              {/* PROMPTS LIST */}
              <div className="space-y-2.5 pt-2">
                {filteredTemplates.length > 0 ? (
                  filteredTemplates.map((template) => {
                    const meta = PROMPT_CATEGORIES_METADATA[template.category];
                    const isCurrent = template.id === currentPromptId;

                    return (
                      <motion.div
                        key={template.id}
                        whileHover={{ y: -1 }}
                        onClick={() => {
                          onSelectPrompt(template);
                          onClose();
                        }}
                        className={`p-4 rounded-2xl border transition-all cursor-pointer text-left relative ${
                          isCurrent
                            ? 'bg-[#FFF0F3] border-[#FF4B72] shadow-xs ring-2 ring-[#FFCCD5]'
                            : 'bg-white hover:bg-neutral-50/80 border-neutral-200/90 shadow-2xs'
                        }`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-center gap-2">
                            <span className="text-base">{template.emoji}</span>
                            <span
                              className="px-2.5 py-0.5 rounded-full text-[11px] font-extrabold border"
                              style={{
                                backgroundColor: meta.bgColor,
                                color: meta.color,
                                borderColor: meta.borderColor,
                              }}
                            >
                              {template.type === 'activity' ? '🥂 Date Activity' : meta.label}
                            </span>
                          </div>

                          {isCurrent ? (
                            <span className="inline-flex items-center gap-1 text-[11px] font-extrabold text-[#FF4B72]">
                              <Check className="w-3.5 h-3.5" /> Selected
                            </span>
                          ) : (
                            <span className="text-[11px] font-bold text-neutral-400 group-hover:text-[#FF4B72]">
                              Select →
                            </span>
                          )}
                        </div>

                        <h4 className="text-sm font-bold text-[#3E2431] mt-2 leading-snug">
                          {template.text}
                        </h4>

                        {template.subtext && (
                          <p className="text-xs text-[#6E495B] mt-1 italic">
                            {template.subtext}
                          </p>
                        )}
                      </motion.div>
                    );
                  })
                ) : (
                  <div className="text-center py-10 text-xs text-[#6E495B]">
                    No prompts matched your search. Try another keyword or category!
                  </div>
                )}
              </div>
            </div>
          ) : (
            /* WRITE CUSTOM PROMPT FORM */
            <form onSubmit={handleCustomSubmit} className="flex-1 overflow-y-auto p-5 sm:p-6 space-y-4">
              <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 text-xs text-amber-900 space-y-1">
                <p className="font-bold flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-amber-600" /> Create a Custom Couple Question
                </p>
                <p className="text-amber-800 leading-relaxed">
                  Have a special question you want to ask your partner today? Type it below and it will become your couple’s active Daily Prompt!
                </p>
              </div>

              {/* Category Selector */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-[#6E495B]">
                  Select Question Category
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {categoryKeys.map((catKey) => {
                    const meta = PROMPT_CATEGORIES_METADATA[catKey];
                    const isSelected = customCategory === catKey;
                    return (
                      <button
                        key={catKey}
                        type="button"
                        onClick={() => setCustomCategory(catKey)}
                        className={`p-2.5 rounded-2xl border text-xs font-bold flex items-center gap-2 cursor-pointer transition-all ${
                          isSelected
                            ? 'bg-[#3E2431] text-white border-[#3E2431] shadow-xs'
                            : 'bg-white text-[#3E2431] border-neutral-200 hover:bg-neutral-50'
                        }`}
                      >
                        <span className="text-sm">{meta.emoji}</span>
                        <span className="truncate">{meta.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Question Text */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-[#6E495B]">
                  Your Question or Mini Activity
                </label>
                <textarea
                  required
                  rows={4}
                  value={customText}
                  onChange={(e) => setCustomText(e.target.value)}
                  placeholder="e.g. What is one thing you are most excited about for our upcoming weekend date?"
                  className="w-full p-3.5 rounded-2xl bg-neutral-50 border border-neutral-200 text-xs font-medium text-[#3E2431] focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] shadow-2xs resize-none"
                />
              </div>

              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsCustomMode(false)}
                  className="px-4 py-2.5 rounded-2xl text-xs font-bold text-[#6E495B] hover:bg-neutral-100 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!customText.trim()}
                  className="px-5 py-2.5 rounded-2xl bg-[#FF4B72] hover:bg-[#e83e63] text-white text-xs font-extrabold shadow-tuchi cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
                >
                  <Check className="w-3.5 h-3.5" />
                  <span>Set as Today's Prompt</span>
                </button>
              </div>
            </form>
          )}
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
