import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'

import { Button, Input, Textarea } from '../../../components/ui'
import { subjectFormSchema, type SubjectFormValues } from './subjectFormSchema'

interface SubjectFormProps {
  readonly initialValues?: SubjectFormValues
  readonly pending: boolean
  readonly serverError?: string
  readonly onCancel: () => void
  readonly onSubmit: (values: SubjectFormValues) => void
}

export function SubjectForm({ initialValues, pending, serverError, onCancel, onSubmit }: SubjectFormProps) {
  const errorRef = useRef<HTMLParagraphElement>(null)
  const form = useForm<SubjectFormValues>({
    resolver: zodResolver(subjectFormSchema),
    defaultValues: initialValues ?? { name: '', description: '' },
  })

  useEffect(() => {
    if (serverError) errorRef.current?.focus()
  }, [serverError])

  return (
    <form className="organization-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Input label="Name" autoFocus autoComplete="off" error={form.formState.errors.name?.message} {...form.register('name')} />
      <Textarea label="Description" description="Optional" {...form.register('description')} />
      {serverError ? <p className="organization-form-error" ref={errorRef} role="alert" tabIndex={-1}>{serverError}</p> : null}
      <div className="organization-actions">
        <Button disabled={pending} type="submit">{pending ? 'Saving…' : 'Save Subject'}</Button>
        <Button disabled={pending} onClick={onCancel} variant="tertiary">Cancel</Button>
      </div>
    </form>
  )
}
