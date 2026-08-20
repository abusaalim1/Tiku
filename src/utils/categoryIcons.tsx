import React from 'react';
import { LucideProps } from 'lucide-react';
import { Category3DIcon } from '../components/Romance3DIcons';

interface CategoryIconProps extends LucideProps {
  iconName?: string;
  categoryName?: string;
  fallbackIcon?: React.FC<LucideProps>;
  size?: number | string;
}

export const CategoryIcon: React.FC<CategoryIconProps> = ({
  iconName,
  categoryName,
  size = 24,
  className = '',
}) => {
  const numericSize = typeof size === 'number' ? size : parseInt(size) || 24;
  return (
    <Category3DIcon
      iconName={iconName}
      categoryName={categoryName}
      size={numericSize}
      className={className}
    />
  );
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
