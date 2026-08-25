import { forwardRef, type HTMLAttributes } from 'react'

export type BadgeTone =
  | 'neutral'
  | 'strong'
  | 'developing'
  | 'needs-attention'
  | 'insufficient-evidence'

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & {
  tone?: BadgeTone
}

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  { className, tone = 'neutral', ...props },
  ref,
) {
  const classes = ['ui-badge', `ui-badge-${tone}`, className].filter(Boolean).join(' ')

  return <span {...props} className={classes} ref={ref} />
})
