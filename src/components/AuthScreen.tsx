import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  loginWithGoogle,
  loginWithEmail,
  registerWithEmail,
  loginGuest,
} from '../lib/firebase';
import {
  Lock,
  Heart,
  ShieldCheck,
  Mail,
  KeyRound,
  Sparkles,
  ArrowRight,
  UserCheck,
  Eye,
  EyeOff,
  AlertCircle,
  CheckCircle2,
  HelpCircle,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

interface AuthScreenProps {
  onSuccess?: () => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = () => {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [copiedDomain, setCopiedDomain] = useState(false);
  const [errorMsg, setErrorMsg] = useState<{
    title: string;
    description: string;
    isFirebaseConfigIssue?: boolean;
    isDomainIssue?: boolean;
  } | null>(null);
  const [showAdminHelp, setShowAdminHelp] = useState(false);

  // Current domain hostname
  const currentHostname = typeof window !== 'undefined' ? window.location.hostname : 'your-domain.com';

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

    if (score <= 1) return { score: 1, text: 'Weak', color: 'bg-red-400' };
    if (score === 2) return { score: 2, text: 'Fair', color: 'bg-amber-400' };
    if (score === 3) return { score: 3, text: 'Good', color: 'bg-emerald-400' };
    return { score: 4, text: 'Strong', color: 'bg-[#FF4B72]' };
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
          'Firebase blocked account creation because Email/Password sign-up is turned off in your Firebase project. Enable Email/Password in Authentication > Sign-in method and check "Enable create (sign-up)" in Settings > User actions.',
        isFirebaseConfigIssue: true,
        isSignupRestricted: true,
      };
    }

    if (message.includes('closing') || message.includes('hidden') || message.includes('indexeddb')) {
      return {
        title: 'Browser Connection Suspended',
        description: 'Your mobile browser tab went to sleep or closed the local cache. Simply tap the button again or reload the page to continue.',
      };
    }

    if (code === 'auth/email-already-in-use') {
      return {
        title: 'Account Already Exists',
        description: 'This email is already registered. Please click "Sign In" above to unlock your memories.',
      };
    }

    if (
      code === 'auth/invalid-credential' ||
      code === 'auth/wrong-password' ||
      code === 'auth/user-not-found'
    ) {
      return {
        title: 'Incorrect Credentials',
        description: 'Invalid email or password. Please double-check your password or click Create Vault Account.',
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
        description: 'Firebase requires your password to have at least 6 characters.',
      };
    }

    if (code === 'auth/popup-closed-by-user') {
      return {
        title: 'Google Sign-In Cancelled',
        description: 'The Google popup was closed before signing in. Please tap Continue with Google again.',
      };
    }

    if (code === 'auth/network-request-failed') {
      return {
        title: 'Network Issue',
        description: 'Could not reach Firebase servers. Please check your internet connection and try again.',
      };
    }

    return {
      title: 'Authentication Error',
      description: err?.message || 'Something went wrong while connecting to your vault. Please try again.',
    };
  };

  const handleGoogleLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
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

  const handleGuestLogin = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      await loginGuest();
    } catch (err: any) {
      setErrorMsg(parseAuthError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden bg-gradient-to-br from-[#FFF5F7] via-[#FFF0F3] to-[#F7EBEF]">
      {/* Floating Animated Background Hearts & Glows */}
      <motion.div
        animate={{
          scale: [1, 1.15, 1],
          x: [0, 15, 0],
          y: [0, -20, 0],
        }}
        transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute top-12 left-12 w-72 h-72 bg-[#FFCCD5]/50 rounded-full blur-3xl pointer-events-none -z-10"
      />
      <motion.div
        animate={{
          scale: [1, 1.2, 1],
          x: [0, -20, 0],
          y: [0, 20, 0],
        }}
        transition={{ duration: 12, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="absolute bottom-12 right-12 w-80 h-80 bg-[#BFE3D0]/40 rounded-full blur-3xl pointer-events-none -z-10"
      />
      <motion.div
        animate={{
          scale: [1, 1.1, 1],
          opacity: [0.3, 0.6, 0.3],
        }}
        transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut', delay: 2 }}
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-[#FFE5EC]/45 rounded-full blur-3xl pointer-events-none -z-10"
      />

      {/* Floating Mini Hearts */}
      <motion.div
        animate={{ y: [-10, 10, -10], rotate: [0, 10, 0] }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
        className="hidden md:block absolute top-20 left-[15%] text-2xl text-[#FFCCD5] select-none pointer-events-none"
      >
        🌸
      </motion.div>
      <motion.div
        animate={{ y: [10, -10, 10], rotate: [0, -12, 0] }}
        transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="hidden md:block absolute bottom-24 left-[12%] text-3xl text-[#FF4B72]/30 select-none pointer-events-none"
      >
        💖
      </motion.div>
      <motion.div
        animate={{ y: [-15, 15, -15], rotate: [0, 15, 0] }}
        transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut', delay: 0.5 }}
        className="hidden md:block absolute top-28 right-[15%] text-2xl text-[#E8AFC0] select-none pointer-events-none"
      >
        ✨
      </motion.div>
      <motion.div
        animate={{ y: [12, -12, 12], rotate: [0, -10, 0] }}
        transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut', delay: 2 }}
        className="hidden md:block absolute bottom-28 right-[14%] text-3xl text-[#FFCCD5] select-none pointer-events-none"
      >
        🎀
      </motion.div>

      {/* MAIN AUTHENTICATION CARD */}
      <motion.div
        initial={{ opacity: 0, y: 30, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{
          duration: 0.45,
          ease: [0.16, 1, 0.3, 1],
        }}
        className="w-full max-w-[440px] bg-white/85 backdrop-blur-xl rounded-[36px] p-6 sm:p-8 border border-white/95 shadow-tuchi-lg space-y-6 relative z-10 glossy-top"
      >
        {/* TOP BRANDING & BADGE */}
        <div className="text-center space-y-3">
          <motion.div
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full bg-gradient-to-r from-[#FFF0F3] to-[#FFE5EC] text-[#8C3F5D] text-[11px] font-extrabold border border-[#FFCCD5] shadow-xs"
          >
            <ShieldCheck className="w-3.5 h-3.5 text-[#FF4B72]" />
            <span>Private & Encrypted Couple Vault</span>
          </motion.div>

          <div className="relative inline-block">
            <motion.div
              animate={{ scale: [1, 1.06, 1] }}
              transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
              className="w-16 h-16 rounded-3xl bg-gradient-to-tr from-[#FFE5EC] via-[#FFF0F3] to-[#FFD8E3] border border-white mx-auto flex items-center justify-center shadow-xs"
            >
              <Heart className="w-8 h-8 fill-[#FF4B72] text-[#FF4B72] drop-shadow-xs" />
            </motion.div>
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
              className="absolute -top-1 -right-1 text-sm select-none"
            >
              ✨
            </motion.div>
          </div>

          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold font-heading text-[#3E2431] tracking-tight">
              Our Private Scrapbook
            </h1>
            <p className="text-xs sm:text-sm text-[#6E495B] font-medium max-w-xs mx-auto leading-relaxed mt-1">
              Sign in to unlock your shared memories, photos, milestones, and love notes.
            </p>
          </div>
        </div>

        {/* ERROR / NOTICE BANNER WITH INTELLIGENT ADVICE */}
        <AnimatePresence mode="wait">
          {errorMsg && (
            <motion.div
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.95 }}
              transition={{ duration: 0.25 }}
              className="p-3.5 rounded-2xl bg-red-50/95 border border-red-200 text-[#3E2431] text-xs space-y-2.5 shadow-xs"
            >
              <div className="flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <p className="font-bold text-red-700">{errorMsg.title}</p>
                  <p className="text-red-600/90 leading-relaxed">{errorMsg.description}</p>
                </div>
              </div>

              {/* Copy Hostname Button for Unauthorized Domain issue */}
              {errorMsg.isDomainIssue && (
                <div className="p-2 rounded-xl bg-white/95 border border-red-200 flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="text-[10px] font-bold text-neutral-500 uppercase">Domain to add:</p>
                    <p className="text-xs font-mono font-bold text-[#3E2431] truncate">{currentHostname}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(currentHostname)}
                    className="px-2.5 py-1.5 rounded-lg bg-[#3E2431] text-white hover:bg-[#8C3F5D] text-[11px] font-bold flex items-center gap-1 cursor-pointer transition-all flex-shrink-0"
                  >
                    {copiedDomain ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedDomain ? 'Copied!' : 'Copy'}</span>
                  </button>
                </div>
              )}

              {errorMsg.isFirebaseConfigIssue && (
                <div className="pt-2 border-t border-red-200/70 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => setShowAdminHelp(!showAdminHelp)}
                      className="text-[11px] font-bold text-[#8C3F5D] hover:text-[#3E2431] inline-flex items-center gap-1 cursor-pointer text-left"
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
                      className="text-[11px] font-extrabold px-2.5 py-1 rounded-lg bg-[#FF4B72] text-white hover:bg-[#E83E63] cursor-pointer shadow-xs flex-shrink-0"
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
                        className="bg-white/90 p-2.5 rounded-xl border border-red-200 text-[11px] text-[#6E495B] space-y-1.5"
                      >
                        {errorMsg.isDomainIssue ? (
                          <>
                            <p className="font-bold text-[#3E2431]">To authorize this domain:</p>
                            <ol className="list-decimal pl-4 space-y-1">
                              <li>Go to <b>console.firebase.google.com</b> &gt; Project <b>gen-lang-client-0274845526</b></li>
                              <li>Click <b>Authentication</b> &gt; <b>Settings</b> tab</li>
                              <li>Scroll to <b>Authorized domains</b> &gt; Click <b>Add domain</b></li>
                              <li>Paste: <code className="bg-neutral-100 px-1 py-0.5 rounded font-mono text-[10px]">{currentHostname}</code></li>
                              <li>Click <b>Save</b> and retry Google login</li>
                            </ol>
                          </>
                        ) : (
                          <>
                            <p className="font-bold text-[#3E2431]">To enable Email Sign-Up in Firebase:</p>
                            <ol className="list-decimal pl-4 space-y-1 text-[11px]">
                              <li>Go to <b>console.firebase.google.com</b> &gt; Project <b>gen-lang-client-0274845526</b></li>
                              <li>Click <b>Authentication</b> &gt; <b>Sign-in method</b> tab &gt; Click <b>Email/Password</b> &gt; Turn on <b>Enable</b> &gt; Click <b>Save</b></li>
                              <li>Next, click the <b>Settings</b> tab &gt; Scroll to <b>User actions</b> &gt; Ensure <b>"Enable create (sign-up)"</b> is checked &gt; Click <b>Save</b></li>
                              <li>Return here and click Create Vault Account or Sign In!</li>
                            </ol>
                          </>
                        )}
                        <p className="text-[10px] text-neutral-500 italic mt-1">
                          You can also click "Enter as Guest" to use the scrapbook immediately.
                        </p>
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
            className="w-full py-3.5 px-4 rounded-2xl bg-white hover:bg-neutral-50/90 text-[#3E2431] text-xs sm:text-sm font-bold border border-neutral-200/90 shadow-xs flex items-center justify-center gap-3 transition-all cursor-pointer disabled:opacity-50 group"
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
            <div className="flex-1 h-px bg-[#FFCCD5]/70" />
            <span className="text-[10px] font-extrabold text-[#8C3F5D]/70 uppercase tracking-wider">
              or continue with email
            </span>
            <div className="flex-1 h-px bg-[#FFCCD5]/70" />
          </div>
        </div>

        {/* SMOOTH SLIDING TAB TOGGLE: SIGN IN / SIGN UP */}
        <div className="relative flex rounded-2xl bg-[#FFF0F3] p-1 border border-[#FFCCD5]/60">
          <button
            type="button"
            onClick={() => {
              setMode('signin');
              setErrorMsg(null);
            }}
            className={`relative flex-1 py-2 text-xs font-extrabold rounded-xl transition-colors cursor-pointer z-10 ${
              mode === 'signin' ? 'text-[#3E2431]' : 'text-[#8C3F5D] hover:text-[#3E2431]'
            }`}
          >
            {mode === 'signin' && (
              <motion.div
                layoutId="activeAuthTab"
                transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                className="absolute inset-0 bg-white rounded-xl shadow-xs -z-10 border border-white"
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
            className={`relative flex-1 py-2 text-xs font-extrabold rounded-xl transition-colors cursor-pointer z-10 ${
              mode === 'signup' ? 'text-[#3E2431]' : 'text-[#8C3F5D] hover:text-[#3E2431]'
            }`}
          >
            {mode === 'signup' && (
              <motion.div
                layoutId="activeAuthTab"
                transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                className="absolute inset-0 bg-white rounded-xl shadow-xs -z-10 border border-white"
              />
            )}
            <span>Create Vault Account</span>
          </button>
        </div>

        {/* ANIMATED EMAIL & PASSWORD FORM */}
        <form onSubmit={handleEmailAuth} className="space-y-3.5">
          {/* EMAIL FIELD */}
          <div className="space-y-1">
            <label className="block text-xs font-bold text-[#6E495B] flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Mail className="w-3.5 h-3.5 text-[#FF4B72]" /> Email Address
              </span>
            </label>
            <div className="relative">
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="w-full px-4 py-2.5 rounded-2xl bg-white/95 border border-[#FFCCD5] text-[#3E2431] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] transition-all placeholder:text-neutral-400 shadow-xs"
              />
            </div>
          </div>

          {/* PASSWORD FIELD WITH VISIBILITY TOGGLE */}
          <div className="space-y-1">
            <label className="block text-xs font-bold text-[#6E495B] flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <KeyRound className="w-3.5 h-3.5 text-[#FF4B72]" /> Password
              </span>
              {mode === 'signup' && password && (
                <span className="text-[10px] font-bold text-[#8C3F5D]">
                  {strength.text}
                </span>
              )}
            </label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'signup' ? 'Create a strong password (min 6 chars)' : 'Enter your password'}
                className="w-full pl-4 pr-10 py-2.5 rounded-2xl bg-white/95 border border-[#FFCCD5] text-[#3E2431] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] transition-all placeholder:text-neutral-400 shadow-xs"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8C3F5D] hover:text-[#3E2431] transition-colors p-1 cursor-pointer"
                title={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {/* Password strength meter in Sign Up mode */}
            {mode === 'signup' && password.length > 0 && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                className="pt-1 flex items-center gap-1.5"
              >
                <div className="flex-1 grid grid-cols-4 gap-1 h-1.5 rounded-full overflow-hidden bg-neutral-200">
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 1 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 2 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 3 ? strength.color : 'bg-transparent'}`} />
                  <div className={`h-full rounded-full transition-all duration-300 ${strength.score >= 4 ? strength.color : 'bg-transparent'}`} />
                </div>
              </motion.div>
            )}
          </div>

          {/* CONFIRM PASSWORD FIELD (ONLY ON SIGN UP) */}
          <AnimatePresence>
            {mode === 'signup' && (
              <motion.div
                initial={{ opacity: 0, height: 0, y: -6 }}
                animate={{ opacity: 1, height: 'auto', y: 0 }}
                exit={{ opacity: 0, height: 0, y: -6 }}
                transition={{ duration: 0.2 }}
                className="space-y-1 overflow-hidden"
              >
                <label className="block text-xs font-bold text-[#6E495B] flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <CheckCircle2 className="w-3.5 h-3.5 text-[#FF4B72]" /> Confirm Password
                  </span>
                  {confirmPassword && (
                    <span className={`text-[10px] font-bold ${passwordsMatch ? 'text-emerald-600' : 'text-red-500'}`}>
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
                    className="w-full pl-4 pr-10 py-2.5 rounded-2xl bg-white/95 border border-[#FFCCD5] text-[#3E2431] text-xs sm:text-sm font-medium focus:outline-none focus:border-[#FF4B72] focus:ring-2 focus:ring-[#FFE5EC] transition-all placeholder:text-neutral-400 shadow-xs"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8C3F5D] hover:text-[#3E2431] transition-colors p-1 cursor-pointer"
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* SUBMIT BUTTON WITH HOVER/TAP SPRING TRANSITIONS */}
          <motion.button
            whileHover={{ scale: 1.02, y: -1 }}
            whileTap={{ scale: 0.98 }}
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-[#3E2431] via-[#6E495B] to-[#FF4B72] text-white text-xs sm:text-sm font-extrabold shadow-tuchi flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-50 border border-white/20"
          >
            {loading ? (
              <div className="flex items-center gap-2">
                <span className="inline-block animate-spin w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
                <span>Connecting to Vault...</span>
              </div>
            ) : mode === 'signin' ? (
              <>
                <Lock className="w-4 h-4" />
                <span>Unlock My Scrapbook</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4 text-yellow-300" />
                <span>Create New Memory Vault</span>
              </>
            )}
          </motion.button>
        </form>

        {/* GUEST MODE OPTION */}
        <div className="pt-2 text-center border-t border-[#FFCCD5]/50 flex flex-col items-center gap-1.5">
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="button"
            disabled={loading}
            onClick={handleGuestLogin}
            className="text-xs font-extrabold text-[#8C3F5D] hover:text-[#3E2431] transition-colors inline-flex items-center gap-1.5 cursor-pointer py-1.5 px-3 rounded-xl hover:bg-white/60"
          >
            <UserCheck className="w-3.5 h-3.5 text-[#FF4B72]" />
            <span>Try Instant Guest Mode</span>
            <ArrowRight className="w-3 h-3" />
          </motion.button>
          <span className="text-[10px] text-[#6E495B]/70 font-medium">
            Jump in immediately without entering an email or password
          </span>
        </div>
      </motion.div>
    </div>
  );
};
