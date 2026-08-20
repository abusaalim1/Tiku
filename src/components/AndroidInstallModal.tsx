import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Smartphone,
  Download,
  CheckCircle2,
  Share2,
  MoreVertical,
  X,
  ShieldCheck,
  Zap,
} from 'lucide-react';
import { Sparkles3D, Heart3D } from './Romance3DIcons';

interface AndroidInstallModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidInstallModal: React.FC<AndroidInstallModalProps> = ({
  isOpen,
  onClose,
}) => {
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [installSuccess, setInstallSuccess] = useState(false);

  useEffect(() => {
    if (
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as any).standalone === true
    ) {
      setIsInstalled(true);
    }

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e);
    };

    const handleAppInstalled = () => {
      setIsInstalled(true);
      setInstallSuccess(true);
      setDeferredPrompt(null);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  const handleInstallClick = async () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      if (outcome === 'accepted') {
        setInstallSuccess(true);
      }
      setDeferredPrompt(null);
    } else {
      if (navigator.share) {
        try {
          await navigator.share({
            title: 'Tiku — Couple Memory Space',
            text: 'Check out our private couple memory space app!',
            url: window.location.href,
          });
        } catch (err) {
          // fallback
        }
      }
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 15 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 15 }}
          className="w-full max-w-lg glass-modal rounded-[36px] shadow-[0_24px_60px_rgba(244,125,144,0.2)] border border-[#FDE2E7] overflow-hidden flex flex-col max-h-[90vh] text-[#2D1F27] bg-white"
        >
          {/* HEADER */}
          <div className="p-5 sm:p-6 border-b border-[#FDE2E7] flex items-center justify-between bg-[#FFF7F9]">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-[#FFF0F3] to-white border border-[#FDE2E7] flex items-center justify-center shadow-xs">
                <Heart3D size={24} />
              </div>
              <div>
                <h3 className="text-lg font-bold font-heading text-[#2D1F27]">
                  Install Tiku on <span className="font-serif-italic font-bold text-[#F47D90]">Android</span>
                </h3>
                <p className="text-xs text-[#6B5360]">
                  Run as a native, full-screen Android app
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="w-9 h-9 rounded-full bg-white hover:bg-[#FFF0F3] text-[#6B5360] hover:text-[#2D1F27] flex items-center justify-center transition-colors shadow-2xs border border-[#FDE2E7] cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* CONTENT */}
          <div className="p-5 sm:p-6 overflow-y-auto space-y-5">
            {/* APP PREVIEW CARD */}
            <div className="p-4 rounded-[28px] bg-[#FFF7F9] border border-[#FDE2E7] flex items-center gap-4">
              <div className="w-16 h-16 rounded-2xl bg-white border border-[#FDE2E7] shadow-xs overflow-hidden flex-shrink-0 flex items-center justify-center p-2">
                <img src="/icon.svg" alt="Tiku App Icon" className="w-full h-full object-contain" />
              </div>
              <div className="space-y-1">
                <h4 className="text-sm font-bold text-[#2D1F27] flex items-center gap-1.5">
                  <span>Tiku — Couple Memory Space</span>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-extrabold border border-emerald-200">
                    PWA App
                  </span>
                </h4>
                <p className="text-xs text-[#6B5360]">
                  Full-screen mode, fast load times, offline cache & home screen icon.
                </p>
              </div>
            </div>

            {/* DIRECT 1-TAP INSTALL ACTION */}
            {deferredPrompt && (
              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={handleInstallClick}
                className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-[#F47D90] to-[#EE6A80] hover:opacity-95 text-white text-sm font-extrabold shadow-tuchi-glow flex items-center justify-center gap-2 cursor-pointer transition-all"
              >
                <Download className="w-4 h-4 text-white" />
                <span>1-Tap Install on this Android Device</span>
              </motion.button>
            )}

            {installSuccess && (
              <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-bold flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                <span>Tiku has been installed to your Android Home Screen! 🎉</span>
              </div>
            )}

            {/* STEP BY STEP ANDROID GUIDE */}
            <div className="space-y-3">
              <h5 className="text-xs font-bold uppercase tracking-wider text-[#6B5360]">
                How to install on any Android phone (Chrome, Samsung Internet, Edge)
              </h5>

              <div className="space-y-2.5 text-xs text-[#2D1F27]">
                <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] flex items-start gap-3 shadow-xs">
                  <div className="w-6 h-6 rounded-full bg-[#FFF0F3] text-[#F47D90] font-extrabold flex items-center justify-center flex-shrink-0 text-xs border border-[#FDE2E7]">
                    1
                  </div>
                  <div>
                    <p className="font-bold">Open this URL in Google Chrome or Samsung Internet</p>
                    <p className="text-[#6B5360] text-[11px] mt-0.5">
                      Open this web app URL directly on your Android phone's browser.
                    </p>
                  </div>
                </div>

                <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] flex items-start gap-3 shadow-xs">
                  <div className="w-6 h-6 rounded-full bg-[#FFF0F3] text-[#F47D90] font-extrabold flex items-center justify-center flex-shrink-0 text-xs border border-[#FDE2E7]">
                    2
                  </div>
                  <div>
                    <p className="font-bold flex items-center gap-1">
                      Tap the 3 dots menu <MoreVertical className="w-3.5 h-3.5 text-[#F47D90] inline" />
                    </p>
                    <p className="text-[#6B5360] text-[11px] mt-0.5">
                      Tap the browser menu at the top right of Google Chrome.
                    </p>
                  </div>
                </div>

                <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] flex items-start gap-3 shadow-xs">
                  <div className="w-6 h-6 rounded-full bg-[#FFF0F3] text-[#F47D90] font-extrabold flex items-center justify-center flex-shrink-0 text-xs border border-[#FDE2E7]">
                    3
                  </div>
                  <div>
                    <p className="font-bold flex items-center gap-1">
                      Select <span className="text-[#F47D90]">"Install app"</span> or <span className="text-[#F47D90]">"Add to Home screen"</span>
                    </p>
                    <p className="text-[#6B5360] text-[11px] mt-0.5">
                      Tiku will automatically download its icon and install as a standalone Android app in your App Drawer!
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* ANDROID APP FEATURES */}
            <div className="grid grid-cols-2 gap-2.5 pt-1">
              <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-1">
                <div className="flex items-center gap-1.5 text-xs font-bold text-[#2D1F27]">
                  <Zap className="w-3.5 h-3.5 text-[#F47D90]" />
                  <span>Instant Launch</span>
                </div>
                <p className="text-[11px] text-[#6B5360]">
                  Opens instantly without browser toolbars.
                </p>
              </div>

              <div className="p-3.5 rounded-2xl bg-[#FFF7F9] border border-[#FDE2E7] space-y-1">
                <div className="flex items-center gap-1.5 text-xs font-bold text-[#2D1F27]">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Synced Vault</span>
                </div>
                <p className="text-[11px] text-[#6B5360]">
                  Cloud synced with your partner securely.
                </p>
              </div>
            </div>
          </div>

          {/* FOOTER */}
          <div className="p-4 sm:p-5 border-t border-[#FDE2E7] bg-[#FFF7F9] flex items-center justify-between gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-2xl text-xs font-bold text-[#6B5360] hover:text-[#2D1F27] cursor-pointer"
            >
              Close
            </button>

            <button
              type="button"
              onClick={async () => {
                if (navigator.share) {
                  try {
                    await navigator.share({
                      title: 'Tiku — Couple Memory Space',
                      text: 'Open this on your Android phone to install the couple app!',
                      url: window.location.href,
                    });
                  } catch (e) {}
                } else {
                  await navigator.clipboard.writeText(window.location.href);
                  alert('App URL copied to clipboard! Open in Android Chrome to install.');
                }
              }}
              className="px-4 py-2 rounded-2xl bg-white border border-[#FDE2E7] text-[#F47D90] hover:bg-[#FFF0F3] text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-colors"
            >
              <Share2 className="w-3.5 h-3.5 text-[#F47D90]" />
              <span>Share / Copy App Link</span>
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
