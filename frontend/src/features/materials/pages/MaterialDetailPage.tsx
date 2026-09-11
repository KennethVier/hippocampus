import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type Dispatch, type SetStateAction, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../../../api/apiClient'
import { Badge, Button, ErrorState, Skeleton } from '../../../components/ui'
import { deleteMaterial, getMaterial } from '../api/materialsApi'
import { DeleteMaterialConfirmation } from '../components/DeleteMaterialConfirmation'
import type { MaterialStructureNode } from '../api/materialContracts'
import { useMaterialProcessing, useMaterialStructure } from '../hooks/useMaterialProcessing'
import { displayMaterialStatus, displayProcessingStage, displayProcessingStatus } from '../materialPresentation'
import { deleteErrorMessage } from '../materialsErrors'
import { materialKeys } from '../queries/materialQueries'
import '../materials.css'

export function MaterialDetailPage() {
  const rawId = useParams().materialId; const id = rawId && z.uuid().safeParse(rawId).success ? rawId : null
  const queryClient = useQueryClient(); const navigate = useNavigate(); const [deleteOpen, setDeleteOpen] = useState(false)
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({})
  const material = useQuery({ queryKey: materialKeys.detail(id ?? 'invalid'), queryFn: ({ signal }) => getMaterial(id ?? '', signal), enabled: id !== null })
  const processing = useMaterialProcessing(id)
  const structure = useMaterialStructure(id, processing.data?.structureAvailable === true)
  const deletion = useMutation({ mutationFn: deleteMaterial, onSuccess: async () => {
    if (id) queryClient.removeQueries({ queryKey: materialKeys.detail(id), exact: true })
    await queryClient.invalidateQueries({ queryKey: materialKeys.lists() }); setDeleteOpen(false); navigate('/materials')
  }, onError: async (error) => { if (error instanceof ApiError && error.code === 'MATERIAL_NOT_FOUND' && id) await queryClient.invalidateQueries({ queryKey: materialKeys.detail(id) }) } })
  if (id === null || (material.error instanceof ApiError && material.error.code === 'MATERIAL_NOT_FOUND')) return <UnavailableMaterial />
  if (material.isPending) return <section className="materials-page"><Skeleton label="Loading Material" /><Skeleton /></section>
  if (material.isError) return <ErrorState title="Material could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void material.refetch()}>Try again</Button>} />
  const current = material.data
  const processingReady = processing.data !== undefined
  const processingStatus = processingReady ? displayProcessingStatus(processing.data.readiness) : displayMaterialStatus(current.status)
  const processingStage = processingReady ? displayProcessingStage(processing.data.stage) : null
  const processingLimitation = processingReady
    && (processing.data.readiness === 'PARTIALLY_READY' || processing.data.readiness === 'FAILED')
    ? processing.data.limitation
    : null
  const structureRoot = structure.data?.root

  return <section className="materials-page" aria-labelledby="material-title">
    <nav aria-label="Breadcrumb"><Link to="/materials">Materials</Link><span aria-hidden="true"> / </span><span>{current.title}</span></nav>
    <header className="materials-header"><div><p className="materials-eyebrow">Material</p><div className="materials-title-row"><h1 id="material-title">{current.title}</h1><Badge>{displayMaterialStatus(current.status)}</Badge></div></div><Button onClick={() => { deletion.reset(); setDeleteOpen(true) }} variant="tertiary">Delete material</Button></header>
    {processingReady ? (
      <section aria-live="polite" className="material-processing-panel">
        <h2>Processing</h2>
        <p>{processingStatus}</p>
        {processingStage ? <p>{processingStage}</p> : null}
        {processing.data.progress !== null ? <p>Current step: {Math.round(processing.data.progress)}%</p> : null}
        {processingLimitation ? <p>{processingLimitation}</p> : null}
      </section>
    ) : null}
    <dl className="material-detail-metadata">
      {current.originalFilename ? <><dt>Original file</dt><dd>{current.originalFilename}</dd></> : null}
      <dt>Material type</dt><dd>{current.materialType}</dd>
      {current.mimeType ? <><dt>File type</dt><dd>{current.mimeType}</dd></> : null}
      <dt>Status</dt><dd>{displayMaterialStatus(current.status)}</dd>
      <dt>Added</dt><dd>{new Date(current.createdAt).toLocaleString()}</dd>
      <dt>Last updated</dt><dd>{new Date(current.updatedAt).toLocaleString()}</dd>
    </dl>
    {structure.data?.available && structureRoot ? (
      <section>
        <h2>Structure</h2>
        <ul>
          {renderStructureTree(structureRoot, expandedNodes, setExpandedNodes)}
        </ul>
      </section>
    ) : null}
    <DeleteMaterialConfirmation name={current.title} open={deleteOpen} pending={deletion.isPending} error={deletion.error ? deleteErrorMessage(deletion.error) : undefined} onClose={() => setDeleteOpen(false)} onConfirm={() => deletion.mutate(current.id)} />
  </section>
}

function renderStructureTree(
  node: MaterialStructureNode,
  expanded: Record<string, boolean>,
  setExpanded: Dispatch<SetStateAction<Record<string, boolean>>>
) {
  const hasChildren = node.children.length > 0
  const expandedNode = expanded[node.id] ?? true
  const label = `${node.title ?? node.nodeType}${node.startPage !== null && node.endPage !== null ? ` (${node.startPage}-${node.endPage})` : ''}`
  return <li key={node.id}>
    {hasChildren ? (
      <button type="button" onClick={() => setExpanded((prev) => ({ ...prev, [node.id]: !expandedNode }))} aria-expanded={expandedNode}>
        {label}
      </button>
    ) : <span>{label}</span>}
    {hasChildren && expandedNode ? <ul>{node.children.map((child) => renderStructureTree(child, expanded, setExpanded))}</ul> : null}
  </li>
}

function UnavailableMaterial() { return <ErrorState title="Material unavailable" description="This material could not be found or is not available to you." action={<Link className="materials-link-action" to="/materials">Back to Materials</Link>} /> }
