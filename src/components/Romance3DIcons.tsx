import React from 'react';

export interface Icon3DProps {
  className?: string;
  size?: number;
}

// 1. 3D Glossy Rose Heart
export const Heart3D: React.FC<Icon3DProps> = ({ className = '', size = 48 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_16px_rgba(244,125,144,0.4)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="heartPinkGrad" x1="15%" y1="10%" x2="85%" y2="90%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="40%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#D94E68" />
      </linearGradient>
      <radialGradient id="heartSpecular" cx="35%" cy="28%" r="35%">
        <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.8" />
        <stop offset="60%" stopColor="#FFFFFF" stopOpacity="0" />
      </radialGradient>
      <radialGradient id="heartDepth" cx="50%" cy="80%" r="40%">
        <stop offset="0%" stopColor="#9C243B" stopOpacity="0.35" />
        <stop offset="100%" stopColor="#9C243B" stopOpacity="0" />
      </radialGradient>
    </defs>
    {/* Base 3D Heart */}
    <path
      d="M50 86 C50 86 14 62 8 38 C3 20 17 8 32 10 C41 12 47 19 50 25 C53 19 59 12 68 10 C83 8 97 20 92 38 C86 62 50 86 50 86 Z"
      fill="url(#heartPinkGrad)"
    />
    {/* Bottom Depth Shadow */}
    <path
      d="M50 86 C50 86 14 62 8 38 C3 20 17 8 32 10 C41 12 47 19 50 25 C53 19 59 12 68 10 C83 8 97 20 92 38 C86 62 50 86 50 86 Z"
      fill="url(#heartDepth)"
    />
    {/* 3D Glossy Highlight */}
    <ellipse cx="32" cy="24" rx="10" ry="5" fill="url(#heartSpecular)" transform="rotate(-30 32 24)" />
    <circle cx="68" cy="22" r="3.5" fill="#FFFFFF" fillOpacity="0.65" />
  </svg>
);

export const Heart3DIcon = Heart3D;

// 2. 3D Wrapped Gift Box with Ribbon & Bow
export const GiftBox3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="giftBoxGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF0F3" />
        <stop offset="50%" stopColor="#FDE2E7" />
        <stop offset="100%" stopColor="#F8B8C6" />
      </linearGradient>
      <linearGradient id="giftLidGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="100%" stopColor="#FCD5DE" />
      </linearGradient>
      <linearGradient id="ribbonPink" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FF9FB0" />
        <stop offset="60%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#D94E68" />
      </linearGradient>
      <linearGradient id="goldBow" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFE599" />
        <stop offset="50%" stopColor="#FFC857" />
        <stop offset="100%" stopColor="#E69500" />
      </linearGradient>
    </defs>
    {/* Box Body */}
    <rect x="22" y="44" width="56" height="42" rx="10" fill="url(#giftBoxGrad)" />
    {/* Vertical Ribbon */}
    <rect x="44" y="44" width="12" height="42" fill="url(#ribbonPink)" />
    {/* Box Lid */}
    <rect x="18" y="34" width="64" height="14" rx="6" fill="url(#giftLidGrad)" />
    <rect x="44" y="34" width="12" height="14" fill="url(#ribbonPink)" />
    {/* Ribbon Bow */}
    <g transform="translate(50, 26)">
      {/* Left Loop */}
      <path d="M 0 4 C -16 -12 -24 6 0 6 Z" fill="url(#ribbonPink)" />
      <ellipse cx="-8" cy="-1" rx="4" ry="2" fill="#FFFFFF" fillOpacity="0.4" transform="rotate(20 -8 -1)" />
      {/* Right Loop */}
      <path d="M 0 4 C 16 -12 24 6 0 6 Z" fill="url(#ribbonPink)" />
      <ellipse cx="8" cy="-1" rx="4" ry="2" fill="#FFFFFF" fillOpacity="0.4" transform="rotate(-20 8 -1)" />
      {/* Center Knot */}
      <circle cx="0" cy="5" r="5" fill="url(#goldBow)" />
    </g>
    {/* Sparkle */}
    <path d="M 76 22 L 78 28 L 84 30 L 78 32 L 76 38 L 74 32 L 68 30 L 74 28 Z" fill="url(#goldBow)" opacity="0.9" />
  </svg>
);

