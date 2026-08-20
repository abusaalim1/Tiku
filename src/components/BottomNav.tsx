import React from 'react';
import { motion } from 'motion/react';
import { NavTab } from '../types';
import { Plus } from 'lucide-react';
import {
  Heart3D,
  Sparkles3D,
  PolaroidCamera3D,
} from './Romance3DIcons';

interface BottomNavProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  onOpenAddSnap: () => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  activeTab,
  onTabChange,
  onOpenAddSnap,
}) => {
  return (
    <div 
      className="fixed bottom-0 left-0 right-0 z-40 px-3 sm:px-5 pointer-events-none flex justify-center"
      style={{ paddingBottom: 'max(0.75rem, calc(0.5rem + env(safe-area-inset-bottom, 0px)))' }}
    >
      <div className="pointer-events-auto w-full max-w-md glass-dock rounded-[32px] px-3 py-2 flex items-center justify-between border border-[#FDE2E7] shadow-[0_16px_40px_rgba(244,125,144,0.18)] relative bg-white/95 backdrop-blur-xl">
        {/* Left Side: Home & Gallery */}
        <div className="flex items-center gap-1 sm:gap-2 flex-1 justify-around">
          {/* Home Tab */}
          <button
            onClick={() => onTabChange('home')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer relative ${
              activeTab === 'home' ? 'text-[#F47D90] font-extrabold' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {activeTab === 'home' && (
              <motion.div
                layoutId="active-tab-glow"
                className="absolute inset-0 bg-[#FFF0F3] rounded-2xl border border-[#FDE2E7]"
                transition={{ type: 'spring', damping: 22, stiffness: 300 }}
              />
            )}
            <div className="relative z-10">
              <Heart3D size={22} />
            </div>
            <span className="relative z-10 text-[11px] font-bold tracking-tight">Home</span>
          </button>

          {/* Gallery / Albums Tab */}
          <button
            onClick={() => onTabChange('albums')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer relative ${
              activeTab === 'albums' ? 'text-[#F47D90] font-extrabold' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {activeTab === 'albums' && (
              <motion.div
                layoutId="active-tab-glow"
                className="absolute inset-0 bg-[#FFF0F3] rounded-2xl border border-[#FDE2E7]"
                transition={{ type: 'spring', damping: 22, stiffness: 300 }}
              />
            )}
            <div className="relative z-10">
              <PolaroidCamera3D size={22} />
            </div>
            <span className="relative z-10 text-[11px] font-bold tracking-tight">Albums</span>
          </button>
        </div>

        {/* Center: Big Elevated Floating Action Button */}
        <div className="relative -top-5 px-2 flex justify-center">
          <motion.button
            whileHover={{ scale: 1.1, rotate: 90 }}
            whileTap={{ scale: 0.92 }}
            transition={{ type: 'spring', damping: 18, stiffness: 350 }}
            onClick={onOpenAddSnap}
            className="w-14 h-14 rounded-full bg-gradient-to-tr from-[#EE6A80] to-[#F47D90] text-white shadow-tuchi-glow border-2 border-white flex items-center justify-center cursor-pointer transition-shadow"
            title="Add New Memory"
          >
            <Plus className="w-7 h-7 stroke-[2.8] text-white" />
          </motion.button>
        </div>

        {/* Right Side: Moments & More */}
        <div className="flex items-center gap-1 sm:gap-2 flex-1 justify-around">
          {/* Moments / Memories Timeline */}
          <button
            onClick={() => onTabChange('memories')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer relative ${
              activeTab === 'memories' ? 'text-[#F47D90] font-extrabold' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {activeTab === 'memories' && (
              <motion.div
                layoutId="active-tab-glow"
                className="absolute inset-0 bg-[#FFF0F3] rounded-2xl border border-[#FDE2E7]"
                transition={{ type: 'spring', damping: 22, stiffness: 300 }}
              />
            )}
            <div className="relative z-10">
              <Sparkles3D size={22} />
            </div>
            <span className="relative z-10 text-[11px] font-bold tracking-tight">Moments</span>
          </button>

          {/* More / Us Profile */}
          <button
            onClick={() => onTabChange('us')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer relative ${
              activeTab === 'us' ? 'text-[#F47D90] font-extrabold' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {activeTab === 'us' && (
              <motion.div
                layoutId="active-tab-glow"
                className="absolute inset-0 bg-[#FFF0F3] rounded-2xl border border-[#FDE2E7]"
                transition={{ type: 'spring', damping: 22, stiffness: 300 }}
              />
            )}
            <div className="relative z-10 flex items-center justify-center w-[22px] h-[22px]">
              <span className="text-lg leading-none">👥</span>
            </div>
            <span className="relative z-10 text-[11px] font-bold tracking-tight">Us</span>
          </button>
        </div>
      </div>
    </div>
  );
};
