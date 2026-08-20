import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Category, CoupleProfile, DailyPromptEntry, Snap } from '../types';
import { formatDate } from '../utils/dateUtils';
import { Category3DIcon, Heart3D, CalendarHeart3D, Sparkles3D } from './Romance3DIcons';
import { calculatePromptStreak } from '../data/dailyPromptsData';
import { compressAvatar, MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_MB } from '../utils/imageUtils';
import { AndroidInstallModal } from './AndroidInstallModal';
import {
  Calendar,
  Camera,
  Save,
  BarChart2,
  Trash2,
  RefreshCw,
  ShieldCheck,
  LogOut,
  Flame,
  BookOpen,
  Smartphone,
} from 'lucide-react';
import { User } from 'firebase/auth';

interface UsProfileViewProps {
  profile: CoupleProfile;
  categories: Category[];
  snaps: Snap[];
  dailyPrompts?: DailyPromptEntry[];
  currentUser?: User | null;
  onSignOut?: () => void;
  onUpdateProfile: (updated: CoupleProfile) => void;
  onResetData: () => void;
  onClearAllSnaps?: () => void;
}

export const UsProfileView: React.FC<UsProfileViewProps> = ({
  profile,
  categories,
  snaps,
  dailyPrompts = [],
  currentUser,
  onSignOut,
  onUpdateProfile,
  onResetData,
  onClearAllSnaps,
}) => {
  const [userName, setUserName] = useState<string>(profile.userName || '');
  const [partnerName, setPartnerName] = useState<string>(profile.partnerName || '');
  const [startDate, setStartDate] = useState<string>(
    profile.startDate || new Date().toISOString().split('T')[0]
  );
  const [userPhoto, setUserPhoto] = useState<string>(profile.userPhotoUrl || '');
  const [partnerPhoto, setPartnerPhoto] = useState<string>(profile.partnerPhotoUrl || '');
  const [isSavedMessage, setIsSavedMessage] = useState<boolean>(false);
  const [showAndroidInstall, setShowAndroidInstall] = useState<boolean>(false);

  // Calculate days together
  const start = new Date(startDate || Date.now());
  const now = new Date();
  const diffTime = Math.abs(now.getTime() - start.getTime());
  const daysTogether = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  const promptStreakInfo = calculatePromptStreak(dailyPrompts);

  const handleFileUpload = async (
    e: React.ChangeEvent<HTMLInputElement>,
    setter: (url: string) => void
  ) => {
    const file = e.target.files?.[0];
    if (file && file.type.startsWith('image/')) {
      if (file.size > MAX_FILE_SIZE_BYTES) {
        alert(`Image size exceeds ${MAX_FILE_SIZE_MB}MB. Please choose a smaller photo.`);
        return;
      }
      try {
        const compressed = await compressAvatar(file);
        if (compressed) setter(compressed);
      } catch (err) {
        console.error('Profile photo compression error:', err);
      }
    }
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();

    const updatedProfile: CoupleProfile = {
      ...profile,
      userName: userName.trim(),
      partnerName: partnerName.trim(),
      startDate,
      userPhotoUrl: userPhoto,
      partnerPhotoUrl: partnerPhoto,
      isOnboarded: true,
    };

    onUpdateProfile(updatedProfile);
    setIsSavedMessage(true);
    setTimeout(() => setIsSavedMessage(false), 3000);
  };

  return (
    <div className="pb-32 pt-4 px-4 sm:px-6 max-w-4xl mx-auto space-y-6 text-[#2D1F27]">
      {/* AUTHENTICATION & PRIVACY VAULT BADGE */}
      <section className="glass-panel rounded-[32px] p-4 sm:p-5 border border-[#FDE2E7] shadow-tuchi flex flex-col sm:flex-row items-center justify-between gap-4 bg-white">
        <div className="flex items-center gap-3.5">
          <div className="w-11 h-11 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-200 shadow-xs flex-shrink-0">
            <ShieldCheck className="w-6 h-6 text-emerald-600" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="text-sm font-extrabold font-heading text-[#2D1F27]">Private Memory Vault</h4>
              <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-extrabold border border-emerald-200">
                Active & Protected
              </span>
            </div>
            <p className="text-xs text-[#6B5360] font-medium">
              Signed in as: <span className="font-bold text-[#2D1F27]">{currentUser?.email || (currentUser?.isAnonymous ? 'Guest Mode (Private Vault)' : 'Connected Account')}</span>
            </p>
          </div>
        </div>

        {onSignOut && (
          <motion.button
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
            onClick={onSignOut}
            className="py-2 px-4 rounded-2xl bg-white hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-extrabold border border-[#FDE2E7] shadow-xs flex items-center gap-1.5 cursor-pointer transition-all"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Sign Out</span>
          </motion.button>
        )}
      </section>

      {/* COUPLE HEADER BADGE */}
      <section className="glass-panel rounded-[36px] p-6 sm:p-8 text-center border border-[#FDE2E7] shadow-tuchi relative overflow-hidden bg-white">
        {/* Avatars Display */}
        <div className="flex items-center justify-center -space-x-4 mb-4">
          <motion.div
            whileHover={{ scale: 1.08, rotate: -3 }}
            className="w-22 h-22 rounded-full border-4 border-white bg-[#FFF0F3] overflow-hidden shadow-tuchi-glow flex items-center justify-center relative ring-2 ring-[#FDE2E7]"
          >
            {userPhoto ? (
              <img src={userPhoto} alt="You" className="w-full h-full object-cover" />
            ) : (
              <span className="text-2xl font-bold font-heading text-[#F47D90]">
                {userName ? userName.charAt(0).toUpperCase() : 'Y'}
              </span>
            )}
          </motion.div>

          <div className="w-11 h-11 rounded-full bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center z-10 shadow-tuchi-glow p-1">
            <Heart3D size={24} />
          </div>

          <motion.div
            whileHover={{ scale: 1.08, rotate: 3 }}
            className="w-22 h-22 rounded-full border-4 border-white bg-[#FFF0F3] overflow-hidden shadow-tuchi-glow flex items-center justify-center relative ring-2 ring-[#FDE2E7]"
          >
            {partnerPhoto ? (
              <img src={partnerPhoto} alt="Partner" className="w-full h-full object-cover" />
            ) : (
              <span className="text-2xl font-bold font-heading text-[#F47D90]">
                {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
              </span>
            )}
          </motion.div>
        </div>

        <h3 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#2D1F27]">
          {userName || 'You'} & <span className="font-serif-italic font-bold text-[#F47D90]">{partnerName || 'Partner'}</span>
        </h3>
        <p className="text-xs text-[#F47D90] font-extrabold mt-1">Day {daysTogether} Together 💖</p>
        <p className="text-[11px] text-[#6B5360] mt-0.5">Since {formatDate(startDate)}</p>

        {/* Quick Connection Stats Chips */}
        <div className="flex flex-wrap items-center justify-center gap-2 pt-3">
          {promptStreakInfo.streak > 0 && (
            <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-orange-50 border border-orange-200 text-orange-600 text-xs font-extrabold shadow-2xs">
              <Flame className="w-3.5 h-3.5 text-orange-500 fill-orange-500" />
              <span>{promptStreakInfo.streak} Day Prompt Streak</span>
            </span>
          )}
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#FFF0F3] border border-[#FDE2E7] text-[#F47D90] text-xs font-bold shadow-2xs">
            <BookOpen className="w-3.5 h-3.5 text-[#F47D90]" />
            <span>{dailyPrompts.length} Prompts Answered</span>
          </span>
        </div>
      </section>

      {/* STATS BREAKDOWN */}
      <section className="glass-panel rounded-[32px] p-6 border border-[#FDE2E7] shadow-tuchi space-y-4 bg-white">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold font-heading text-[#2D1F27] flex items-center gap-2">
            <BarChart2 className="w-5 h-5 text-[#F47D90]" /> Memory <span className="font-serif-italic font-bold text-[#F47D90]">Breakdown</span>
          </h3>
          <span className="text-xs font-bold text-[#6B5360]">{snaps.length} Total Snaps</span>
        </div>

        {/* Categories Breakdown List with 3D Icons */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
          {categories.map((cat) => {
            const count = snaps.filter((s) => s.categoryId === cat.id).length;
            const percentage =
              snaps.length > 0 ? Math.round((count / snaps.length) * 100) : 0;

            return (
              <motion.div
                key={cat.id}
                whileHover={{ scale: 1.02 }}
                className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] flex items-center justify-between shadow-xs"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-2xl bg-white border border-[#FDE2E7] flex items-center justify-center flex-shrink-0 shadow-xs">
                    <Category3DIcon
                      categoryName={cat.name}
                      size={20}
                    />
                  </div>
                  <div>
                    <span className="text-xs font-bold text-[#2D1F27] block">
                      {cat.name}
                    </span>
                    <p className="text-[10px] text-[#6B5360] font-medium">{percentage}% of all snaps</p>
                  </div>
                </div>

                <span className="text-xs font-bold font-heading text-[#F47D90] px-3 py-1 rounded-full bg-[#FFF0F3] border border-[#FDE2E7]">
                  {count}
                </span>
              </motion.div>
            );
          })}
        </div>
      </section>

      {/* EDIT PROFILE FORM */}
      <section className="glass-panel rounded-[32px] p-6 border border-[#FDE2E7] shadow-tuchi space-y-6 bg-white">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold font-heading text-[#2D1F27]">
            Edit Relationship & Profile <span className="font-serif-italic font-bold text-[#F47D90]">Photos</span>
          </h3>
          {isSavedMessage && (
            <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-3 py-1 rounded-full border border-emerald-200">
              ✓ Saved successfully!
            </span>
          )}
        </div>

        <form onSubmit={handleSave} className="space-y-6">
          {/* PROFILE PICTURES EDITING SECTION */}
          <div className="space-y-4 pt-1 border-b border-[#FDE2E7] pb-6">
            <h4 className="text-xs font-bold text-[#6B5360] uppercase tracking-wider flex items-center gap-1.5">
              <Camera className="w-4 h-4 text-[#F47D90]" /> Profile Pictures (Max 20MB)
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Your Profile Picture */}
              <div className="p-4 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-3 shadow-xs">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-[#2D1F27]">Your Photo ({userName || 'You'})</span>
                  {userPhoto && (
                    <button
                      type="button"
                      onClick={() => setUserPhoto('')}
                      className="text-[11px] text-red-500 hover:text-red-700 font-semibold cursor-pointer"
                    >
                      Remove
                    </button>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  <div className="relative w-16 h-16 rounded-full overflow-hidden border-2 border-[#FDE2E7] flex-shrink-0 bg-[#FFF0F3] flex items-center justify-center shadow-xs">
                    {userPhoto ? (
                      <img src={userPhoto} alt="Your profile" className="w-full h-full object-cover" />
                    ) : (
                      <span className="text-xl font-bold text-[#F47D90]">
                        {userName ? userName.charAt(0).toUpperCase() : 'Y'}
                      </span>
                    )}
                  </div>

                  <div className="flex-1 space-y-2">
                    <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-extrabold cursor-pointer transition-all shadow-xs border border-[#FDE2E7]">
                      <Camera className="w-3.5 h-3.5" />
                      <span>Upload Photo</span>
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setUserPhoto)}
                      />
                    </label>

                    <div>
                      <input
                        type="text"
                        value={userPhoto}
                        onChange={(e) => setUserPhoto(e.target.value)}
                        placeholder="Or paste Image URL..."
                        className="w-full px-3 py-1.5 rounded-xl bg-white border border-[#FDE2E7] text-[#2D1F27] text-xs placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90]"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Partner Profile Picture */}
              <div className="p-4 rounded-3xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-3 shadow-xs">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-[#2D1F27]">Partner Photo ({partnerName || 'Partner'})</span>
                  {partnerPhoto && (
                    <button
                      type="button"
                      onClick={() => setPartnerPhoto('')}
                      className="text-[11px] text-red-500 hover:text-red-700 font-semibold cursor-pointer"
                    >
                      Remove
                    </button>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  <div className="relative w-16 h-16 rounded-full overflow-hidden border-2 border-[#FDE2E7] flex-shrink-0 bg-[#FFF0F3] flex items-center justify-center shadow-xs">
                    {partnerPhoto ? (
                      <img src={partnerPhoto} alt="Partner profile" className="w-full h-full object-cover" />
                    ) : (
                      <span className="text-xl font-bold text-[#F47D90]">
                        {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
                      </span>
                    )}
                  </div>

                  <div className="flex-1 space-y-2">
                    <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-extrabold cursor-pointer transition-all shadow-xs border border-[#FDE2E7]">
                      <Camera className="w-3.5 h-3.5" />
                      <span>Upload Photo</span>
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setPartnerPhoto)}
                      />
                    </label>

                    <div>
                      <input
                        type="text"
                        value={partnerPhoto}
                        onChange={(e) => setPartnerPhoto(e.target.value)}
                        placeholder="Or paste Image URL..."
                        className="w-full px-3 py-1.5 rounded-xl bg-white border border-[#FDE2E7] text-[#2D1F27] text-xs placeholder:text-[#9E8592]/60 focus:outline-none focus:border-[#F47D90]"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider mb-1">
                Your Name
              </label>
              <input
                type="text"
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                placeholder="Your name"
                className="w-full px-4 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs font-medium focus:outline-none focus:border-[#F47D90]"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider mb-1">
                Partner's Name
              </label>
              <input
                type="text"
                value={partnerName}
                onChange={(e) => setPartnerName(e.target.value)}
                placeholder="Partner's name"
                className="w-full px-4 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs font-medium focus:outline-none focus:border-[#F47D90]"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider mb-1 flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5 text-[#F47D90]" /> Relationship Start Date (Used to Unlock Milestones)
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full px-4 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs font-semibold focus:outline-none focus:border-[#F47D90]"
            />
          </div>

          <div className="pt-2">
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              type="submit"
              className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-extrabold shadow-tuchi-glow flex items-center justify-center gap-2 cursor-pointer transition-all"
            >
              <Save className="w-4 h-4 text-white" />
              <span>Save Profile & Photos</span>
            </motion.button>
          </div>
        </form>
      </section>

      {/* ANDROID APP INSTALLATION BADGE & ACTIONS */}
      <section className="glass-panel rounded-[32px] p-5 border border-[#FDE2E7] shadow-tuchi flex flex-col sm:flex-row items-center justify-between gap-4 bg-white">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] p-2 shadow-xs flex-shrink-0 flex items-center justify-center">
            <img src="/icon.svg" alt="Tiku App" className="w-full h-full object-contain" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="text-sm font-extrabold font-heading text-[#2D1F27]">Tiku for Android</h4>
              <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-extrabold border border-emerald-200">
                Native PWA Ready
              </span>
            </div>
            <p className="text-xs text-[#6B5360] font-medium mt-0.5">
              Install as a full-screen Android app with home screen icon & offline vault.
            </p>
          </div>
        </div>

        <motion.button
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.97 }}
          onClick={() => setShowAndroidInstall(true)}
          className="py-2.5 px-5 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-extrabold shadow-tuchi-glow flex items-center gap-2 cursor-pointer transition-all flex-shrink-0"
        >
          <Smartphone className="w-4 h-4 text-white" />
          <span>Install Android App</span>
        </motion.button>
      </section>

      {/* DEMO / RESET CONTROLS */}
      <section className="glass-panel rounded-[32px] p-5 border border-[#FDE2E7] shadow-xs text-center space-y-3 bg-white">
        <h4 className="text-xs font-bold uppercase text-[#6B5360]">Scrapbook Data Options</h4>
        <div className="flex flex-wrap items-center justify-center gap-3">
          {onClearAllSnaps && snaps.length > 0 && (
            <motion.button
              whileTap={{ scale: 0.95 }}
              onClick={() => {
                if (confirm('Are you sure you want to delete all pictures? This will empty your photo album.')) {
                  onClearAllSnaps();
                }
              }}
              className="py-2 px-4 rounded-2xl bg-red-50 hover:bg-red-100 text-red-600 text-xs font-bold border border-red-200 flex items-center gap-1.5 shadow-xs cursor-pointer"
            >
              <Trash2 className="w-3.5 h-3.5" /> Empty All Pictures ({snaps.length})
            </motion.button>
          )}

          <motion.button
            whileTap={{ scale: 0.95 }}
            onClick={() => {
              if (confirm('Reset to initial settings and clear data?')) {
                onResetData();
              }
            }}
            className="py-2 px-4 rounded-2xl bg-[#FFF7F9] hover:bg-[#FFF0F3] text-[#F47D90] text-xs font-bold border border-[#FDE2E7] flex items-center gap-1.5 shadow-xs cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5 text-[#F47D90]" /> Reset Profile Data
          </motion.button>
        </div>
      </section>

      {/* Android Install Modal */}
      <AndroidInstallModal
        isOpen={showAndroidInstall}
        onClose={() => setShowAndroidInstall(false)}
      />
    </div>
  );
};
