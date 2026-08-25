import { forwardRef, type ComponentPropsWithoutRef } from 'react'

export type CardProps = ComponentPropsWithoutRef<'div'>

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { className, ...props },
  ref,
) {
  const classes = ['ui-card', className].filter(Boolean).join(' ')

  return <div {...props} className={classes} ref={ref} />
})
