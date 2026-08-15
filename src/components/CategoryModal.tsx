import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, Sparkles, Tag } from 'lucide-react';
import { CategoryIcon, AVAILABLE_ICON_NAMES } from '../utils/categoryIcons';

interface CategoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaveCategory: (newCategory: { name: string; iconName: string; color: string }) => void;
}

const PRESET_COLORS = ['#F47D90', '#EE6A80', '#F8A0AF', '#FDE2E7', '#F29CA9', '#E06B7F', '#C4576C', '#3E2431'];

export const CategoryModal: React.FC<CategoryModalProps> = ({ isOpen, onClose, onSaveCategory }) => {
  const [name, setName] = useState<string>('');
  const [iconName, setIconName] = useState<string>('Heart');
  const [color, setColor] = useState<string>('#F47D90');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    onSaveCategory({
      name: name.trim(),
      iconName,
      color,
    });

    setName('');
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/40 backdrop-blur-md cursor-pointer"
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.88, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.88, y: 20 }}
            transition={{ type: 'spring', damping: 24, stiffness: 300 }}
            className="relative w-full max-w-sm glass-modal rounded-[28px] p-6 border border-[#FDE2E7] z-10 glossy-top"
          >
            {/* Close Button */}
            <button
              onClick={onClose}
              className="absolute top-4 right-4 w-8 h-8 rounded-full bg-white hover:bg-[#FFF0F3] text-[#7A5C68] flex items-center justify-center transition-all shadow-xs cursor-pointer hover:scale-105 active:scale-95"
            >
              <X className="w-4 h-4" />
            </button>

            <div className="text-center mb-5">
              <div className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#FFF0F3] text-[#F47D90] text-xs font-bold mb-1.5 shadow-2xs border border-[#FDE2E7]">
                <Tag className="w-3.5 h-3.5" />
                <span>Custom Tag</span>
              </div>
              <h3 className="text-xl font-bold font-heading text-[#3E2431]">Create Category</h3>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Category Name */}
              <div>
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider mb-1">
                  Category Name
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Late Night Drives, Concerts..."
                  autoFocus
                  className="w-full px-4 py-2.5 rounded-2xl bg-white border-2 border-[#FDE2E7] text-[#3E2431] text-sm focus:outline-none focus:border-[#F47D90] transition-all font-medium"
                />
              </div>

              {/* Icon Selection */}
              <div>
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider mb-1">
                  Pick an Icon
                </label>
                <div className="flex flex-wrap gap-2 justify-center p-2 rounded-2xl bg-white border-2 border-[#FDE2E7] max-h-36 overflow-y-auto no-scrollbar">
                  {AVAILABLE_ICON_NAMES.map((ic) => (
                    <button
                      key={ic}
                      type="button"
                      onClick={() => setIconName(ic)}
                      className={`w-9 h-9 rounded-full flex items-center justify-center transition-all cursor-pointer ${
                        iconName === ic
                          ? 'bg-[#F47D90] text-white scale-110 shadow-xs ring-2 ring-[#FDE2E7]'
                          : 'text-[#7A5C68] hover:bg-[#FFF0F3]'
                      }`}
                      title={ic}
                    >
                      <CategoryIcon iconName={ic} className="w-4 h-4" />
                    </button>
                  ))}
                </div>
              </div>

              {/* Color Selection */}
              <div>
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider mb-1">
                  Accent Color
                </label>
                <div className="flex items-center justify-center gap-2.5 p-2 rounded-2xl bg-white border-2 border-[#FDE2E7]">
                  {PRESET_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`w-7 h-7 rounded-full border-2 border-white transition-transform cursor-pointer ${
                        color === c ? 'scale-125 shadow-md ring-2 ring-[#3E2431]' : 'hover:scale-110'
                      }`}
                      style={{ backgroundColor: c }}
                    />
                  ))}
                </div>
              </div>

              {/* Submit */}
              <div className="pt-2">
                <motion.button
                  whileHover={{ scale: name.trim() ? 1.02 : 1 }}
                  whileTap={{ scale: name.trim() ? 0.98 : 1 }}
                  type="submit"
                  disabled={!name.trim()}
                  className={`w-full py-3.5 px-5 rounded-full font-bold text-xs shadow-tuchi transition-all cursor-pointer ${
                    name.trim()
                      ? 'bg-[#3E2431] hover:bg-[#2D1822] text-white'
                      : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5 inline mr-1" /> Add Custom Category
                </motion.button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
