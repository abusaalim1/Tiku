import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { CoupleProfile } from '../types';
import { ArrowRight, ArrowLeft, Camera, Sparkles, Check, Heart } from 'lucide-react';
import { compressAvatar } from '../utils/imageUtils';
import confetti from 'canvas-confetti';
import {
  Heart3D,
  CoupleHearts3D,
  LoveLetter3D,
  WingedHeart3D,
  Calendar3D,
  PolaroidCamera3D,
  PartyPopper3D,
  Sparkles3D,
} from './Romance3DIcons';

interface OnboardingProps {
  initialProfile: CoupleProfile;
  onComplete: (profile: CoupleProfile) => void;
}

export const Onboarding: React.FC<OnboardingProps> = ({ initialProfile, onComplete }) => {
  const [step, setStep] = useState<number>(1);
  const [userName, setUserName] = useState<string>(initialProfile.userName || '');
  const [partnerName, setPartnerName] = useState<string>(initialProfile.partnerName || '');
  const [startDate, setStartDate] = useState<string>(
    initialProfile.startDate || new Date().toISOString().split('T')[0]
  );
  const [userPhoto, setUserPhoto] = useState<string>(initialProfile.userPhotoUrl || '');
  const [partnerPhoto, setPartnerPhoto] = useState<string>(initialProfile.partnerPhotoUrl || '');

  const calculateDays = (dateStr: string) => {
    try {
      const start = new Date(dateStr);
      const now = new Date();
      const diffTime = Math.abs(now.getTime() - start.getTime());
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
      return isNaN(diffDays) ? 1 : diffDays;
    } catch {
      return 1;
    }
  };

  const daysCount = calculateDays(startDate);

  const handleNext = () => {
    if (step < 6) {
      setStep((prev) => prev + 1);
    } else {
      finishOnboarding();
    }
  };

  const handleBack = () => {
    if (step > 1) {
      setStep((prev) => prev - 1);
    }
  };

  const finishOnboarding = async () => {
    confetti({
      particleCount: 120,
      spread: 80,
      origin: { y: 0.55 },
      colors: ['#F47D90', '#EE6A80', '#FDE2E7', '#FFFFFF', '#FFE082', '#FF9FB0'],
    });

    const compressedUserPhoto = await compressAvatar(userPhoto);
    const compressedPartnerPhoto = await compressAvatar(partnerPhoto);

    const updatedProfile: CoupleProfile = {
      userName: userName.trim() || 'You',
      partnerName: partnerName.trim() || 'Partner',
      startDate: startDate || new Date().toISOString().split('T')[0],
      userPhotoUrl: compressedUserPhoto,
      partnerPhotoUrl: compressedPartnerPhoto,
      isOnboarded: true,
      createdAt: initialProfile.createdAt || new Date().toISOString(),
    };

    onComplete(updatedProfile);
  };

  const handleFileUpload = async (
    e: React.ChangeEvent<HTMLInputElement>,
    setter: (url: string) => void
  ) => {
    const file = e.target.files?.[0];
    if (file) {
      try {
        const compressed = await compressAvatar(file);
        setter(compressed);
      } catch (err) {
        console.error('Avatar compression failed:', err);
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 bg-gradient-to-b from-[#FFF7F9] via-[#FFF0F3]/60 to-[#FAF5F0] text-[#2D1F27] relative overflow-hidden">
      {/* Background Soft Pastel Ambient Orbs */}
      <motion.div
        animate={{ scale: [1, 1.15, 1], x: [0, 20, 0], y: [0, -20, 0] }}
        transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute -top-12 -right-12 w-96 h-96 bg-[#FDE2E7]/70 rounded-full blur-3xl pointer-events-none -z-10"
      />
      <motion.div
        animate={{ scale: [1, 1.2, 1], x: [0, -20, 0], y: [0, 20, 0] }}
        transition={{ duration: 12, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="absolute -bottom-12 -left-12 w-96 h-96 bg-[#FFF0F3] rounded-full blur-3xl pointer-events-none -z-10"
      />

      <motion.div
        initial={{ opacity: 0, y: 25, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-md glass-modal rounded-[36px] p-6 sm:p-8 shadow-[0_24px_60px_rgba(244,125,144,0.15)] border border-[#FDE2E7] relative overflow-hidden flex flex-col justify-between min-h-[540px] text-[#2D1F27] bg-white/95"
      >
        {/* Progress Bar Header */}
        <div className="relative z-10 mb-4">
          <div className="flex items-center justify-between text-xs font-bold text-[#6B5360] mb-2">
            <span>Step {step} of 6</span>
            <span className="text-[#F47D90] font-extrabold">{Math.round((step / 6) * 100)}%</span>
          </div>
          <div className="w-full bg-[#FFF0F3] h-2.5 rounded-full overflow-hidden p-0.5 border border-[#FDE2E7]">
            <motion.div
              className="bg-gradient-to-r from-[#F47D90] to-[#EE6A80] h-full rounded-full shadow-tuchi-glow"
              initial={false}
              animate={{ width: `${(step / 6) * 100}%` }}
              transition={{ duration: 0.35, ease: 'easeOut' }}
            />
          </div>
        </div>

        {/* Step Content with AnimatePresence */}
        <div className="relative z-10 flex-1 flex flex-col justify-center my-2">
          <AnimatePresence mode="wait">
            {/* STEP 1: WELCOME SCREEN */}
            {step === 1 && (
              <motion.div
                key="step-1"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
                className="text-center space-y-5 py-3"
              >
                <div className="relative inline-block mx-auto">
                  <motion.div
                    animate={{ scale: [1, 1.06, 1], rotate: [0, -1, 1, 0] }}
                    transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
                    className="w-24 h-24 mx-auto bg-gradient-to-tr from-[#FFF0F3] to-white rounded-3xl flex items-center justify-center shadow-tuchi-glow border border-[#FDE2E7]"
                  >
                    <CoupleHearts3D size={64} />
                  </motion.div>
                  <motion.div
                    animate={{ rotate: [0, 15, -15, 0], scale: [1, 1.15, 1] }}
                    transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
                    className="absolute -top-2 -right-2"
                  >
                    <Sparkles3D size={28} />
                  </motion.div>
                </div>

                <div className="space-y-2">
                  <h1 className="text-3xl font-extrabold font-heading text-[#2D1F27] tracking-tight">
                    Tiku <span className="font-serif-italic font-bold text-[#F47D90]">Scrapbook</span>
                  </h1>
                  <p className="text-[#6B5360] text-xs sm:text-sm font-medium max-w-xs mx-auto leading-relaxed">
                    Your private, aesthetic couple memory space to save polaroids, milestones, and cherish everyday moments together.
                  </p>
                </div>

                <div className="pt-2">
                  <motion.button
                    whileHover={{ scale: 1.02, y: -1 }}
                    whileTap={{ scale: 0.98 }}
                    onClick={handleNext}
                    className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white font-extrabold text-sm sm:text-base shadow-tuchi-glow transition-all flex items-center justify-center gap-2 cursor-pointer"
                  >
                    <span>Get Started</span>
                    <ArrowRight className="w-5 h-5 text-white" />
                  </motion.button>
                </div>
              </motion.div>
            )}

            {/* STEP 2: YOUR NAME */}
            {step === 2 && (
              <motion.div
                key="step-2"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
                className="space-y-6 py-2"
              >
                <div className="text-center space-y-2">
                  <div className="w-16 h-16 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                    <LoveLetter3D size={38} />
                  </div>
                  <h2 className="text-2xl font-bold font-heading text-[#2D1F27]">
                    First, what is your <span className="font-serif-italic font-bold text-[#F47D90]">name</span>?
                  </h2>
                  <p className="text-xs text-[#6B5360]">So your memories know who wrote each cute note!</p>
                </div>

                <div className="space-y-2">
                  <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider">
                    Your Name
                  </label>
                  <input
                    type="text"
                    value={userName}
                    onChange={(e) => setUserName(e.target.value)}
                    placeholder="e.g. Alex"
                    autoFocus
                    className="w-full px-5 py-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-base font-medium focus:outline-none focus:border-[#F47D90] shadow-xs"
                  />
                </div>
              </motion.div>
            )}

            {/* STEP 3: PARTNER'S NAME */}
            {step === 3 && (
              <motion.div
                key="step-3"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
                className="space-y-6 py-2"
              >
                <div className="text-center space-y-2">
                  <div className="w-16 h-16 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                    <WingedHeart3D size={44} />
                  </div>
                  <h2 className="text-2xl font-bold font-heading text-[#2D1F27]">
                    And who is your <span className="font-serif-italic font-bold text-[#F47D90]">partner</span>?
                  </h2>
                  <p className="text-xs text-[#6B5360]">The love of your life sharing this private journal.</p>
                </div>

                <div className="space-y-2">
                  <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider">
                    Partner's Name
                  </label>
                  <input
                    type="text"
                    value={partnerName}
                    onChange={(e) => setPartnerName(e.target.value)}
                    placeholder="e.g. Maya"
                    autoFocus
                    className="w-full px-5 py-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-base font-medium focus:outline-none focus:border-[#F47D90] shadow-xs"
                  />
                </div>
              </motion.div>
            )}

            {/* STEP 4: RELATIONSHIP START DATE */}
            {step === 4 && (
              <motion.div
                key="step-4"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
                className="space-y-5 py-2"
              >
                <div className="text-center space-y-2">
                  <div className="w-16 h-16 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                    <Calendar3D size={40} />
                  </div>
                  <h2 className="text-2xl font-bold font-heading text-[#2D1F27]">
                    When did your <span className="font-serif-italic font-bold text-[#F47D90]">love story</span> begin?
                  </h2>
                  <p className="text-xs text-[#6B5360]">
                    We'll calculate your Day {daysCount} anniversary count automatically.
                  </p>
                </div>

                <div className="space-y-2">
                  <label className="block text-xs font-bold text-[#6B5360] uppercase tracking-wider">
                    Anniversary / Start Date
                  </label>
                  <input
                    type="date"
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="w-full px-5 py-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-base font-medium focus:outline-none focus:border-[#F47D90] shadow-xs cursor-pointer"
                  />
                </div>

                {/* Days Together Counter Badge Preview */}
                <div className="p-3.5 rounded-2xl bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center gap-2">
                  <Heart3D size={20} />
                  <span className="text-xs font-bold text-[#2D1F27]">
                    <span className="text-[#F47D90] font-extrabold">{daysCount}</span> Days of Love together
                  </span>
                </div>
              </motion.div>
            )}

            {/* STEP 5: PROFILE PHOTOS */}
            {step === 5 && (
              <motion.div
                key="step-5"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
                className="space-y-4 py-1"
              >
                <div className="text-center space-y-1">
                  <div className="w-14 h-14 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                    <PolaroidCamera3D size={36} />
                  </div>
                  <h2 className="text-2xl font-bold font-heading text-[#2D1F27]">
                    Add couple <span className="font-serif-italic font-bold text-[#F47D90]">avatars</span>
                  </h2>
                  <p className="text-xs text-[#6B5360]">
                    Upload photos for your header & daily notes.
                  </p>
                </div>

                {/* Photos Side by Side */}
                <div className="grid grid-cols-2 gap-3 pt-1">
                  {/* User Photo */}
                  <div className="flex flex-col items-center gap-2 p-3 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7]">
                    <span className="text-xs font-bold text-[#2D1F27] truncate max-w-[100px]">{userName || 'You'}</span>
                    <div className="relative w-20 h-20 rounded-full overflow-hidden border-2 border-[#F47D90] shadow-xs group bg-[#FFF0F3] flex items-center justify-center">
                      {userPhoto ? (
                        <img
                          src={userPhoto}
                          alt="Your avatar"
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <span className="text-xl font-bold text-[#F47D90]">
                          {userName ? userName.charAt(0).toUpperCase() : 'Y'}
                        </span>
                      )}
                      <label className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer text-white">
                        <Camera className="w-5 h-5" />
                        <input
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={(e) => handleFileUpload(e, setUserPhoto)}
                        />
                      </label>
                    </div>
                    <label className="text-[11px] font-bold text-[#F47D90] hover:underline cursor-pointer">
                      Upload
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setUserPhoto)}
                      />
                    </label>
                  </div>

                  {/* Partner Photo */}
                  <div className="flex flex-col items-center gap-2 p-3 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7]">
                    <span className="text-xs font-bold text-[#2D1F27] truncate max-w-[100px]">{partnerName || 'Partner'}</span>
                    <div className="relative w-20 h-20 rounded-full overflow-hidden border-2 border-[#F47D90] shadow-xs group bg-[#FFF0F3] flex items-center justify-center">
                      {partnerPhoto ? (
                        <img
                          src={partnerPhoto}
                          alt="Partner avatar"
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <span className="text-xl font-bold text-[#F47D90]">
                          {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
                        </span>
                      )}
                      <label className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer text-white">
                        <Camera className="w-5 h-5" />
                        <input
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={(e) => handleFileUpload(e, setPartnerPhoto)}
                        />
                      </label>
                    </div>
                    <label className="text-[11px] font-bold text-[#F47D90] hover:underline cursor-pointer">
                      Upload
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setPartnerPhoto)}
                      />
                    </label>
                  </div>
                </div>
              </motion.div>
            )}

            {/* STEP 6: DONE & CELEBRATE */}
            {step === 6 && (
              <motion.div
                key="step-6"
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.3 }}
                className="text-center space-y-4 py-2"
              >
                <div className="w-16 h-16 mx-auto rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-tuchi-glow">
                  <PartyPopper3D size={44} />
                </div>

                <div className="space-y-1.5">
                  <h2 className="text-2xl font-bold font-heading text-[#2D1F27]">
                    You're all <span className="font-serif-italic font-bold text-[#F47D90]">set!</span>
                  </h2>
                  <p className="text-xs text-[#6B5360] max-w-xs mx-auto">
                    Welcome, <strong className="text-[#2D1F27]">{userName || 'You'}</strong> &{' '}
                    <strong className="text-[#2D1F27]">{partnerName || 'Partner'}</strong>! Your couple memory space is ready.
                  </p>
                </div>

                {/* Preview Avatar Badge */}
                <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] inline-flex items-center gap-3 shadow-xs mx-auto">
                  <div className="flex items-center -space-x-3">
                    {userPhoto ? (
                      <img
                        src={userPhoto}
                        alt={userName}
                        className="w-11 h-11 rounded-full border-2 border-white object-cover shadow-xs"
                      />
                    ) : (
                      <div className="w-11 h-11 rounded-full border-2 border-white bg-[#FFF0F3] flex items-center justify-center text-[#F47D90] font-bold shadow-xs">
                        {userName ? userName.charAt(0).toUpperCase() : 'Y'}
                      </div>
                    )}
                    {partnerPhoto ? (
                      <img
                        src={partnerPhoto}
                        alt={partnerName}
                        className="w-11 h-11 rounded-full border-2 border-white object-cover shadow-xs"
                      />
                    ) : (
                      <div className="w-11 h-11 rounded-full border-2 border-white bg-[#FFF0F3] flex items-center justify-center text-[#F47D90] font-bold shadow-xs">
                        {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
                      </div>
                    )}
                  </div>
                  <div className="text-left">
                    <div className="text-xs sm:text-sm font-bold font-heading text-[#2D1F27]">
                      {userName || 'You'} & {partnerName || 'Partner'}
                    </div>
                    <div className="text-[11px] text-[#F47D90] font-bold flex items-center gap-1">
                      <Sparkles className="w-3 h-3" /> Ready to capture moments
                    </div>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Navigation Buttons Footer */}
        <div className="relative z-10 pt-4 mt-2 flex items-center justify-between border-t border-[#FDE2E7]">
          {step > 1 ? (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={handleBack}
              className="py-2.5 px-4 rounded-xl bg-white hover:bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] text-xs font-bold flex items-center gap-1.5 transition-colors border border-[#FDE2E7] cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back</span>
            </motion.button>
          ) : (
            <div />
          )}

          {step > 1 && (
            <motion.button
              whileHover={{ scale: 1.02, y: -1 }}
              whileTap={{ scale: 0.98 }}
              onClick={handleNext}
              className="py-2.5 px-6 rounded-xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs font-extrabold shadow-tuchi-glow flex items-center gap-2 transition-all cursor-pointer"
            >
              <span>{step === 6 ? 'Enter Scrapbook' : 'Next'}</span>
              <ArrowRight className="w-4 h-4 text-white" />
            </motion.button>
          )}
        </div>
      </motion.div>
    </div>
  );
};