// 3. 3D Polaroid Camera
export const PolaroidCamera3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="cameraBody" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="50%" stopColor="#FFF0F3" />
        <stop offset="100%" stopColor="#FCDCE3" />
      </linearGradient>
      <linearGradient id="lensOuter" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="100%" stopColor="#F47D90" />
      </linearGradient>
      <linearGradient id="lensGlass" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#3E2431" />
        <stop offset="60%" stopColor="#1E0D17" />
        <stop offset="100%" stopColor="#5A283C" />
      </linearGradient>
    </defs>
    {/* Camera Body */}
    <rect x="16" y="28" width="68" height="54" rx="14" fill="url(#cameraBody)" stroke="#FDE2E7" strokeWidth="1.5" />
    {/* Top Bar / Flash */}
    <rect x="26" y="20" width="20" height="10" rx="4" fill="#F47D90" />
    <circle cx="70" cy="38" r="4.5" fill="#FFE599" stroke="#FFC857" strokeWidth="1" />
    {/* Pink Stripe */}
    <rect x="16" y="48" width="68" height="6" fill="#FDE2E7" />
    {/* Big Lens */}
    <circle cx="50" cy="56" r="18" fill="url(#lensOuter)" />
    <circle cx="50" cy="56" r="14" fill="url(#lensGlass)" />
    <circle cx="45" cy="51" r="4" fill="#FFFFFF" fillOpacity="0.75" />
    {/* Emerging Mini Polaroid */}
    <rect x="36" y="72" width="28" height="18" rx="3" fill="#FFFFFF" stroke="#F8B8C6" strokeWidth="1" />
    <rect x="39" y="74" width="22" height="11" rx="2" fill="#FFF0F3" />
    <path d="M 50 80 C 47 77 44 80 50 83 C 56 80 53 77 50 80 Z" fill="#F47D90" />
  </svg>
);

