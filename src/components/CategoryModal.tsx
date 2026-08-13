import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, Sparkles, Tag } from 'lucide-react';
import { CategoryIcon, AVAILABLE_ICON_NAMES } from '../utils/categoryIcons';

interface CategoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaveCategory: (newCategory: { name: string; iconName: string; color: string }) => void;
}

const PRESET_COLORS = ['#E8AFC0', '#FFDEE5', '#BFE3D0', '#D9A15E', '#7A5566', '#A8DADC', '#F4A261', '#E76F51'];

export const CategoryModal: React.FC<CategoryModalProps> = ({ isOpen, onClose, onSaveCategory }) => {
  const [name, setName] = useState<string>('');
  const [iconName, setIconName] = useState<string>('Heart');
  const [color, setColor] = useState<string>('#E8AFC0');

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
            className="relative w-full max-w-sm glass-modal rounded-[28px] p-6 border border-white/90 z-10 glossy-top"
          >
            {/* Close Button */}
            <button
              onClick={onClose}
              className="absolute top-4 right-4 w-8 h-8 rounded-full bg-white/90 hover:bg-[#FFDEE5] text-[#7A5566] flex items-center justify-center transition-all shadow-xs cursor-pointer hover:scale-105 active:scale-95"
            >
              <X className="w-4 h-4" />
            </button>

            <div className="text-center mb-5">
              <div className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-[#FFE5EC] text-[#8C3F5D] text-xs font-bold mb-1.5 shadow-2xs">
                <Tag className="w-3.5 h-3.5" />
                <span>Custom Tag</span>
              </div>
              <h3 className="text-xl font-bold font-heading text-[#4A2C3A]">Create Category</h3>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Category Name */}
              <div>
                <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1">
                  Category Name
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Late Night Drives, Concerts..."
                  autoFocus
                  className="w-full px-4 py-2.5 rounded-2xl bg-white border-2 border-[#FFDEE5] text-[#4A2C3A] text-sm focus:outline-none focus:border-[#8C3F5D] transition-all font-medium"
                />
              </div>

              {/* Icon Selection */}
              <div>
                <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1">
                  Pick an Icon
                </label>
                <div className="flex flex-wrap gap-2 justify-center p-2 rounded-2xl bg-white/80 border-2 border-[#FFDEE5] max-h-36 overflow-y-auto no-scrollbar">
                  {AVAILABLE_ICON_NAMES.map((ic) => (
                    <button
                      key={ic}
                      type="button"
                      onClick={() => setIconName(ic)}
                      className={`w-9 h-9 rounded-full flex items-center justify-center transition-all cursor-pointer ${
                        iconName === ic
                          ? 'bg-[#8C3F5D] text-white scale-110 shadow-xs ring-2 ring-[#FFDEE5]'
                          : 'text-[#7A5566] hover:bg-[#FFF0F3]'
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
                <label className="block text-xs font-bold text-[#7A5566] uppercase tracking-wider mb-1">
                  Accent Color
                </label>
                <div className="flex items-center justify-center gap-2.5 p-2 rounded-2xl bg-white/80 border-2 border-[#FFDEE5]">
                  {PRESET_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`w-7 h-7 rounded-full border-2 border-white transition-transform cursor-pointer ${
                        color === c ? 'scale-125 shadow-md ring-2 ring-[#4A2C3A]' : 'hover:scale-110'
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
                      ? 'bg-[#4A2C3A] hover:bg-[#38202b] text-white'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed'
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
