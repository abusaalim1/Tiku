import React, { useState } from 'react';
import { CoupleProfile } from '../types';
import { Heart, ArrowRight, ArrowLeft, Camera, Sparkles, Check, Calendar, User } from 'lucide-react';
import { compressAvatar } from '../utils/imageUtils';
import confetti from 'canvas-confetti';

interface OnboardingProps {
  initialProfile: CoupleProfile;
  onComplete: (profile: CoupleProfile) => void;
}

export const Onboarding: React.FC<OnboardingProps> = ({ initialProfile, onComplete }) => {
  const [step, setStep] = useState<number>(1);
  const [userName, setUserName] = useState<string>(initialProfile.userName || '');
  const [partnerName, setPartnerName] = useState<string>(initialProfile.partnerName || '');
  const [startDate, setStartDate] = useState<string>(initialProfile.startDate || new Date().toISOString().split('T')[0]);
  const [userPhoto, setUserPhoto] = useState<string>(initialProfile.userPhotoUrl || '');
  const [partnerPhoto, setPartnerPhoto] = useState<string>(initialProfile.partnerPhotoUrl || '');

  const handleNext = () => {
    if (step < 6) {
      setStep(prev => prev + 1);
    } else {
      finishOnboarding();
    }
  };

  const handleBack = () => {
    if (step > 1) {
      setStep(prev => prev - 1);
    }
  };

  const finishOnboarding = async () => {
    confetti({
      particleCount: 100,
      spread: 70,
      origin: { y: 0.6 },
      colors: ['#F47D90', '#FDE2E7', '#FFF0F3', '#EE6A80', '#FFFFFF'],
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
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 bg-[#FFF7F9]">
      <div className="w-full max-w-md glass-panel rounded-3xl p-6 sm:p-8 shadow-[0_12px_32px_rgba(244,125,144,0.15)] border border-[#FDE2E7] relative overflow-hidden flex flex-col justify-between min-h-[520px] bg-white">
        {/* Subtle Background Accent Orbs */}
        <div className="absolute -top-12 -right-12 w-32 h-32 bg-[#FDE2E7]/60 rounded-full blur-2xl pointer-events-none" />
        <div className="absolute -bottom-12 -left-12 w-32 h-32 bg-[#FFF0F3]/80 rounded-full blur-2xl pointer-events-none" />

        {/* Progress Bar Header */}
        <div className="relative z-10 mb-6">
          <div className="flex items-center justify-between text-xs font-semibold text-[#7A5C68] mb-2">
            <span>Step {step} of 6</span>
            <span>{Math.round((step / 6) * 100)}%</span>
          </div>
          <div className="w-full bg-[#FFF0F3] h-2 rounded-full overflow-hidden p-0.5 border border-[#FDE2E7]">
            <div
              className="bg-[#F47D90] h-full rounded-full transition-all duration-300"
              style={{ width: `${(step / 6) * 100}%` }}
            />
          </div>
        </div>

        {/* Step Content */}
        <div className="relative z-10 flex-1 flex flex-col justify-center">
          {/* STEP 1: WELCOME SCREEN */}
          {step === 1 && (
            <div className="text-center space-y-5 animate-fade-in py-4">
              <div className="w-20 h-20 mx-auto bg-gradient-to-br from-[#FFF0F3] to-[#FDE2E7] rounded-full flex items-center justify-center shadow-xs animate-float border border-[#FDE2E7]">
                <Heart className="w-10 h-10 text-[#F47D90] fill-[#F47D90]" />
              </div>
              <div className="space-y-2">
                <h1 className="text-3xl font-bold font-heading text-[#3E2431] flex items-center justify-center gap-1.5">
                  Tiku <Sparkles className="w-5 h-5 text-[#F47D90]" />
                </h1>
                <p className="text-[#7A5C68] text-base font-medium max-w-xs mx-auto leading-relaxed">
                  Your cute, cozy corner to save, organize, and never lose your photo memories.
                </p>
              </div>

              <div className="pt-4">
                <button
                  onClick={handleNext}
                  className="w-full py-4 px-6 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white font-semibold text-base shadow-xs transition-all transform active:scale-95 flex items-center justify-center gap-2 cursor-pointer"
                >
                  <span>Get Started</span>
                  <ArrowRight className="w-5 h-5 text-white" />
                </button>
              </div>
            </div>
          )}

          {/* STEP 2: YOUR NAME */}
          {step === 2 && (
            <div className="space-y-6 animate-fade-in py-2">
              <div className="text-center space-y-2">
                <div className="w-12 h-12 mx-auto rounded-full bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90]">
                  <User className="w-6 h-6" />
                </div>
                <h2 className="text-2xl font-bold font-heading text-[#3E2431]">
                  First, what is your name?
                </h2>
                <p className="text-sm text-[#7A5C68]">So Tiku knows who is saving these cute snaps!</p>
              </div>

              <div className="space-y-2">
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider">
                  Your Name
                </label>
                <input
                  type="text"
                  value={userName}
                  onChange={(e) => setUserName(e.target.value)}
                  placeholder="Enter your name..."
                  autoFocus
                  className="w-full px-5 py-3.5 rounded-full bg-white border border-[#FDE2E7] text-[#3E2431] text-lg font-medium focus:outline-none focus:ring-2 focus:ring-[#F47D90] shadow-xs"
                />
              </div>
            </div>
          )}

          {/* STEP 3: PARTNER'S NAME */}
          {step === 3 && (
            <div className="space-y-6 animate-fade-in py-2">
              <div className="text-center space-y-2">
                <div className="w-12 h-12 mx-auto rounded-full bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90]">
                  <Heart className="w-6 h-6 fill-[#F47D90]" />
                </div>
                <h2 className="text-2xl font-bold font-heading text-[#3E2431]">
                  And who is your partner?
                </h2>
                <p className="text-sm text-[#7A5C68]">The special someone sharing these moments with you.</p>
              </div>

              <div className="space-y-2">
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider">
                  Partner's Name
                </label>
                <input
                  type="text"
                  value={partnerName}
                  onChange={(e) => setPartnerName(e.target.value)}
                  placeholder="Enter partner's name..."
                  autoFocus
                  className="w-full px-5 py-3.5 rounded-full bg-white border border-[#FDE2E7] text-[#3E2431] text-lg font-medium focus:outline-none focus:ring-2 focus:ring-[#F47D90] shadow-xs"
                />
              </div>
            </div>
          )}

          {/* STEP 4: RELATIONSHIP START DATE */}
          {step === 4 && (
            <div className="space-y-6 animate-fade-in py-2">
              <div className="text-center space-y-2">
                <div className="w-12 h-12 mx-auto rounded-full bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90]">
                  <Calendar className="w-6 h-6" />
                </div>
                <h2 className="text-2xl font-bold font-heading text-[#3E2431]">
                  When did your love story start?
                </h2>
                <p className="text-sm text-[#7A5C68]">
                  This powers your automatic "Day X Together" counter!
                </p>
              </div>

              <div className="space-y-2">
                <label className="block text-xs font-bold text-[#7A5C68] uppercase tracking-wider">
                  Relationship Start Date
                </label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-5 py-3.5 rounded-full bg-white border border-[#FDE2E7] text-[#3E2431] text-lg font-medium focus:outline-none focus:ring-2 focus:ring-[#F47D90] shadow-xs cursor-pointer"
                />
              </div>
            </div>
          )}

          {/* STEP 5: PROFILE PHOTOS */}
          {step === 5 && (
            <div className="space-y-5 animate-fade-in py-2">
              <div className="text-center space-y-1">
                <div className="w-12 h-12 mx-auto rounded-full bg-[#FFF0F3] border border-[#FDE2E7] flex items-center justify-center text-[#F47D90]">
                  <Camera className="w-6 h-6" />
                </div>
                <h2 className="text-2xl font-bold font-heading text-[#3E2431]">
                  Add profile avatars
                </h2>
                <p className="text-xs text-[#7A5C68]">
                  Upload photos or select presets for your couple header.
                </p>
              </div>

              {/* Photos Grid */}
              <div className="grid grid-cols-2 gap-4 pt-2">
                {/* User Photo */}
                <div className="flex flex-col items-center gap-2 p-3 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7]">
                  <span className="text-xs font-bold text-[#3E2431]">{userName || 'You'}</span>
                  <div className="relative w-20 h-20 rounded-full overflow-hidden border-2 border-[#F47D90] shadow-xs group bg-[#FFF0F3] flex items-center justify-center">
                    {userPhoto ? (
                      <img
                        src={userPhoto}
                        alt="Your avatar"
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span className="text-xl font-bold text-[#3E2431]">
                        {userName ? userName.charAt(0).toUpperCase() : 'Y'}
                      </span>
                    )}
                    <label className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer text-white">
                      <Camera className="w-5 h-5" />
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setUserPhoto)}
                      />
                    </label>
                  </div>
                  <label className="text-[11px] font-semibold text-[#F47D90] hover:underline cursor-pointer">
                    Upload Photo
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
                  <span className="text-xs font-bold text-[#3E2431]">{partnerName || 'Partner'}</span>
                  <div className="relative w-20 h-20 rounded-full overflow-hidden border-2 border-[#F47D90] shadow-xs group bg-[#FFF0F3] flex items-center justify-center">
                    {partnerPhoto ? (
                      <img
                        src={partnerPhoto}
                        alt="Partner avatar"
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span className="text-xl font-bold text-[#3E2431]">
                        {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
                      </span>
                    )}
                    <label className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer text-white">
                      <Camera className="w-5 h-5" />
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e, setPartnerPhoto)}
                      />
                    </label>
                  </div>
                  <label className="text-[11px] font-semibold text-[#F47D90] hover:underline cursor-pointer">
                    Upload Photo
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => handleFileUpload(e, setPartnerPhoto)}
                    />
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* STEP 6: DONE & CELEBRATE */}
          {step === 6 && (
            <div className="text-center space-y-5 animate-fade-in py-2">
              <div className="w-16 h-16 mx-auto bg-[#FFF0F3] text-[#F47D90] border border-[#FDE2E7] rounded-full flex items-center justify-center shadow-xs">
                <Check className="w-8 h-8 stroke-[3]" />
              </div>

              <div className="space-y-2">
                <h2 className="text-2xl font-bold font-heading text-[#3E2431]">
                  You're all set!
                </h2>
                <p className="text-sm text-[#7A5C68] max-w-xs mx-auto">
                  Welcome, <strong className="text-[#3E2431]">{userName || 'You'}</strong> &{' '}
                  <strong className="text-[#3E2431]">{partnerName || 'Partner'}</strong>! Your cozy memory scrapbook is ready.
                </p>
              </div>

              {/* Preview Avatar Badge */}
              <div className="p-4 rounded-2xl bg-white border border-[#FDE2E7] inline-flex items-center gap-3 shadow-xs mx-auto">
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
                    <div className="w-11 h-11 rounded-full border-2 border-white bg-[#FDE2E7] flex items-center justify-center text-[#F47D90] font-bold shadow-xs">
                      {partnerName ? partnerName.charAt(0).toUpperCase() : 'P'}
                    </div>
                  )}
                </div>
                <div className="text-left">
                  <div className="text-sm font-bold font-heading text-[#3E2431]">
                    {userName || 'You'} & {partnerName || 'Partner'}
                  </div>
                  <div className="text-xs text-[#F47D90] font-semibold flex items-center gap-1">
                    <Sparkles className="w-3 h-3" /> Ready to capture moments
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Navigation Buttons Footer */}
        <div className="relative z-10 pt-6 mt-2 flex items-center justify-between border-t border-[#FDE2E7]">
          {step > 1 ? (
            <button
              onClick={handleBack}
              className="py-2.5 px-4 rounded-full bg-white hover:bg-[#FFF0F3] text-[#7A5C68] text-sm font-semibold flex items-center gap-1 transition-colors border border-[#FDE2E7] cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back</span>
            </button>
          ) : (
            <div />
          )}

          {step > 1 && (
            <button
              onClick={handleNext}
              className="py-2.5 px-6 rounded-full bg-[#F47D90] hover:bg-[#EE6A80] text-white text-sm font-semibold shadow-xs flex items-center gap-2 transition-all transform active:scale-95 cursor-pointer"
            >
              <span>{step === 6 ? 'Enter Tiku' : 'Next'}</span>
              <ArrowRight className="w-4 h-4 text-white" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