// 4. 3D Rose Bouquet
export const RoseBouquet3D: React.FC<Icon3DProps> = ({ className = '', size = 64 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_20px_rgba(244,125,144,0.4)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="coneWrap" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF2F5" />
        <stop offset="60%" stopColor="#FCD5DE" />
        <stop offset="100%" stopColor="#F8ABC0" />
      </linearGradient>
      <linearGradient id="roseRed" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FF9FB0" />
        <stop offset="40%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
    </defs>
    {/* Wrapping Cone */}
    <path d="M 28 42 C 22 62 40 90 50 94 C 60 90 78 62 72 42 Z" fill="url(#coneWrap)" stroke="#F8ABC0" strokeWidth="1" />
    {/* Rose Buds */}
    <circle cx="40" cy="34" r="10" fill="url(#roseRed)" />
    <circle cx="40" cy="34" r="4.5" fill="#FFE5EA" opacity="0.8" />
    <circle cx="58" cy="34" r="10" fill="url(#roseRed)" />
    <circle cx="58" cy="34" r="4.5" fill="#FFE5EA" opacity="0.8" />
    <circle cx="49" cy="24" r="11" fill="url(#roseRed)" />
    <circle cx="49" cy="24" r="5" fill="#FFFFFF" opacity="0.8" />
    <circle cx="34" cy="46" r="8" fill="url(#roseRed)" />
    <circle cx="64" cy="46" r="8" fill="url(#roseRed)" />
    <circle cx="49" cy="44" r="9" fill="url(#roseRed)" />
    <circle cx="49" cy="44" r="4" fill="#FFE5EA" opacity="0.8" />
    {/* Ribbon Tie */}
    <g transform="translate(50, 72)">
      <circle cx="0" cy="0" r="4" fill="#F47D90" />
      <path d="M 0 0 C -8 -6 -12 2 0 3 Z" fill="#F47D90" />
      <path d="M 0 0 C 8 -6 12 2 0 3 Z" fill="#F47D90" />
      <path d="M -2 3 L -6 14" stroke="#F47D90" strokeWidth="2" strokeLinecap="round" />
      <path d="M 2 3 L 6 14" stroke="#F47D90" strokeWidth="2" strokeLinecap="round" />
    </g>
  </svg>
);

// 5. 3D Single Rose
export const SingleRose3D: React.FC<Icon3DProps> = ({ className = '', size = 48 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_6px_14px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="stemGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#78C694" />
        <stop offset="100%" stopColor="#3E8E5B" />
      </linearGradient>
      <linearGradient id="roseBloom" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
    </defs>
    {/* Stem */}
    <path d="M 48 48 C 50 68 45 84 48 94" stroke="url(#stemGrad)" strokeWidth="4.5" strokeLinecap="round" />
    {/* Leaves */}
    <path d="M 49 64 C 62 60 66 70 50 72 Z" fill="url(#stemGrad)" />
    <path d="M 47 74 C 34 70 30 80 46 82 Z" fill="url(#stemGrad)" />
    {/* Rose Flower Head */}
    <path
      d="M 50 16 C 66 16 74 30 68 44 C 62 54 38 54 32 44 C 26 30 34 16 50 16 Z"
      fill="url(#roseBloom)"
    />
    <circle cx="50" cy="30" r="11" fill="#C93554" />
    <path d="M 44 26 C 47 22 53 22 56 26 C 53 32 47 32 44 26 Z" fill="#FFA6B8" />
  </svg>
);

// 6. 3D Birthday / Celebration Cake
export const Cake3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="cakeCream" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="50%" stopColor="#FFF0F3" />
        <stop offset="100%" stopColor="#FCD5DE" />
      </linearGradient>
      <linearGradient id="cakeBase" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FDE2E7" />
        <stop offset="100%" stopColor="#F8ABC0" />
      </linearGradient>
      <linearGradient id="candleFlame" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#FFE082" />
        <stop offset="60%" stopColor="#FF9800" />
        <stop offset="100%" stopColor="#F44336" />
      </linearGradient>
    </defs>
    {/* Plate */}
    <ellipse cx="50" cy="84" rx="42" ry="8" fill="#FDE2E7" stroke="#F8ABC0" strokeWidth="1" />
    {/* Bottom Layer */}
    <path d="M 20 60 C 20 60 20 78 50 78 C 80 78 80 60 80 60 L 80 50 C 80 50 80 64 50 64 C 20 64 20 50 20 50 Z" fill="url(#cakeBase)" />
    <ellipse cx="50" cy="50" rx="30" ry="9" fill="url(#cakeCream)" />
    {/* Top Layer */}
    <path d="M 28 42 C 28 42 28 56 50 56 C 72 56 72 42 72 42 L 72 34 C 72 34 72 46 50 46 C 28 46 28 34 28 34 Z" fill="url(#cakeBase)" />
    <ellipse cx="50" cy="34" rx="22" ry="7" fill="url(#cakeCream)" />
    {/* Cream Drips */}
    <circle cx="34" cy="40" r="3" fill="#FFFFFF" />
    <circle cx="50" cy="42" r="3.5" fill="#FFFFFF" />
    <circle cx="66" cy="40" r="3" fill="#FFFFFF" />
    {/* Strawberries */}
    <circle cx="38" cy="32" r="4" fill="#F47D90" />
    <circle cx="62" cy="32" r="4" fill="#F47D90" />
    {/* Center Candle */}
    <rect x="48" y="16" width="4" height="14" rx="1.5" fill="#F47D90" />
    <path d="M 50 8 C 47 12 47 15 50 17 C 53 15 53 12 50 8 Z" fill="url(#candleFlame)" />
  </svg>
);

// 7. 3D Sparkles / Stars
export const Sparkles3D: React.FC<Icon3DProps> = ({ className = '', size = 44 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_6px_14px_rgba(255,200,87,0.5)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="sparkleGold" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="40%" stopColor="#FFE082" />
        <stop offset="100%" stopColor="#FFB300" />
      </linearGradient>
      <linearGradient id="sparklePink" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="50%" stopColor="#FFA6B8" />
        <stop offset="100%" stopColor="#F47D90" />
      </linearGradient>
    </defs>
    {/* Big Center Star */}
    <path
      d="M 50 8 C 50 32 68 50 92 50 C 68 50 50 68 50 92 C 50 68 32 50 8 50 C 32 50 50 32 50 8 Z"
      fill="url(#sparkleGold)"
    />
    <circle cx="50" cy="50" r="6" fill="#FFFFFF" />
    {/* Small Pink Star Top-Right */}
    <path
      d="M 76 14 C 76 22 82 28 90 28 C 82 28 76 34 76 42 C 76 34 70 28 62 28 C 70 28 76 22 76 14 Z"
      fill="url(#sparklePink)"
    />
    {/* Mini Gold Star Bottom-Left */}
    <path
      d="M 24 64 C 24 70 28 74 34 74 C 28 74 24 78 24 84 C 24 78 20 74 14 74 C 20 74 24 70 24 64 Z"
      fill="url(#sparkleGold)"
    />
  </svg>
);

export const Sparkle3DIcon = Sparkles3D;

// 8. 3D Love Letter / Envelope with Wax Heart Seal
export const LoveLetter3D: React.FC<Icon3DProps> = ({ className = '', size = 48 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="envGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="50%" stopColor="#FFF0F3" />
        <stop offset="100%" stopColor="#FCD5DE" />
      </linearGradient>
      <linearGradient id="waxSeal" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
    </defs>
    {/* Envelope Body */}
    <rect x="14" y="26" width="72" height="52" rx="10" fill="url(#envGrad)" stroke="#FDE2E7" strokeWidth="1.5" />
    {/* Flap Lines */}
    <path d="M 14 28 L 50 56 L 86 28" stroke="#F8ABC0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M 14 76 L 42 48" stroke="#FDE2E7" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M 86 76 L 58 48" stroke="#FDE2E7" strokeWidth="1.5" strokeLinecap="round" />
    {/* Red Wax Heart Seal */}
    <circle cx="50" cy="56" r="10" fill="url(#waxSeal)" />
    <path d="M 50 62 C 47 59 44 61 50 64 C 56 61 53 59 50 62 Z" fill="#FFFFFF" />
  </svg>
);

// 9. 3D Party Popper / Celebration Confetti
export const PartyPopper3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="popperCone" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFE082" />
        <stop offset="50%" stopColor="#FFC857" />
        <stop offset="100%" stopColor="#F47D90" />
      </linearGradient>
    </defs>
    {/* Cone */}
    <path d="M 16 84 L 46 36 L 68 58 Z" fill="url(#popperCone)" />
    <ellipse cx="57" cy="47" rx="16" ry="7" fill="#F47D90" transform="rotate(45 57 47)" />
    {/* Confetti Ribbons */}
    <path d="M 64 34 Q 74 24 70 14" stroke="#F47D90" strokeWidth="3" strokeLinecap="round" fill="none" />
    <path d="M 76 44 Q 88 40 92 28" stroke="#FFC857" strokeWidth="3" strokeLinecap="round" fill="none" />
    <path d="M 52 24 Q 60 12 50 6" stroke="#48DB9C" strokeWidth="3" strokeLinecap="round" fill="none" />
    {/* Confetti Dots & Stars */}
    <circle cx="78" cy="18" r="3" fill="#F47D90" />
    <circle cx="86" cy="38" r="3.5" fill="#FFC857" />
    <circle cx="58" cy="10" r="2.5" fill="#7E57C2" />
    <circle cx="44" cy="18" r="3" fill="#48DB9C" />
  </svg>
);

// 10. 3D Interlocking Couple Hearts
export const CoupleHearts3D: React.FC<Icon3DProps> = ({ className = '', size = 56 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.4)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="pinkH1" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#D94E68" />
      </linearGradient>
      <linearGradient id="peachH2" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFE0B2" />
        <stop offset="50%" stopColor="#FFAB91" />
        <stop offset="100%" stopColor="#FF7043" />
      </linearGradient>
    </defs>
    {/* Right Pink Heart */}
    <g transform="translate(18, 6)">
      <path
        d="M 40 70 C 40 70 12 50 8 30 C 4 15 16 5 28 7 C 35 8 40 14 40 18 C 40 14 45 8 52 7 C 64 5 76 15 72 30 C 68 50 40 70 40 70 Z"
        fill="url(#pinkH1)"
      />
      <ellipse cx="26" cy="18" rx="6" ry="3" fill="#FFFFFF" fillOpacity="0.6" transform="rotate(-25 26 18)" />
    </g>
    {/* Left Peach Heart */}
    <g transform="translate(-4, 20) scale(0.8)">
      <path
        d="M 40 70 C 40 70 12 50 8 30 C 4 15 16 5 28 7 C 35 8 40 14 40 18 C 40 14 45 8 52 7 C 64 5 76 15 72 30 C 68 50 40 70 40 70 Z"
        fill="url(#peachH2)"
      />
      <ellipse cx="26" cy="18" rx="6" ry="3" fill="#FFFFFF" fillOpacity="0.6" transform="rotate(-25 26 18)" />
    </g>
  </svg>
);

// 11. 3D Locked Heart
export const LockedHeart3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="lockHeart" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
      <linearGradient id="lockShackle" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFE599" />
        <stop offset="100%" stopColor="#FFC857" />
      </linearGradient>
    </defs>
    {/* Shackle */}
    <path d="M 38 40 L 38 24 C 38 14 62 14 62 24 L 62 40" stroke="url(#lockShackle)" strokeWidth="6" strokeLinecap="round" fill="none" />
    {/* Heart Body */}
    <path
      d="M 50 82 C 50 82 18 60 12 40 C 8 26 18 16 32 18 C 39 19 45 25 50 30 C 55 25 61 19 68 18 C 82 16 92 26 88 40 C 82 60 50 82 50 82 Z"
      fill="url(#lockHeart)"
    />
    {/* Keyhole */}
    <circle cx="50" cy="46" r="4.5" fill="#FFE599" />
    <polygon points="48,46 52,46 53,56 47,56" fill="#FFE599" />
  </svg>
);

// 12. 3D Winged Heart
export const WingedHeart3D: React.FC<Icon3DProps> = ({ className = '', size = 64 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.4)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="wingedHeartPink" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#D94E68" />
      </linearGradient>
      <linearGradient id="wingFeathers" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="100%" stopColor="#FDE2E7" />
      </linearGradient>
    </defs>
    {/* Left Wing */}
    <g transform="translate(6, 22)">
      <path d="M 28 20 C 10 10 2 24 4 36 C 8 44 24 38 30 30 Z" fill="url(#wingFeathers)" stroke="#F8B8C6" strokeWidth="1" />
      <path d="M 26 12 C 4 0 -4 14 0 26 C 6 36 20 26 28 18 Z" fill="url(#wingFeathers)" stroke="#F8B8C6" strokeWidth="1" />
    </g>
    {/* Right Wing */}
    <g transform="translate(64, 22)">
      <path d="M 2 20 C 20 10 28 24 26 36 C 22 44 6 38 0 30 Z" fill="url(#wingFeathers)" stroke="#F8B8C6" strokeWidth="1" />
      <path d="M 4 12 C 26 0 34 14 30 26 C 24 36 10 26 2 18 Z" fill="url(#wingFeathers)" stroke="#F8B8C6" strokeWidth="1" />
    </g>
    {/* Center Heart */}
    <path
      d="M 50 78 C 50 78 26 58 20 42 C 16 30 26 20 37 22 C 43 24 47 28 50 33 C 53 28 57 24 63 22 C 74 20 84 30 80 42 C 74 58 50 78 50 78 Z"
      fill="url(#wingedHeartPink)"
    />
    <ellipse cx="36" cy="30" rx="7" ry="3.5" fill="#FFFFFF" fillOpacity="0.65" transform="rotate(-25 36 30)" />
  </svg>
);

// 13. 3D Cupid Bow & Arrow
export const CupidBow3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="bowGold" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF0B3" />
        <stop offset="50%" stopColor="#FFC857" />
        <stop offset="100%" stopColor="#E69500" />
      </linearGradient>
    </defs>
    {/* Bow Arc */}
    <path d="M 30 20 C 65 30 75 70 35 85" stroke="url(#bowGold)" strokeWidth="6" strokeLinecap="round" fill="none" />
    <line x1="30" y1="20" x2="35" y2="85" stroke="#FDE2E7" strokeWidth="2" strokeDasharray="3 3" />
    {/* Arrow */}
    <line x1="20" y1="52" x2="80" y2="52" stroke="#F47D90" strokeWidth="4" strokeLinecap="round" />
    {/* Arrow Heart Tip */}
    <path d="M 80 52 C 74 46 72 49 76 52 C 72 55 74 58 80 52 Z" fill="#F47D90" />
  </svg>
);

