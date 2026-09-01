import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'

import { Button, Input, Textarea } from '../../../components/ui'
import { topicFormSchema, type TopicFormValues } from './topicFormSchema'

interface TopicFormProps {
  readonly initialValues?: TopicFormValues
  readonly pending: boolean
  readonly serverError?: string
  readonly onCancel: () => void
  readonly onSubmit: (values: TopicFormValues) => void
}

export function TopicForm({ initialValues, pending, serverError, onCancel, onSubmit }: TopicFormProps) {
  const errorRef = useRef<HTMLParagraphElement>(null)
  const form = useForm<TopicFormValues>({
    resolver: zodResolver(topicFormSchema),
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
        <Button disabled={pending} type="submit">{pending ? 'Saving…' : 'Save Topic'}</Button>
        <Button disabled={pending} onClick={onCancel} variant="tertiary">Cancel</Button>
      </div>
    </form>
  )
}
