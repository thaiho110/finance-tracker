import { cn } from '@/lib/utils';
import { getCategoryTone } from '@/lib/utils';

interface CategoryBadgeProps {
  category: string;
  /** The category's own color (#RRGGBB). Without it the badge falls back to a tone by name. */
  color?: string | null;
  className?: string;
}

/**
 * Category badge with a colored dot. Follows the design spec: background at 14% opacity, solid 6px dot,
 * default text color.
 */
export function CategoryBadge({ category, color, className }: CategoryBadgeProps) {
  const hasColor = !!color && /^#[0-9a-f]{6}$/i.test(color);
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
        !hasColor && `category-${getCategoryTone(category)}`,
        hasColor && 'text-text-default',
        className
      )}
      style={hasColor ? { backgroundColor: `${color}24` } : undefined}
    >
      <span
        className="size-1.5 rounded-full shrink-0"
        style={{ backgroundColor: hasColor ? color! : 'currentColor' }}
      />
      {category}
    </span>
  );
}
