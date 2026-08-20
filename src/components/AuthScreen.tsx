import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  loginWithGoogle,
  loginWithEmail,
  registerWithEmail,
  loginGuest,
  sendPasswordReset,
} from '../lib/firebase';
import {
  Mail,
  KeyRound,
  ArrowRight,
  Eye,
  EyeOff,
  AlertCircle,
  CheckCircle2,
  HelpCircle,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
  Send,
  RefreshCw,
  Sparkles,
} from 'lucide-react';
import { Heart3D, LockedHeart3D, ShieldCheck3D, Sparkles3D } from './Romance3DIcons';

interface AuthScreenProps {
  onSuccess?: () => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = () => {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('abusalim202322@gmail.com');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [resetLoading, setResetLoading] = useState(false);
  const [resetSuccessMsg, setResetSuccessMsg] = useState<string | null>(null);
  const [copiedDomain, setCopiedDomain] = useState(false);
  const [errorMsg, setErrorMsg] = useState<{
    title: string;
    description: string;
    isFirebaseConfigIssue?: boolean;
    isDomainIssue?: boolean;
    isExistingAccount?: boolean;
    isCredentialIssue?: boolean;
    isSignupRestricted?: boolean;
  } | null>(null);
  const [showAdminHelp, setShowAdminHelp] = useState(false);

  // Current domain hostname
  const currentHostname = typeof window !== 'undefined' ? window.location.hostname : 'tiku-memories.vercel.app';

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedDomain(true);
    setTimeout(() => setCopiedDomain(false), 2000);
  };

  // Compute password strength
  const getPasswordStrength = (pass: string) => {
    if (!pass) return { score: 0, text: '', color: 'bg-neutral-200' };
    let score = 0;
    if (pass.length >= 6) score += 1;
    if (pass.length >= 8) score += 1;
    if (/[0-9]/.test(pass) && /[a-zA-Z]/.test(pass)) score += 1;
    if (/[^a-zA-Z0-9]/.test(pass)) score += 1;

    if (score <= 1) return { score: 1, text: 'Weak', color: 'bg-rose-400' };
    if (score === 2) return { score: 2, text: 'Fair', color: 'bg-amber-400' };
    if (score === 3) return { score: 3, text: 'Good', color: 'bg-emerald-400' };
    return { score: 4, text: 'Strong', color: 'bg-[#F47D90]' };
  };

  const strength = getPasswordStrength(password);
  const passwordsMatch = mode === 'signin' || (confirmPassword && password === confirmPassword);

  const parseAuthError = (err: any) => {
    console.error('Auth action error:', err);
    const code = err?.code || '';
    const message = (err?.message || '').toLowerCase();

    if (code === 'auth/unauthorized-domain') {
      return {
        title: 'Domain Not Authorized in Firebase',
        description: `Firebase requires adding "${currentHostname}" to your project's Authorized Domains list before Google Sign-In or OAuth can run on this URL.`,
        isFirebaseConfigIssue: true,
        isDomainIssue: true,
      };
    }

    if (code === 'auth/admin-restricted-operation' || code === 'auth/operation-not-allowed') {
      return {
        title: 'Email Sign-Up Not Enabled in Firebase Console',
        description:
          'Firebase blocked new account creation because Email Sign-Up is disabled. You can still sign in to existing accounts or use Guest Mode.',
        isFirebaseConfigIssue: true,
        isSignupRestricted: true,
      };
    }

    if (message.includes('closing') || message.includes('hidden') || message.includes('indexeddb')) {
      return {
        title: 'Browser Connection Suspended',
        description: 'Your mobile browser tab went to sleep. Tap the button again to continue.',
      };
    }

    if (code === 'auth/email-already-in-use') {
      return {
        title: 'Account Already Exists',
        description: `An account for ${email} already exists. Click "Switch to Sign In" below to unlock your memories!`,
        isExistingAccount: true,
      };
    }

    if (
      code === 'auth/invalid-credential' ||
      code === 'auth/wrong-password' ||
      code === 'auth/user-not-found'
    ) {
      return {
        title: 'Incorrect Email or Password',
        description: 'The password entered did not match. You can reset your password or enter in instant mode.',
        isCredentialIssue: true,
      };
    }

    if (code === 'auth/invalid-email') {
      return {
        title: 'Invalid Email Format',
        description: 'Please provide a valid email address (e.g., name@example.com).',
      };
    }

    if (code === 'auth/weak-password') {
      return {
        title: 'Password Too Short',
        description: 'Firebase requires passwords to have at least 6 characters.',
      };
    }

    if (code === 'auth/popup-closed-by-user') {
      return {
        title: 'Google Sign-In Cancelled',
        description: 'The Google popup was closed before completing sign in. Please tap Continue with Google again.',
      };
    }

    if (code === 'auth/popup-blocked') {
      return {
        title: 'Popup Blocked by Browser',
        description: 'Your mobile browser blocked the Google popup. Please allow popups or use Email Sign In.',
      };
    }

    if (code === 'auth/network-request-failed') {
      return {
        title: 'Network Issue',
        description: 'Could not reach Firebase servers. Please check your internet connection and try again.',
      };
    }

    return {
      title: 'Authentication Notice',
      description: err?.message || 'Something went wrong while connecting to your vault. Please try again.',
    };
  };

