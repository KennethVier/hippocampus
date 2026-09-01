import { Button, Dialog } from '../../../components/ui'

interface ArchiveConfirmationProps {
  readonly entity: 'Subject' | 'Topic'
  readonly name: string
  readonly open: boolean
  readonly pending: boolean
  readonly error?: string
  readonly onClose: () => void
  readonly onConfirm: () => void
}

export function ArchiveConfirmation({ entity, name, open, pending, error, onClose, onConfirm }: ArchiveConfirmationProps) {
  return (
    <Dialog closeOnBackdrop={!pending} description={`${name} will no longer appear in your active organization. Related study information is not deleted.`} onClose={pending ? () => undefined : onClose} open={open} title={`Archive ${entity}`}>
      {error ? <p className="organization-form-error" role="alert">{error}</p> : null}
      <div className="organization-actions">
        <Button disabled={pending} onClick={onConfirm}>{pending ? 'Archiving…' : `Archive ${entity}`}</Button>
        <Button disabled={pending} onClick={onClose} variant="tertiary">Cancel</Button>
      </div>
    </Dialog>
  )
}