// 14. 3D Ring Box / Engagement Ring
export const RingBox3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="ringBoxGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
      <linearGradient id="ringGold" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF0B3" />
        <stop offset="50%" stopColor="#FFC857" />
        <stop offset="100%" stopColor="#E69500" />
      </linearGradient>
    </defs>
    {/* Open Top Lid */}
    <ellipse cx="50" cy="30" rx="26" ry="12" fill="#FFA6B8" />
    <path d="M 24 30 C 24 20 76 20 76 30 C 76 40 24 40 24 30 Z" fill="#9C243B" />
    {/* Base Box */}
    <path d="M 22 46 C 22 38 78 38 78 46 L 72 74 C 72 82 28 82 28 74 Z" fill="url(#ringBoxGrad)" />
    <ellipse cx="50" cy="46" rx="28" ry="10" fill="#4A1220" />
    {/* Sparkling Ring */}
    <ellipse cx="50" cy="44" rx="10" ry="5" fill="none" stroke="url(#ringGold)" strokeWidth="3" />
    <polygon points="50,34 46,38 54,38" fill="#E0F7FA" />
    <polygon points="50,30 47,34 53,34" fill="#FFFFFF" />
  </svg>
);

// 15. 3D Photo Album / Scrapbook
export const PhotoAlbum3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="albumCover" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="50%" stopColor="#FFF0F3" />
        <stop offset="100%" stopColor="#FCD5DE" />
      </linearGradient>
      <linearGradient id="albumSpine" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="100%" stopColor="#F47D90" />
      </linearGradient>
    </defs>
    {/* Book Pages */}
    <rect x="24" y="24" width="58" height="58" rx="8" fill="#FFFFFF" stroke="#FDE2E7" strokeWidth="2" />
    {/* Book Cover */}
    <rect x="20" y="20" width="60" height="60" rx="10" fill="url(#albumCover)" stroke="#F8ABC0" strokeWidth="1.5" />
    {/* Spine */}
    <rect x="20" y="20" width="12" height="60" rx="4" fill="url(#albumSpine)" />
    {/* Heart Badge on Cover */}
    <path
      d="M 54 54 C 54 54 38 42 34 30 C 32 20 40 14 48 16 C 51 17 53 20 54 23 C 55 20 57 17 60 16 C 68 14 76 20 74 30 C 70 42 54 54 54 54 Z"
      fill="#F47D90"
      transform="scale(0.6) translate(30, 26)"
    />
  </svg>
);