  const handleGoogleLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
    setResetSuccessMsg(null);
    try {
      await loginWithGoogle();
    } catch (err: any) {
      if (err?.code !== 'auth/cancelled-popup-request') {
        setErrorMsg(parseAuthError(err));
      }
    } finally {
      setLoading(false);
    }
  };

  const handleEmailAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setResetSuccessMsg(null);

    if (!email.trim() || !password.trim()) {
      setErrorMsg({
        title: 'Missing Fields',
        description: 'Please fill in both email and password to proceed.',
      });
      return;
    }

    if (password.length < 6) {
      setErrorMsg({
        title: 'Password Too Short',
        description: 'Password must be at least 6 characters long.',
      });
      return;
    }

    if (mode === 'signup' && password !== confirmPassword) {
      setErrorMsg({
        title: 'Passwords Do Not Match',
        description: 'Please ensure both password fields match exactly.',
      });
      return;
    }

    setLoading(true);
    try {
      if (mode === 'signup') {
        await registerWithEmail(email, password);
      } else {
        await loginWithEmail(email, password);
      }
    } catch (err: any) {
      setErrorMsg(parseAuthError(err));
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordReset = async () => {
    if (!email.trim()) {
      setErrorMsg({
        title: 'Email Required',
        description: 'Please enter your email address above to receive the password reset link.',
      });
      return;
    }

    setResetLoading(true);
    setErrorMsg(null);
    try {
      await sendPasswordReset(email);
      setResetSuccessMsg(`Password reset link sent to ${email}! Please check your email inbox (and spam folder) to set a new password.`);
    } catch (err: any) {
      setErrorMsg(parseAuthError(err));
    } finally {
      setResetLoading(false);
    }
  };

  const handleGuestLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
    setResetSuccessMsg(null);
    try {
      await loginGuest();
    } catch (err: any) {
      setErrorMsg(parseAuthError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden bg-gradient-to-b from-[#FFF7F9] via-[#FFF0F3]/60 to-[#FAF5F0] text-[#2D1F27]">
      {/* Background Soft Pastel Orbs */}
      <motion.div
        animate={{
          scale: [1, 1.15, 1],
          x: [0, 20, 0],
          y: [0, -20, 0],
        }}
        transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute top-10 left-10 w-96 h-96 bg-[#FDE2E7]/70 rounded-full blur-3xl pointer-events-none -z-10"
      />
      <motion.div
        animate={{
          scale: [1, 1.2, 1],
          x: [0, -20, 0],
          y: [0, 20, 0],
        }}
        transition={{ duration: 12, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="absolute bottom-10 right-10 w-96 h-96 bg-[#FFF0F3] rounded-full blur-3xl pointer-events-none -z-10"
      />

      {/* MAIN AUTHENTICATION CARD */}
      <motion.div
        initial={{ opacity: 0, y: 30, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{
          duration: 0.45,
          ease: [0.16, 1, 0.3, 1],
        }}
        className="w-full max-w-[440px] glass-modal rounded-[36px] p-6 sm:p-8 border border-[#FDE2E7] shadow-[0_24px_60px_rgba(244,125,144,0.15)] space-y-5 relative z-10 text-[#2D1F27] bg-white/95"
      >
        {/* TOP BRANDING & 3D ICON ACCENT */}
        <div className="text-center space-y-3">
          <motion.div
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-[#FFF0F3] text-[#F47D90] text-[11px] font-extrabold border border-[#FDE2E7] shadow-xs"
          >
            <ShieldCheck3D size={18} />
            <span>Private & Encrypted Couple Vault</span>
          </motion.div>

          <div className="relative inline-block my-1">
            <motion.div
              animate={{ scale: [1, 1.06, 1], rotate: [0, -1, 1, 0] }}
              transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
              className="w-20 h-20 rounded-3xl bg-gradient-to-tr from-[#FFF0F3] to-[#FFFFFF] border border-[#FDE2E7] mx-auto flex items-center justify-center shadow-tuchi-glow"
            >
              <Heart3D size={56} />
            </motion.div>
            <motion.div
              animate={{ rotate: [0, 15, -15, 0], scale: [1, 1.15, 1] }}
              transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
              className="absolute -top-2 -right-2"
            >
              <Sparkles3D size={28} />
            </motion.div>
          </div>

          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#2D1F27] tracking-tight">
              Tiku <span className="font-serif-italic font-bold text-[#F47D90]">Memories</span>
            </h1>
            <p className="text-xs sm:text-sm text-[#6B5360] font-medium max-w-xs mx-auto leading-relaxed mt-1">
              Sign in to unlock your shared scrapbook, polaroids, milestones, and daily love notes.
            </p>
          </div>
        </div>

        {/* SUCCESS NOTIFICATION */}
        <AnimatePresence>
          {resetSuccessMsg && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-start gap-2.5 shadow-xs"
            >
              <CheckCircle2 className="w-4 h-4 text-emerald-500 flex-shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-bold text-emerald-900">Email Sent Successfully</p>
                <p className="text-emerald-700 leading-relaxed">{resetSuccessMsg}</p>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* ERROR / NOTICE BANNER */}
        <AnimatePresence mode="wait">
          {errorMsg && (
            <motion.div
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.95 }}
              transition={{ duration: 0.25 }}
              className="p-3.5 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 text-xs space-y-2.5 shadow-xs"
            >
              <div className="flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-rose-500 flex-shrink-0 mt-0.5" />
                <div className="space-y-1 flex-1">
                  <p className="font-bold text-rose-900">{errorMsg.title}</p>
                  <p className="text-rose-700 leading-relaxed">{errorMsg.description}</p>
                </div>
              </div>

              {/* ACTION: Switch to Sign In if account already exists */}
              {errorMsg.isExistingAccount && (
                <div className="pt-2 border-t border-rose-200 flex gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setMode('signin');
                      setErrorMsg(null);
                    }}
                    className="flex-1 py-2 px-3 rounded-xl bg-[#F47D90] text-white hover:bg-[#EE6A80] font-extrabold text-xs flex items-center justify-center gap-1.5 transition-all shadow-tuchi-glow cursor-pointer"
                  >
                    <span>Switch to Sign In & Enter</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}

              {/* ACTION: Send Password Reset if credentials failed */}
              {errorMsg.isCredentialIssue && (
                <div className="pt-2 border-t border-rose-200 flex flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={resetLoading}
                      onClick={handlePasswordReset}
                      className="flex-1 py-2 px-3 rounded-xl bg-[#F47D90] text-white hover:bg-[#EE6A80] font-extrabold text-xs flex items-center justify-center gap-1.5 transition-all shadow-tuchi-glow cursor-pointer disabled:opacity-50"
                    >
                      {resetLoading ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Send className="w-3.5 h-3.5" />
                      )}
                      <span>Send Reset Email</span>
                    </button>
                    <button
                      type="button"
                      onClick={handleGuestLogin}
                      className="py-2 px-3 rounded-xl bg-white border border-[#FDE2E7] text-[#2D1F27] hover:bg-[#FFF0F3] font-bold text-xs cursor-pointer flex-shrink-0"
                    >
                      Enter as Guest
                    </button>
                  </div>
                </div>
              )}

              {/* Copy Hostname Button */}
              {errorMsg.isDomainIssue && (
                <div className="p-2 rounded-xl bg-white border border-rose-200 flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="text-[10px] font-bold text-rose-800 uppercase">Domain to add in Firebase:</p>
                    <p className="text-xs font-mono font-bold text-rose-950 truncate">{currentHostname}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(currentHostname)}
                    className="px-2.5 py-1.5 rounded-lg bg-[#F47D90] text-white hover:bg-[#EE6A80] text-[11px] font-bold flex items-center gap-1 cursor-pointer transition-all flex-shrink-0"
                  >
                    {copiedDomain ? <Check className="w-3 h-3 text-white" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedDomain ? 'Copied!' : 'Copy'}</span>
                  </button>
                </div>
              )}

              {errorMsg.isFirebaseConfigIssue && (
                <div className="pt-2 border-t border-rose-200 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => setShowAdminHelp(!showAdminHelp)}
                      className="text-[11px] font-bold text-[#F47D90] hover:text-[#EE6A80] inline-flex items-center gap-1 cursor-pointer text-left"
                    >
                      <HelpCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      <span>{errorMsg.isDomainIssue ? 'How to authorize domain?' : 'How to enable in Firebase?'}</span>
                      {showAdminHelp ? (
                        <ChevronUp className="w-3 h-3 flex-shrink-0" />
                      ) : (
                        <ChevronDown className="w-3 h-3 flex-shrink-0" />
                      )}
                    </button>

                    <button
                      type="button"
                      onClick={handleGuestLogin}
                      className="text-[11px] font-extrabold px-2.5 py-1 rounded-lg bg-[#F47D90] text-white hover:bg-[#EE6A80] cursor-pointer shadow-tuchi-glow flex-shrink-0"
                    >
                      Enter as Guest
                    </button>
                  </div>

                  <AnimatePresence>
                    {showAdminHelp && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        className="bg-white p-2.5 rounded-xl border border-rose-200 text-[11px] text-[#6B5360] space-y-1.5"
                      >
                        {errorMsg.isDomainIssue ? (
                          <>
                            <p className="font-bold text-[#2D1F27]">To authorize Google Sign-In:</p>
                            <ol className="list-decimal pl-4 space-y-1">
                              <li>Go to <b>console.firebase.google.com</b> &gt; Project <b>gen-lang-client-0274845526</b></li>
                              <li>Click <b>Authentication</b> &gt; <b>Settings</b> tab</li>
                              <li>Scroll to <b>Authorized domains</b> &gt; Click <b>Add domain</b></li>
                              <li>Paste: <code className="bg-[#FFF0F3] px-1 py-0.5 rounded font-mono text-[10px] text-[#F47D90]">{currentHostname}</code></li>
                              <li>Click <b>Save</b> and retry Google login</li>
                            </ol>
                          </>
                        ) : (
                          <>
                            <p className="font-bold text-[#2D1F27]">To enable Email Sign-Up in Firebase:</p>
                            <ol className="list-decimal pl-4 space-y-1 text-[11px]">
                              <li>Go to <b>console.firebase.google.com</b> &gt; Project <b>gen-lang-client-0274845526</b></li>
                              <li>Click <b>Authentication</b> &gt; <b>Sign-in method</b> tab &gt; Click <b>Email/Password</b> &gt; Turn on <b>Enable</b> &gt; Click <b>Save</b></li>
                              <li>Next, click the <b>Settings</b> tab &gt; Scroll to <b>User actions</b> &gt; Ensure <b>"Enable create (sign-up)"</b> is checked &gt; Click <b>Save</b></li>
                              <li>Return here and click Create Vault Account or Sign In!</li>
                            </ol>
                          </>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {/* GOOGLE SIGN IN BUTTON */}
        <div className="space-y-3">
          <motion.button
            whileHover={{ scale: 1.015, y: -1 }}
            whileTap={{ scale: 0.985 }}
            type="button"
            disabled={loading}
            onClick={handleGoogleLogin}
            className="w-full py-3.5 px-4 rounded-2xl bg-white hover:bg-[#FFF0F3] text-[#2D1F27] text-xs sm:text-sm font-bold border border-[#FDE2E7] shadow-xs flex items-center justify-center gap-3 transition-all cursor-pointer disabled:opacity-50 group"
          >
            <svg className="w-4 h-4 transition-transform group-hover:scale-110" viewBox="0 0 24 24">
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
          <div className="flex items-center gap-3 my-1">
            <div className="flex-1 h-px bg-[#FDE2E7]" />
            <span className="text-[10px] font-extrabold text-[#9E8592] uppercase tracking-wider">
              or continue with email
            </span>
            <div className="flex-1 h-px bg-[#FDE2E7]" />
          </div>
        </div>

        {/* TAB TOGGLE: SIGN IN / SIGN UP */}
        <div className="relative flex rounded-2xl bg-[#FFF0F3] p-1 border border-[#FDE2E7]">
          <button
            type="button"
            onClick={() => {
              setMode('signin');
              setErrorMsg(null);
            }}
            className={`relative flex-1 py-2.5 text-xs font-extrabold rounded-xl transition-colors cursor-pointer z-10 ${
              mode === 'signin' ? 'text-white' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {mode === 'signin' && (
              <motion.div
                layoutId="activeAuthTab"
                transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                className="absolute inset-0 bg-[#F47D90] rounded-xl shadow-tuchi-glow -z-10"
              />
            )}
            <span>Sign In</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('signup');
              setErrorMsg(null);
            }}
            className={`relative flex-1 py-2.5 text-xs font-extrabold rounded-xl transition-colors cursor-pointer z-10 ${
              mode === 'signup' ? 'text-white' : 'text-[#6B5360] hover:text-[#2D1F27]'
            }`}
          >
            {mode === 'signup' && (
              <motion.div
                layoutId="activeAuthTab"
                transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                className="absolute inset-0 bg-[#F47D90] rounded-xl shadow-tuchi-glow -z-10"
              />
            )}
            <span>Create Account</span>
          </button>
        </div>

        {/* EMAIL & PASSWORD FORM */}
        <form onSubmit={handleEmailAuth} className="space-y-3">
          {/* EMAIL FIELD */}
          <div className="space-y-1">
            <label className="block text-xs font-bold text-[#6B5360] flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Mail className="w-3.5 h-3.5 text-[#F47D90]" /> Email Address
              </span>
            </label>
            <div className="relative">
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="w-full px-4 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#F47D90] transition-all placeholder:text-[#9E8592]/50 shadow-xs"
              />
            </div>
          </div>

          {/* PASSWORD FIELD */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-[#6B5360] flex items-center gap-1.5">
                <KeyRound className="w-3.5 h-3.5 text-[#F47D90]" /> Password
              </label>
              {mode === 'signin' ? (
                <button
                  type="button"
                  disabled={resetLoading}
                  onClick={handlePasswordReset}
                  className="text-[11px] font-extrabold text-[#F47D90] hover:text-[#EE6A80] transition-colors cursor-pointer disabled:opacity-50"
                >
                  {resetLoading ? 'Sending link...' : 'Forgot password?'}
                </button>
              ) : (
                password && (
                  <span className="text-[10px] font-bold text-[#F47D90]">
                    {strength.text}
                  </span>
                )
              )}
            </div>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'signup' ? 'Create password (min 6 chars)' : 'Enter your password'}
                className="w-full pl-4 pr-10 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#F47D90] transition-all placeholder:text-[#9E8592]/50 shadow-xs"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-[#9E8592] hover:text-[#2D1F27] transition-colors p-1 cursor-pointer"
                title={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {mode === 'signup' && password.length > 0 && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                className="pt-1 flex items-center gap-1.5"
              >
                <div className="flex-1 grid grid-cols-4 gap-1 h-1.5 rounded-full overflow-hidden bg-[#FFF0F3]">
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 1 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 2 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 3 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 4 ? strength.color : 'bg-transparent'}`} />
                </div>
              </motion.div>
            )}
          </div>

          {/* CONFIRM PASSWORD (SIGN UP ONLY) */}
          <AnimatePresence>
            {mode === 'signup' && (
              <motion.div
                initial={{ opacity: 0, height: 0, y: -6 }}
                animate={{ opacity: 1, height: 'auto', y: 0 }}
                exit={{ opacity: 0, height: 0, y: -6 }}
                transition={{ duration: 0.2 }}
                className="space-y-1 overflow-hidden"
              >
                <label className="block text-xs font-bold text-[#6B5360] flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <CheckCircle2 className="w-3.5 h-3.5 text-[#F47D90]" /> Confirm Password
                  </span>
                  {confirmPassword && (
                    <span className={`text-[10px] font-bold ${passwordsMatch ? 'text-emerald-600' : 'text-rose-500'}`}>
                      {passwordsMatch ? '✓ Matches' : '✗ Does not match'}
                    </span>
                  )}
                </label>
                <div className="relative">
                  <input
                    type={showConfirmPassword ? 'text' : 'password'}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Repeat your password"
                    className="w-full pl-4 pr-10 py-2.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] text-[#2D1F27] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#F47D90] transition-all placeholder:text-[#9E8592]/50 shadow-xs"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#9E8592] hover:text-[#2D1F27] transition-colors p-1 cursor-pointer"
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* SUBMIT BUTTON */}
          <motion.button
            whileHover={{ scale: 1.015, y: -1 }}
            whileTap={{ scale: 0.985 }}
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-xs sm:text-sm font-extrabold shadow-tuchi-glow flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-50"
          >
            {loading ? (
              <div className="flex items-center gap-2">
                <span className="inline-block animate-spin w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
                <span>Connecting to Vault...</span>
              </div>
            ) : mode === 'signin' ? (
              <>
                <LockedHeart3D size={20} />
                <span>Unlock My Scrapbook</span>
              </>
            ) : (
              <>
                <Heart3D size={20} />
                <span>Create New Memory Vault</span>
              </>
            )}
          </motion.button>
        </form>

        {/* GUEST MODE OPTION */}
        <div className="pt-2 text-center border-t border-[#FDE2E7] flex flex-col items-center gap-1">
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="button"
            disabled={loading}
            onClick={handleGuestLogin}
            className="text-xs font-extrabold text-[#F47D90] hover:text-[#EE6A80] transition-colors inline-flex items-center gap-1.5 cursor-pointer py-1.5 px-3 rounded-xl hover:bg-[#FFF0F3]"
          >
            <Sparkles className="w-3.5 h-3.5 text-[#F47D90]" />
            <span>Try Instant Guest Mode</span>
            <ArrowRight className="w-3 h-3" />
          </motion.button>
          <span className="text-[10px] text-[#9E8592] font-medium">
            Jump in immediately without entering an email or password
          </span>
        </div>
      </motion.div>
    </div>
  );
};
