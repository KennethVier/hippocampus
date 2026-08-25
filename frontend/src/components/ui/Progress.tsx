import { forwardRef, useId, type ComponentPropsWithoutRef } from 'react'

export type ProgressProps = Omit<ComponentPropsWithoutRef<'progress'>, 'children' | 'max' | 'value'> & {
  value: number
  max?: number
  label: string
}

export const Progress = forwardRef<HTMLProgressElement, ProgressProps>(function Progress(
  { className, id: providedId, label, max = 100, value, ...props },
  ref,
) {
  const generatedId = useId()
  const id = providedId ?? `progress-${generatedId}`
  const classes = ['ui-progress-bar', className].filter(Boolean).join(' ')

  return (
    <div className="ui-progress">
      <label className="ui-progress-label" htmlFor={id}>
        {label}
      </label>
      <progress {...props} className={classes} id={id} max={max} ref={ref} value={value} />
    </div>
  )
})
