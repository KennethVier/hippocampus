import { forwardRef, type HTMLAttributes } from 'react'

export type SkeletonProps = HTMLAttributes<HTMLDivElement> & {
  label?: string
}

export const Skeleton = forwardRef<HTMLDivElement, SkeletonProps>(function Skeleton(
  { className, label, ...props },
  ref,
) {
  const classes = ['ui-skeleton', className].filter(Boolean).join(' ')

  return <div {...props} aria-hidden={label ? undefined : true} aria-label={label} className={classes} ref={ref} role={label ? 'status' : undefined} />
})
