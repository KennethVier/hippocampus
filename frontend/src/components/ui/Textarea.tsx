import { forwardRef, useId, type ReactNode, type TextareaHTMLAttributes } from 'react'

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: ReactNode
  description?: ReactNode
  error?: ReactNode
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
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
  const id = providedId ?? `textarea-${generatedId}`
  const descriptionId = description ? `${id}-description` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [ariaDescribedBy, descriptionId, errorId].filter(Boolean).join(' ') || undefined
  const classes = ['ui-control', 'ui-textarea', error ? 'ui-control-error' : undefined, className]
    .filter(Boolean)
    .join(' ')

  return (
    <div className="ui-field">
      <label className="ui-label" htmlFor={id}>
        {label}
      </label>
      <textarea
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
