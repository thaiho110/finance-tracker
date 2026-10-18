import { cn } from '@/lib/utils';
import { getCategoryTone } from '@/lib/utils';

interface CategoryBadgeProps {
  category: string;
  className?: string;
}

/**
 * Category badge with the correct tone color and dot indicator.
 * Follows the design spec: background at 14% opacity, solid 6px dot, default text color.
 */
export function CategoryBadge({ category, className }: CategoryBadgeProps) {
  const tone = getCategoryTone(category);
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
        `category-${tone}`,
        className
      )}
    >
      <span
        className="size-1.5 rounded-full shrink-0"
        style={{ backgroundColor: 'currentColor' }}
      />
      {category}
    </span>
  );
}
