import { Button, Dialog } from '../../../components/ui'
export function DeleteMaterialConfirmation({ name, open, pending, error, onClose, onConfirm }: { readonly name: string; readonly open: boolean; readonly pending: boolean; readonly error?: string; readonly onClose: () => void; readonly onConfirm: () => void }) {
  return <Dialog closeOnBackdrop={!pending} description={`${name} will no longer be available in your Materials.`} onClose={pending ? () => undefined : onClose} open={open} title="Delete material">
    {error ? <p className="materials-error" role="alert">{error}</p> : null}
    <div className="materials-actions"><Button disabled={pending} onClick={onConfirm}>{pending ? 'Deleting…' : 'Delete material'}</Button><Button disabled={pending} onClick={onClose} variant="tertiary">Cancel</Button></div>
  </Dialog>
}