// 16. 3D Calendar / Date Planner
export const Calendar3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="calBody" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="100%" stopColor="#FFF0F3" />
      </linearGradient>
    </defs>
    {/* Calendar Page */}
    <rect x="18" y="24" width="64" height="58" rx="12" fill="url(#calBody)" stroke="#FDE2E7" strokeWidth="1.5" />
    {/* Red Top Header */}
    <path d="M 18 36 L 18 32 C 18 26 24 24 30 24 L 70 24 C 76 24 82 26 82 32 L 82 36 Z" fill="#F47D90" />
    {/* Rings */}
    <rect x="30" y="16" width="6" height="12" rx="3" fill="#3E2431" />
    <rect x="64" y="16" width="6" height="12" rx="3" fill="#3E2431" />
    {/* Heart Date in Center */}
    <path
      d="M 50 68 C 50 68 34 56 30 46 C 28 38 34 32 42 34 C 47 35 49 38 50 41 C 51 38 53 35 58 34 C 66 32 72 38 70 46 C 66 56 50 68 50 68 Z"
      fill="#F47D90"
      transform="scale(0.8) translate(12, 12)"
    />
  </svg>
);

// 17. 3D Map Pin / Location Marker
export const LocationPin3D: React.FC<Icon3DProps> = ({ className = '', size = 48 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_16px_rgba(244,125,144,0.4)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="pinGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFA6B8" />
        <stop offset="50%" stopColor="#F47D90" />
        <stop offset="100%" stopColor="#C93554" />
      </linearGradient>
    </defs>
    {/* Shadow at bottom */}
    <ellipse cx="50" cy="88" rx="16" ry="5" fill="#3E2431" fillOpacity="0.15" />
    {/* Pin Body */}
    <path
      d="M 50 84 C 50 84 22 52 22 34 C 22 18 34 8 50 8 C 66 8 78 18 78 34 C 78 52 50 84 50 84 Z"
      fill="url(#pinGrad)"
    />
    {/* Inner White Heart */}
    <circle cx="50" cy="34" r="14" fill="#FFFFFF" />
    <path
      d="M 50 42 C 50 42 42 36 40 31 C 39 27 42 24 46 25 C 48 25.5 49 27 50 28.5 C 51 27 52 25.5 54 25 C 58 24 61 27 60 31 C 58 36 50 42 50 42 Z"
      fill="#F47D90"
    />
  </svg>
);

// 18. 3D Trophy / Golden Cup
export const Trophy3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(255,200,87,0.45)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="goldCup" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF9C4" />
        <stop offset="40%" stopColor="#FFD54F" />
        <stop offset="100%" stopColor="#FF8F00" />
      </linearGradient>
    </defs>
    {/* Cup Handles */}
    <path d="M 24 32 C 12 32 12 52 28 54" stroke="url(#goldCup)" strokeWidth="5" strokeLinecap="round" fill="none" />
    <path d="M 76 32 C 88 32 88 52 72 54" stroke="url(#goldCup)" strokeWidth="5" strokeLinecap="round" fill="none" />
    {/* Main Cup Body */}
    <path d="M 26 22 L 74 22 C 74 44 64 62 50 64 C 36 62 26 44 26 22 Z" fill="url(#goldCup)" />
    {/* Stem */}
    <rect x="46" y="64" width="8" height="14" rx="2" fill="url(#goldCup)" />
    {/* Base */}
    <rect x="30" y="78" width="40" height="10" rx="5" fill="url(#goldCup)" />
    {/* Heart on Cup */}
    <circle cx="50" cy="40" r="7" fill="#F47D90" />
  </svg>
);

