import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../../../api/apiClient'
import { Badge, Button, ErrorState, Skeleton } from '../../../components/ui'
import { deleteMaterial, getMaterial } from '../api/materialsApi'
import { DeleteMaterialConfirmation } from '../components/DeleteMaterialConfirmation'
import { displayMaterialStatus } from '../materialPresentation'
import { deleteErrorMessage } from '../materialsErrors'
import { materialKeys } from '../queries/materialQueries'
import '../materials.css'

export function MaterialDetailPage() {
  const rawId = useParams().materialId; const id = rawId && z.uuid().safeParse(rawId).success ? rawId : null
  const queryClient = useQueryClient(); const navigate = useNavigate(); const [deleteOpen, setDeleteOpen] = useState(false)
  const material = useQuery({ queryKey: materialKeys.detail(id ?? 'invalid'), queryFn: ({ signal }) => getMaterial(id ?? '', signal), enabled: id !== null })
  const deletion = useMutation({ mutationFn: deleteMaterial, onSuccess: async () => {
    if (id) queryClient.removeQueries({ queryKey: materialKeys.detail(id), exact: true })
    await queryClient.invalidateQueries({ queryKey: materialKeys.lists() }); setDeleteOpen(false); navigate('/materials')
  }, onError: async (error) => { if (error instanceof ApiError && error.code === 'MATERIAL_NOT_FOUND' && id) await queryClient.invalidateQueries({ queryKey: materialKeys.detail(id) }) } })
  if (id === null || (material.error instanceof ApiError && material.error.code === 'MATERIAL_NOT_FOUND')) return <UnavailableMaterial />
  if (material.isPending) return <section className="materials-page"><Skeleton label="Loading Material" /><Skeleton /></section>
  if (material.isError) return <ErrorState title="Material could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void material.refetch()}>Try again</Button>} />
  const current = material.data
  return <section className="materials-page" aria-labelledby="material-title">
    <nav aria-label="Breadcrumb"><Link to="/materials">Materials</Link><span aria-hidden="true"> / </span><span>{current.title}</span></nav>
    <header className="materials-header"><div><p className="materials-eyebrow">Material</p><div className="materials-title-row"><h1 id="material-title">{current.title}</h1><Badge>{displayMaterialStatus(current.status)}</Badge></div></div><Button onClick={() => { deletion.reset(); setDeleteOpen(true) }} variant="tertiary">Delete material</Button></header>
    <dl className="material-detail-metadata">
      {current.originalFilename ? <><dt>Original file</dt><dd>{current.originalFilename}</dd></> : null}
      <dt>Material type</dt><dd>{current.materialType}</dd>
      {current.mimeType ? <><dt>File type</dt><dd>{current.mimeType}</dd></> : null}
      <dt>Status</dt><dd>{displayMaterialStatus(current.status)}</dd>
      <dt>Added</dt><dd>{new Date(current.createdAt).toLocaleString()}</dd>
      <dt>Last updated</dt><dd>{new Date(current.updatedAt).toLocaleString()}</dd>
    </dl>
    <DeleteMaterialConfirmation name={current.title} open={deleteOpen} pending={deletion.isPending} error={deletion.error ? deleteErrorMessage(deletion.error) : undefined} onClose={() => setDeleteOpen(false)} onConfirm={() => deletion.mutate(current.id)} />
  </section>
}
function UnavailableMaterial() { return <ErrorState title="Material unavailable" description="This material could not be found or is not available to you." action={<Link className="materials-link-action" to="/materials">Back to Materials</Link>} /> }
