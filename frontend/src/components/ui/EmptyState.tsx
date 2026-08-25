import { forwardRef, useId, type HTMLAttributes, type ReactNode } from 'react'

export type EmptyStateProps = Omit<HTMLAttributes<HTMLElement>, 'children' | 'title'> & {
  title: ReactNode
  description: ReactNode
  action?: ReactNode
}

export const EmptyState = forwardRef<HTMLElement, EmptyStateProps>(function EmptyState(
  { action, className, description, title, ...props },
  ref,
) {
  const titleId = useId()
  const classes = ['ui-state', className].filter(Boolean).join(' ')

  return (
    <section {...props} aria-labelledby={titleId} className={classes} ref={ref}>
      <h2 className="ui-state-title" id={titleId}>
        {title}
      </h2>
      <p className="ui-state-description">{description}</p>
      {action ? <div className="ui-state-action">{action}</div> : null}
    </section>
  )
})
