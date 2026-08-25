import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react'

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: ReactNode
  description?: ReactNode
  error?: ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    'aria-describedby': ariaDescribedBy,
    'aria-invalid': ariaInvalid,
    className,
    description,
    error,
    id: providedId,
    label,
    ...props
  },
  ref,
) {
  const generatedId = useId()
  const id = providedId ?? `input-${generatedId}`
  const descriptionId = description ? `${id}-description` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [ariaDescribedBy, descriptionId, errorId].filter(Boolean).join(' ') || undefined
  const classes = ['ui-control', error ? 'ui-control-error' : undefined, className]
    .filter(Boolean)
    .join(' ')

  return (
    <div className="ui-field">
      <label className="ui-label" htmlFor={id}>
        {label}
      </label>
      <input
        {...props}
        aria-describedby={describedBy}
        aria-invalid={error ? true : ariaInvalid}
        className={classes}
        id={id}
        ref={ref}
      />
      {description ? (
        <p className="ui-help" id={descriptionId}>
          {description}
        </p>
      ) : null}
      {error ? (
        <p className="ui-error" id={errorId}>
          {error}
        </p>
      ) : null}
    </div>
  )
})
