import React from 'react';
import { motion } from 'motion/react';
import { NavTab } from '../types';
import { Heart, FolderHeart, Calendar, Users } from 'lucide-react';

interface BottomNavProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  onOpenAddSnap: () => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  activeTab,
  onTabChange,
}) => {
  const tabs: { id: NavTab; label: string; icon: React.ReactNode }[] = [
    { id: 'home', label: 'Home', icon: <Heart className="w-4 h-4" /> },
    { id: 'albums', label: 'Albums', icon: <FolderHeart className="w-4 h-4" /> },
    { id: 'memories', label: 'Memories', icon: <Calendar className="w-4 h-4" /> },
    { id: 'us', label: 'Us', icon: <Users className="w-4 h-4" /> },
  ];

  return (
    <div className="fixed bottom-0 left-0 right-0 z-40 p-3 sm:p-5 pointer-events-none flex justify-center pb-safe">
      <nav className="pointer-events-auto max-w-sm sm:max-w-md w-full glass-dock rounded-full px-2.5 py-2 flex items-center justify-around glossy-top">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onTabChange(tab.id)}
              className="relative py-2 px-4 rounded-full transition-all cursor-pointer group flex flex-col items-center justify-center outline-none"
            >
              {isActive && (
                <motion.div
                  layoutId="activeTabBadge"
                  className="absolute inset-0 bg-gradient-to-r from-[#FFE5EC] via-[#FFD8E3] to-[#FFCCD5] rounded-full shadow-xs border border-white"
                  transition={{ type: 'spring', stiffness: 420, damping: 30 }}
                />
              )}

              <motion.div
                whileHover={{ scale: 1.1 }}
                whileTap={{ scale: 0.92 }}
                className={`relative z-10 flex items-center gap-1.5 transition-colors ${
                  isActive ? 'text-[#3E2431] font-extrabold' : 'text-[#6E495B]/80 hover:text-[#3E2431] font-medium'
                }`}
              >
                <div className={isActive ? 'text-[#FF4B72]' : 'text-[#6E495B]'}>{tab.icon}</div>
                <span className="text-xs font-heading tracking-tight">{tab.label}</span>
              </motion.div>
            </button>
          );
        })}
      </nav>
    </div>
  );
};