// 19. 3D Crown
export const Crown3D: React.FC<Icon3DProps> = ({ className = '', size = 50 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_16px_rgba(255,200,87,0.45)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="crownGold" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFF9C4" />
        <stop offset="50%" stopColor="#FFD54F" />
        <stop offset="100%" stopColor="#FFA000" />
      </linearGradient>
    </defs>
    <path
      d="M 18 68 L 22 34 L 38 48 L 50 24 L 62 48 L 78 34 L 82 68 Z"
      fill="url(#crownGold)"
      stroke="#FF8F00"
      strokeWidth="1.5"
    />
    <rect x="16" y="68" width="68" height="10" rx="4" fill="url(#crownGold)" />
    {/* Jewels */}
    <circle cx="50" cy="24" r="4" fill="#F47D90" />
    <circle cx="22" cy="34" r="3.5" fill="#48DB9C" />
    <circle cx="78" cy="34" r="3.5" fill="#48DB9C" />
    <circle cx="50" cy="73" r="3" fill="#F47D90" />
  </svg>
);

// 20. 3D Coffee Cup / Cafe Date
export const CoffeeCup3D: React.FC<Icon3DProps> = ({ className = '', size = 48 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_6px_14px_rgba(244,125,144,0.3)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="cupGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="100%" stopColor="#FFF0F3" />
      </linearGradient>
    </defs>
    {/* Saucer */}
    <ellipse cx="50" cy="80" rx="34" ry="7" fill="#FDE2E7" stroke="#F8ABC0" strokeWidth="1" />
    {/* Handle */}
    <path d="M 68 40 C 80 40 80 60 66 60" stroke="#F47D90" strokeWidth="5" strokeLinecap="round" fill="none" />
    {/* Cup Body */}
    <path d="M 26 34 L 32 68 C 32 74 68 74 68 68 L 74 34 Z" fill="url(#cupGrad)" stroke="#FDE2E7" strokeWidth="1.5" />
    {/* Coffee & Latte Heart */}
    <ellipse cx="50" cy="34" rx="24" ry="7" fill="#8D6E63" />
    <ellipse cx="50" cy="34" rx="8" ry="3.5" fill="#FFF0F3" opacity="0.9" />
    {/* Steam */}
    <path d="M 44 24 Q 40 16 46 10" stroke="#F8ABC0" strokeWidth="2.5" strokeLinecap="round" fill="none" />
    <path d="M 54 24 Q 58 16 52 10" stroke="#F8ABC0" strokeWidth="2.5" strokeLinecap="round" fill="none" />
  </svg>
);

// 21. 3D Airplane / Travel Trip
export const Airplane3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_16px_rgba(244,125,144,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="planeBody" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" />
        <stop offset="60%" stopColor="#FFF0F3" />
        <stop offset="100%" stopColor="#FCD5DE" />
      </linearGradient>
    </defs>
    <g transform="rotate(-30 50 50)">
      {/* Wings */}
      <polygon points="50,18 20,54 80,54" fill="#F47D90" />
      {/* Fuselage */}
      <ellipse cx="50" cy="50" rx="10" ry="34" fill="url(#planeBody)" stroke="#F8ABC0" strokeWidth="1.5" />
      {/* Cockpit Window */}
      <ellipse cx="50" cy="26" rx="4.5" ry="5" fill="#3E2431" />
      {/* Tail */}
      <polygon points="50,72 38,86 62,86" fill="#F47D90" />
    </g>
  </svg>
);

// 22. 3D Shield / Private Security Vault
export const ShieldCheck3D: React.FC<Icon3DProps> = ({ className = '', size = 50 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_16px_rgba(72,219,156,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="shieldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#E8F8F0" />
        <stop offset="60%" stopColor="#C2EBD6" />
        <stop offset="100%" stopColor="#2A9D6F" />
      </linearGradient>
    </defs>
    {/* Shield Base */}
    <path
      d="M 50 14 L 78 24 C 78 54 66 76 50 88 C 34 76 22 54 22 24 Z"
      fill="url(#shieldGrad)"
      stroke="#2A9D6F"
      strokeWidth="2"
    />
    {/* Checkmark */}
    <path d="M 38 48 L 46 56 L 64 38" stroke="#12583D" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

// Aliases for convenience & backward compatibility
export const Rose3D = SingleRose3D;
export const Bouquet3D = RoseBouquet3D;
export const CalendarHeart3D = Calendar3D;
export const Lock3D = LockedHeart3D;
export const Unlock3D: React.FC<Icon3DProps> = ({ className = '', size = 52 }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 100 100"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={`filter drop-shadow-[0_8px_18px_rgba(72,219,156,0.35)] select-none shrink-0 ${className}`}
  >
    <defs>
      <linearGradient id="unlockHeart" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#E8F8F0" />
        <stop offset="50%" stopColor="#C2EBD6" />
        <stop offset="100%" stopColor="#2A9D6F" />
      </linearGradient>
      <linearGradient id="unlockShackle" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FFE599" />
        <stop offset="100%" stopColor="#FFC857" />
      </linearGradient>
    </defs>
    {/* Open Shackle */}
    <path d="M 38 40 L 38 18 C 38 8 62 8 62 18 L 62 26" stroke="url(#unlockShackle)" strokeWidth="6" strokeLinecap="round" fill="none" />
    {/* Heart Body */}
    <path
      d="M 50 82 C 50 82 18 60 12 40 C 8 26 18 16 32 18 C 39 19 45 25 50 30 C 55 25 61 19 68 18 C 82 16 92 26 88 40 C 82 60 50 82 50 82 Z"
      fill="url(#unlockHeart)"
    />
    <circle cx="50" cy="46" r="4.5" fill="#12583D" />
    <polygon points="48,46 52,46 53,56 47,56" fill="#12583D" />
  </svg>
);
export const BookJournal3D = PhotoAlbum3D;

// Universal Smart 3D Category Icon Component
export const Category3DIcon: React.FC<{
  iconName?: string;
  categoryName?: string;
  className?: string;
  size?: number;
}> = ({ iconName, categoryName = '', className = '', size = 36 }) => {
  const norm = (iconName || categoryName).toLowerCase();

  if (norm.includes('coffee') || norm.includes('cafe') || norm.includes('date')) {
    return <CoffeeCup3D className={className} size={size} />;
  }
  if (norm.includes('plane') || norm.includes('trip') || norm.includes('travel') || norm.includes('vacation')) {
    return <Airplane3D className={className} size={size} />;
  }
  if (norm.includes('gift') || norm.includes('anniversary') || norm.includes('present')) {
    return <GiftBox3D className={className} size={size} />;
  }
  if (norm.includes('camera') || norm.includes('photo') || norm.includes('polaroid') || norm.includes('film')) {
    return <PolaroidCamera3D className={className} size={size} />;
  }
  if (norm.includes('cake') || norm.includes('birthday') || norm.includes('celebrat')) {
    return <Cake3D className={className} size={size} />;
  }
  if (norm.includes('rose') || norm.includes('flower') || norm.includes('bouquet')) {
    return <RoseBouquet3D className={className} size={size} />;
  }
  if (norm.includes('trophy') || norm.includes('milestone') || norm.includes('winner')) {
    return <Trophy3D className={className} size={size} />;
  }
  if (norm.includes('star') || norm.includes('sparkle') || norm.includes('future') || norm.includes('dream')) {
    return <Sparkles3D className={className} size={size} />;
  }
  if (norm.includes('pin') || norm.includes('location') || norm.includes('map')) {
    return <LocationPin3D className={className} size={size} />;
  }
  if (norm.includes('calendar') || norm.includes('date')) {
    return <Calendar3D className={className} size={size} />;
  }
  if (norm.includes('album') || norm.includes('folder')) {
    return <PhotoAlbum3D className={className} size={size} />;
  }
  if (norm.includes('kiss') || norm.includes('romantic') || norm.includes('love')) {
    return <CoupleHearts3D className={className} size={size} />;
  }
  if (norm.includes('lock') || norm.includes('vault') || norm.includes('secret')) {
    return <LockedHeart3D className={className} size={size} />;
  }

  // Default to 3D Heart
  return <Heart3D className={className} size={size} />;
};
