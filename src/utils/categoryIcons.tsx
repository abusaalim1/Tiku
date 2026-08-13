import React from 'react';
import {
  Heart,
  Sparkles,
  Plane,
  Gift,
  Smile,
  Utensils,
  Camera,
  Music,
  Sun,
  Coffee,
  Star,
  Compass,
  MapPin,
  Film,
  Cake,
  FolderHeart,
  Calendar,
  Wine,
  Glasses,
  Flame,
  LucideProps,
} from 'lucide-react';

export const CATEGORY_ICON_MAP: Record<string, React.FC<LucideProps>> = {
  Heart,
  Sparkles,
  Plane,
  Gift,
  Smile,
  Utensils,
  Camera,
  Music,
  Sun,
  Coffee,
  Star,
  Compass,
  MapPin,
  Film,
  Cake,
  FolderHeart,
  Calendar,
  Wine,
  Glasses,
  Flame,
};

export const AVAILABLE_ICON_NAMES = [
  'Coffee',
  'Gift',
  'Plane',
  'Smile',
  'Heart',
  'Sparkles',
  'Utensils',
  'Camera',
  'Music',
  'Film',
  'Cake',
  'Wine',
  'Sun',
  'Star',
  'Compass',
  'Calendar',
];

interface CategoryIconProps extends LucideProps {
  iconName?: string;
  categoryName?: string;
  fallbackIcon?: React.FC<LucideProps>;
}

export const CategoryIcon: React.FC<CategoryIconProps> = ({
  iconName,
  categoryName,
  fallbackIcon: FallbackIcon = Heart,
  ...props
}) => {
  let resolvedIconName = iconName;

  // Smart resolution if iconName is missing or generic 'Heart'
  if ((!resolvedIconName || resolvedIconName === 'Heart') && categoryName) {
    const lower = categoryName.toLowerCase();
    if (lower.includes('date')) resolvedIconName = 'Coffee';
    else if (lower.includes('trip') || lower.includes('travel') || lower.includes('vacation')) resolvedIconName = 'Plane';
    else if (lower.includes('anniversary') || lower.includes('anniversaries')) resolvedIconName = 'Gift';
    else if (lower.includes('random') || lower.includes('cute')) resolvedIconName = 'Smile';
    else if (lower.includes('kiss')) resolvedIconName = 'Heart';
    else if (lower.includes('food') || lower.includes('dinner')) resolvedIconName = 'Utensils';
    else if (lower.includes('movie') || lower.includes('cinema')) resolvedIconName = 'Film';
    else if (lower.includes('party') || lower.includes('celebration')) resolvedIconName = 'Cake';
  }

  const IconComponent =
    (resolvedIconName && CATEGORY_ICON_MAP[resolvedIconName]) ||
    (iconName && CATEGORY_ICON_MAP[iconName]) ||
    FallbackIcon;

  return <IconComponent {...props} />;
};
