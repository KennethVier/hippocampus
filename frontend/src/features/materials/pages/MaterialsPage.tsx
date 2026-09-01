import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Button, EmptyState, ErrorState, Skeleton } from '../../../components/ui'
import { useSearchParams } from 'react-router'
import { ApiError } from '../../../api/apiClient'
import { deleteMaterial, listMaterials, MATERIALS_PAGE_SIZE } from '../api/materialsApi'
import type { Material } from '../api/materialContracts'
import { DeleteMaterialConfirmation } from '../components/DeleteMaterialConfirmation'
import { MaterialCard } from '../components/MaterialCard'
import { MaterialsPagination } from '../components/MaterialsPagination'
import { MaterialUploadPanel } from '../components/MaterialUploadPanel'
import { deleteErrorMessage } from '../materialsErrors'
import { materialKeys } from '../queries/materialQueries'
import '../materials.css'

function validPage(value: string | null): number {
  if (value === null || !/^\d+$/.test(value)) return 1
  const page = Number(value); return Number.isSafeInteger(page) && page >= 1 ? page : 1
}

export function MaterialsPage() {
  const queryClient = useQueryClient(); const [params, setParams] = useSearchParams()
  const rawPage = params.get('page'); const page = validPage(rawPage)
  const [deleteTarget, setDeleteTarget] = useState<Material | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const materials = useQuery({ queryKey: materialKeys.list(page - 1, MATERIALS_PAGE_SIZE), queryFn: ({ signal }) => listMaterials(page - 1, MATERIALS_PAGE_SIZE, signal) })
  useEffect(() => { if (rawPage !== String(page)) setParams({ page: String(page) }, { replace: true }) }, [page, rawPage, setParams])
  useEffect(() => { if (materials.data && materials.data.totalPages > 0 && page > materials.data.totalPages) setParams({ page: String(materials.data.totalPages) }, { replace: true }) }, [materials.data, page, setParams])

  const deletion = useMutation({ mutationFn: deleteMaterial, onSuccess: async (_, id) => {
    queryClient.removeQueries({ queryKey: materialKeys.detail(id), exact: true }); setDeleteTarget(null)
    await queryClient.invalidateQueries({ queryKey: materialKeys.lists() }); headingRef.current?.focus()
  }, onError: async (error) => {
    if (error instanceof ApiError && error.code === 'MATERIAL_NOT_FOUND') {
      setDeleteTarget(null); await queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
    }
  } })
  async function accepted() {
    if (page !== 1) setParams({ page: '1' })
    await queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
  }
  return <section className="materials-page" aria-labelledby="materials-title">
    <header className="materials-header"><div><p className="materials-eyebrow">Learning library</p><h1 id="materials-title" ref={headingRef} tabIndex={-1}>Materials</h1><p>Add and manage the private source material you use for study.</p></div></header>
    <MaterialUploadPanel onAccepted={accepted} />
    <section className="materials-section" aria-labelledby="materials-list-title"><h2 id="materials-list-title">Your materials</h2>
      {materials.isPending ? <div className="materials-grid" aria-label="Loading Materials"><Skeleton label="Loading Materials" /><Skeleton /><Skeleton /></div> : null}
      {materials.isError ? <ErrorState title="Materials could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void materials.refetch()}>Try again</Button>} /> : null}
      {materials.data?.items.length === 0 ? <EmptyState title="No Materials yet" description="Add lecture notes, a PDF, image, or transcript when you're ready." /> : null}
      {materials.data?.items.length ? <div className="materials-grid">{materials.data.items.map((material) => <MaterialCard key={material.id} material={material} onDelete={() => { deletion.reset(); setDeleteTarget(material) }} />)}</div> : null}
      {materials.data ? <MaterialsPagination page={page} totalPages={materials.data.totalPages} onPage={(next) => setParams({ page: String(next) })} /> : null}
    </section>
    <DeleteMaterialConfirmation name={deleteTarget?.title ?? 'This material'} open={deleteTarget !== null} pending={deletion.isPending} error={deletion.error ? deleteErrorMessage(deletion.error) : undefined} onClose={() => setDeleteTarget(null)} onConfirm={() => { if (deleteTarget) deletion.mutate(deleteTarget.id) }} />
  </section>
}
