import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { loginWithGoogle, loginWithEmail, registerWithEmail, loginGuest } from '../lib/firebase';
import { Lock, Heart, ShieldCheck, Mail, KeyRound, Sparkles, ArrowRight, UserCheck } from 'lucide-react';

interface AuthScreenProps {
  onSuccess?: () => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = () => {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleGoogleLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      await loginWithGoogle();
    } catch (err: any) {
      console.error('Google sign in error:', err);
      if (err?.code === 'auth/popup-closed-by-user') {
        setErrorMsg('Sign in popup was closed. Please try again.');
      } else if (err?.code === 'auth/cancelled-popup-request') {
        setErrorMsg(null);
      } else {
        setErrorMsg(err?.message || 'Failed to sign in with Google');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleEmailAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      setErrorMsg('Please enter both email and password');
      return;
    }
    if (password.length < 6) {
      setErrorMsg('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    setErrorMsg(null);
    try {
      if (mode === 'signup') {
        await registerWithEmail(email, password);
      } else {
        await loginWithEmail(email, password);
      }
    } catch (err: any) {
      console.error('Auth error:', err);
      if (err?.code === 'auth/email-already-in-use') {
        setErrorMsg('This email is already registered. Please switch to Sign In.');
      } else if (err?.code === 'auth/invalid-credential' || err?.code === 'auth/wrong-password' || err?.code === 'auth/user-not-found') {
        setErrorMsg('Invalid email or password. Please check your credentials.');
      } else if (err?.code === 'auth/invalid-email') {
        setErrorMsg('Please enter a valid email address.');
      } else {
        setErrorMsg(err?.message || 'Authentication failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGuestLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      await loginGuest();
    } catch (err: any) {
      console.error('Guest login error:', err);
      setErrorMsg(err?.message || 'Failed to enter demo vault');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden">
      {/* Decorative ambient background blobs */}
      <div className="absolute top-1/4 left-1/4 w-80 h-80 bg-[#FFCCD5]/40 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute bottom-1/4 right-1/4 w-80 h-80 bg-[#BFE3D0]/35 rounded-full blur-3xl pointer-events-none -z-10" />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-md glass-modal rounded-[36px] p-6 sm:p-9 border border-white/95 shadow-tuchi-lg space-y-6 relative z-10 glossy-top"
      >
        {/* TOP BRANDING & PRIVACY SHIELD */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-white/90 text-[#8C3F5D] text-xs font-extrabold border border-[#FFCCD5] shadow-xs">
            <ShieldCheck className="w-4 h-4 text-[#FF4B72]" />
            <span>100% Private & Protected Vault</span>
          </div>

          <div className="w-16 h-16 rounded-3xl bg-gradient-to-tr from-[#FFE5EC] via-[#FFF0F3] to-[#FFD8E3] border border-white mx-auto flex items-center justify-center shadow-xs">
            <Heart className="w-8 h-8 fill-[#FF4B72] text-[#FF4B72] animate-pulse-soft" />
          </div>

          <h1 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#3E2431] tracking-tight">
            Our Private Scrapbook
          </h1>
          <p className="text-xs sm:text-sm text-[#6E495B] font-medium max-w-xs mx-auto leading-relaxed">
            Sign in to unlock your personal couple memory vault. Your photos and dates are private and visible only to you.
          </p>
        </div>

        {/* ERROR MESSAGE DISPLAY */}
        <AnimatePresence>
          {errorMsg && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="p-3 rounded-2xl bg-red-50/90 border border-red-200 text-red-600 text-xs font-semibold text-center"
            >
              {errorMsg}
            </motion.div>
          )}
        </AnimatePresence>

        {/* GOOGLE SIGN IN BUTTON */}
        <div className="space-y-3">
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="button"
            disabled={loading}
            onClick={handleGoogleLogin}
            className="w-full py-3.5 px-4 rounded-2xl bg-white hover:bg-neutral-50 text-[#3E2431] text-xs sm:text-sm font-bold border border-neutral-200/90 shadow-xs flex items-center justify-center gap-3 transition-all cursor-pointer disabled:opacity-50"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            <span>Continue with Google</span>
          </motion.button>

          {/* DIVIDER */}
          <div className="flex items-center gap-3 my-2">
            <div className="flex-1 h-px bg-[#FFCCD5]/80" />
            <span className="text-[11px] font-bold text-[#6E495B]/70 uppercase tracking-wider">or with email</span>
            <div className="flex-1 h-px bg-[#FFCCD5]/80" />
          </div>
        </div>

        {/* TAB TOGGLE: SIGN IN / SIGN UP */}
        <div className="flex rounded-2xl bg-[#FFF0F3] p-1 border border-[#FFCCD5]/60">
          <button
            type="button"
            onClick={() => {
              setMode('signin');
              setErrorMsg(null);
            }}
            className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all ${
              mode === 'signin'
                ? 'bg-white text-[#3E2431] shadow-xs'
                : 'text-[#6E495B] hover:text-[#3E2431]'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('signup');
              setErrorMsg(null);
            }}
            className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all ${
              mode === 'signup'
                ? 'bg-white text-[#3E2431] shadow-xs'
                : 'text-[#6E495B] hover:text-[#3E2431]'
            }`}
          >
            Create Vault Account
          </button>
        </div>

        {/* EMAIL & PASSWORD FORM */}
        <form onSubmit={handleEmailAuth} className="space-y-3.5">
          <div>
            <label className="block text-xs font-bold text-[#6E495B] mb-1 flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5 text-[#8C3F5D]" /> Email Address
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full px-4 py-2.5 rounded-2xl bg-white/90 border border-[#FFCCD5] text-[#3E2431] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] transition-all"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-[#6E495B] mb-1 flex items-center gap-1.5">
              <KeyRound className="w-3.5 h-3.5 text-[#8C3F5D]" /> Password
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 6 characters"
              className="w-full px-4 py-2.5 rounded-2xl bg-white/90 border border-[#FFCCD5] text-[#3E2431] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] transition-all"
            />
          </div>

          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-[#3E2431] via-[#6E495B] to-[#FF4B72] text-white text-xs sm:text-sm font-extrabold shadow-tuchi flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-50"
          >
            {loading ? (
              <span className="inline-block animate-spin w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
            ) : mode === 'signin' ? (
              <>
                <Lock className="w-4 h-4" />
                <span>Unlock My Scrapbook</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4" />
                <span>Create New Memory Vault</span>
              </>
            )}
          </motion.button>
        </form>

        {/* GUEST MODE OPTION */}
        <div className="pt-2 text-center border-t border-[#FFCCD5]/50">
          <button
            type="button"
            disabled={loading}
            onClick={handleGuestLogin}
            className="text-xs font-semibold text-[#8C3F5D] hover:text-[#3E2431] transition-colors inline-flex items-center gap-1 cursor-pointer"
          >
            <UserCheck className="w-3.5 h-3.5" />
            <span>Try Quick Private Guest Mode</span>
            <ArrowRight className="w-3 h-3" />
          </button>
        </div>
      </motion.div>
    </div>
  );
};
