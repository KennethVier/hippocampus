import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useReducer, useRef, useState } from 'react'
import { ApiError, type UploadProgress } from '../../../api/apiClient'
import { Button, Card } from '../../../components/ui'
import { registerPrivateClientCleanup } from '../../auth/clearPrivateClientState'
import { currentSessionQueryKey } from '../../auth/currentSessionQuery'
import { uploadMaterial } from '../api/materialsApi'
import { uploadErrorMessage } from '../materialsErrors'
import { uploadReducer } from '../uploadState'

const ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'text/plain'] as const
const ACCEPT = ACCEPTED_TYPES.join(',')

export function MaterialUploadPanel({ onAccepted }: { readonly onAccepted: () => Promise<void> }) {
  const queryClient = useQueryClient()
  const [state, dispatch] = useReducer(uploadReducer, { type: 'idle' })
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const inputId = useId(); const errorId = useId()
  const attemptRef = useRef(0)
  const activeRef = useRef<{ attempt: number; controller: AbortController; unregister: () => void; inert: boolean } | null>(null)

  function stopActive(markCanceled: boolean) {
    const active = activeRef.current
    if (!active) return
    active.inert = true; active.controller.abort(); active.unregister(); activeRef.current = null
    if (markCanceled) dispatch({ type: 'cancel', attempt: active.attempt })
  }
  useEffect(() => () => stopActive(false), [])

  function choose(files: FileList | readonly File[]) {
    if (state.type === 'uploading' || state.type === 'awaiting-acceptance') return
    if (files.length !== 1) { setSelectionError('Choose one file at a time.'); return }
    const file = files[0]
    if (!file || file.size <= 0) { setSelectionError('Choose a file that is not empty.'); return }
    if (file.type && !ACCEPTED_TYPES.includes(file.type as (typeof ACCEPTED_TYPES)[number])) { setSelectionError('Choose a PDF, JPEG, PNG, or plain-text file.'); return }
    setSelectionError(null); dispatch({ type: 'select', file })
  }
  async function start() {
    if (state.type !== 'selected' && state.type !== 'failed') return
    const file = state.file; const attempt = ++attemptRef.current; const controller = new AbortController()
    const active: { attempt: number; controller: AbortController; unregister: () => void; inert: boolean } = { attempt, controller, inert: false, unregister: () => undefined }
    active.unregister = registerPrivateClientCleanup(() => {
      active.inert = true; controller.abort(); active.unregister()
      if (activeRef.current === active) activeRef.current = null
      dispatch({ type: 'reset' })
    })
    activeRef.current = active; dispatch({ type: 'start', attempt })
    try {
      const material = await uploadMaterial(file, (progress) => handleProgress(active, progress), controller.signal)
      if (active.inert || activeRef.current !== active) return
      active.unregister(); activeRef.current = null; dispatch({ type: 'accept', attempt, material }); await onAccepted()
    } catch (error: unknown) {
      if (active.inert || activeRef.current !== active) return
      active.unregister(); activeRef.current = null
      if (error instanceof ApiError && error.code === 'REQUEST_ABORTED') { dispatch({ type: 'cancel', attempt }); return }
      dispatch({ type: 'fail', attempt, error: { message: uploadErrorMessage(error) } })
      if (error instanceof ApiError && error.code === 'AUTHENTICATION_REQUIRED') void queryClient.invalidateQueries({ queryKey: currentSessionQueryKey })
    }
  }
  function handleProgress(active: NonNullable<typeof activeRef.current>, progress: UploadProgress) {
    if (active.inert || activeRef.current !== active) return
    dispatch({ type: 'progress', attempt: active.attempt, progress: progress.type === 'determinate' ? { type: 'determinate', percentage: progress.percentage } : { type: 'indeterminate' } })
  }
  const active = state.type === 'uploading' || state.type === 'awaiting-acceptance'
  const file = 'file' in state ? state.file : null
  return <Card className="material-upload">
    <div><p className="materials-eyebrow">Add learning material</p><h2>Upload a file</h2><p>Choose one PDF, JPEG, PNG, or plain-text file. Your file remains private to your account.</p></div>
    <div className="material-drop-zone" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); choose(event.dataTransfer.files) }}>
      <label className="materials-link-action" htmlFor={inputId}>Choose file</label><span> or drop one here</span>
      <input accept={ACCEPT} aria-describedby={selectionError ? errorId : undefined} disabled={active} id={inputId} onChange={(event) => { if (event.target.files) choose(event.target.files); event.target.value = '' }} type="file" />
    </div>
    {selectionError ? <p className="materials-error" id={errorId} role="alert">{selectionError}</p> : null}
    {file ? <p className="material-selected"><strong>Selected:</strong> {file.name}</p> : null}
    {state.type === 'uploading' ? <div className="material-progress">{state.progress.type === 'determinate' ? <><label htmlFor="material-upload-progress">Uploading {state.progress.percentage}%</label><progress id="material-upload-progress" max="100" value={state.progress.percentage} /></> : <><span>Uploading…</span><progress aria-label="Uploading" /></>}</div> : null}
    {state.type === 'awaiting-acceptance' ? <p aria-live="polite">Finishing upload…</p> : null}
    {state.type === 'accepted' ? <div className="material-accepted" role="status"><h3>Upload accepted</h3><p>{state.material.title} was accepted.</p><p>{formatBytes(state.material.fileSizeBytes)} · {state.material.mimeType} · {state.material.materialStatus === 'UPLOADED' ? 'Uploaded' : state.material.materialStatus}</p></div> : null}
    {state.type === 'failed' ? <p className="materials-error" role="alert">{state.error.message}</p> : null}
    {state.type === 'canceled' ? <p role="status">Upload canceled.</p> : null}
    <div className="materials-actions">
      {(state.type === 'selected' || state.type === 'failed') ? <Button onClick={() => void start()}>{state.type === 'failed' ? 'Try upload again' : 'Upload file'}</Button> : null}
      {active ? <Button onClick={() => stopActive(true)} variant="tertiary">Cancel upload</Button> : null}
      {(state.type === 'accepted' || state.type === 'canceled') ? <Button onClick={() => dispatch({ type: 'reset' })} variant="tertiary">Choose another file</Button> : null}
    </div>
  </Card>
}
function formatBytes(value: number) { return value < 1024 ? `${value} bytes` : `${(value / 1024).toFixed(1)} KB` }
