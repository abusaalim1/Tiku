import React from 'react';
import { motion } from 'motion/react';
import { NavTab } from '../types';
import { Home, Image as ImageIcon, Sparkles, MoreHorizontal, Heart } from 'lucide-react';

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
      <div className="pointer-events-auto w-full max-w-md glass-dock rounded-[32px] px-3 py-2 flex items-center justify-between border border-white/90 shadow-[0_16px_40px_rgba(255,182,193,0.35)] relative">
        {/* Left Side: Home & Gallery */}
        <div className="flex items-center gap-1 sm:gap-3 flex-1 justify-around">
          {/* Home Tab */}
          <button
            onClick={() => onTabChange('home')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'home' ? 'text-[#F47D90] font-extrabold' : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <Home className={`w-5 h-5 ${activeTab === 'home' ? 'stroke-[2.5] fill-[#F47D90]/15' : ''}`} />
            <span className="text-[11px] font-semibold tracking-tight">Home</span>
          </button>

          {/* Gallery / Albums Tab */}
          <button
            onClick={() => onTabChange('albums')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'albums' ? 'text-[#F47D90] font-extrabold' : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <ImageIcon className={`w-5 h-5 ${activeTab === 'albums' ? 'stroke-[2.5] text-[#F47D90]' : ''}`} />
            <span className="text-[11px] font-semibold tracking-tight">Gallery</span>
          </button>
        </div>

        {/* Center: Big Elevated Floating Heart Action Button */}
        <div className="relative -top-5 px-2 flex justify-center">
          <motion.button
            whileHover={{ scale: 1.12 }}
            whileTap={{ scale: 0.92 }}
            onClick={onOpenAddSnap}
            className="w-14 h-14 rounded-full bg-gradient-to-tr from-[#EE6A80] via-[#F47D90] to-[#F8A0AF] text-white shadow-[0_10px_25px_rgba(244,125,144,0.45)] border-4 border-white flex items-center justify-center cursor-pointer transition-shadow"
            title="Add New Memory Snap"
          >
            <Heart className="w-6 h-6 fill-white text-white animate-pulse-soft" />
          </motion.button>
        </div>

        {/* Right Side: Moments & More */}
        <div className="flex items-center gap-1 sm:gap-3 flex-1 justify-around">
          {/* Moments / Memories Timeline */}
          <button
            onClick={() => onTabChange('memories')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'memories' ? 'text-[#F47D90] font-extrabold' : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <Sparkles className={`w-5 h-5 ${activeTab === 'memories' ? 'stroke-[2.5] text-[#F47D90]' : ''}`} />
            <span className="text-[11px] font-semibold tracking-tight">Moments</span>
          </button>

          {/* More / Us Profile */}
          <button
            onClick={() => onTabChange('us')}
            className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'us' ? 'text-[#F47D90] font-extrabold' : 'text-[#7A5C68] hover:text-[#3E2431]'
            }`}
          >
            <MoreHorizontal className={`w-5 h-5 ${activeTab === 'us' ? 'stroke-[2.5] text-[#F47D90]' : ''}`} />
            <span className="text-[11px] font-semibold tracking-tight">More</span>
          </button>
        </div>
      </div>
    </div>
  );
};
