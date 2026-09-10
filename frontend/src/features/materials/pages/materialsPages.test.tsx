import { QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type UploadProgress } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import { clearPrivateClientState } from '../../auth/clearPrivateClientState'
import * as api from '../api/materialsApi'
import type { MaterialPage, MaterialProcessing, MaterialStructureNode, MaterialStructureResponse, MaterialUpload } from '../api/materialContracts'
import { processingPollingInterval } from '../hooks/useMaterialProcessing'
import { MaterialDetailPage } from './MaterialDetailPage'
import { MaterialsPage } from './MaterialsPage'

const id = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const material = { id, title: '<script>Private notes</script>', materialType: 'TEXT', originalFilename: null, mimeType: null, status: 'UPLOADED', activeVersionId: null, createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:01:00Z' }
const versionId = '9a7b3302-b431-45e1-90e3-298c9d80918f'
const upload: MaterialUpload = { materialId: id, versionId, title: 'notes.txt', materialType: 'TEXT', originalFilename: 'notes.txt', mimeType: 'text/plain', fileSizeBytes: 5, materialStatus: 'UPLOADED', processingStatus: 'UPLOADED', createdAt: '2026-09-01T10:00:00Z' }
const empty: MaterialPage = { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks() })

function renderAt(path: string) {
  const client = createAppQueryClient()
  const router = createMemoryRouter([{ path: '/materials', element: <MaterialsPage /> }, { path: '/materials/:materialId', element: <MaterialDetailPage /> }], { initialEntries: [path] })
  render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
  return { router, client }
}

function materialProcessing(readiness: string, overrides: Partial<MaterialProcessing> = {}): MaterialProcessing {
  return { materialId: id, versionId, readiness, stage: null, progress: null, limitation: null, structureAvailable: false, ...overrides }
}

function node(
  nodeId: string,
  nodeType: string,
  title: string,
  startPage: number,
  endPage: number,
  children: MaterialStructureNode[] = [],
): MaterialStructureNode {
  return { id: nodeId, nodeType, title, startPage, endPage, children }
}

function structure(root: MaterialStructureNode | null): MaterialStructureResponse {
  return { available: root !== null, root }
}

function mockDetail(processing: MaterialProcessing, tree = structure(null)) {
  vi.spyOn(api, 'getMaterial').mockResolvedValue({ ...material, status: processing.readiness })
  vi.spyOn(api, 'getMaterialStructure').mockResolvedValue(tree)
  return vi.spyOn(api, 'getMaterialProcessing').mockResolvedValue({ ...processing, structureAvailable: tree.available })
}

async function advanceTimers(milliseconds: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds)
  })
}

describe('Materials page', () => {
  it('renders loading, empty, error retry, and safe hostile card text', async () => {
    let resolve: ((value: MaterialPage) => void) | undefined; vi.spyOn(api, 'listMaterials').mockReturnValue(new Promise((done) => { resolve = done }))
    renderAt('/materials'); expect(screen.getByRole('status', { name: 'Loading Materials' })).toBeInTheDocument(); resolve?.(empty)
    expect(await screen.findByRole('heading', { name: 'No Materials yet' })).toBeInTheDocument(); cleanup()
    vi.spyOn(api, 'listMaterials').mockRejectedValueOnce(new Error()).mockResolvedValue(empty); renderAt('/materials'); fireEvent.click(await screen.findByRole('button', { name: 'Try again' })); expect(await screen.findByRole('heading', { name: 'No Materials yet' })).toBeInTheDocument(); cleanup()
    vi.spyOn(api, 'listMaterials').mockResolvedValue({ items: [material], page: 0, size: 12, totalElements: 1, totalPages: 1 }); renderAt('/materials'); expect(await screen.findByRole('heading', { name: material.title })).toBeInTheDocument(); expect(document.querySelector('script')).toBeNull(); expect(screen.queryByText(/Unknown|N\/A|null/)).not.toBeInTheDocument()
  })

  it('normalizes URL pagination and navigates bounded pages', async () => {
    vi.spyOn(api, 'listMaterials').mockResolvedValue({ items: [material], page: 0, size: 12, totalElements: 13, totalPages: 2 })
    const { router } = renderAt('/materials?page=1.5'); await screen.findByRole('heading', { name: material.title }); expect(router.state.location.search).toBe('?page=1')
    fireEvent.click(screen.getByRole('button', { name: 'Next' })); expect(router.state.location.search).toBe('?page=2')
  })

  it('provides a semantic picker, rejects multiple drops, and validates convenience MIME', async () => {
    vi.spyOn(api, 'listMaterials').mockResolvedValue(empty); renderAt('/materials')
    const input = await screen.findByLabelText('Choose file'); expect(input).toHaveAttribute('type', 'file'); expect(input).toHaveAttribute('accept', 'application/pdf,image/jpeg,image/png,text/plain')
    const zone = input.parentElement!; fireEvent.drop(zone, { dataTransfer: { files: [new File(['a'], 'a.txt', { type: 'text/plain' }), new File(['b'], 'b.txt', { type: 'text/plain' })] } }); expect(screen.getByRole('alert')).toHaveTextContent('one file')
    fireEvent.change(input, { target: { files: [new File(['x'], 'x.exe', { type: 'application/octet-stream' })] } }); expect(screen.getByRole('alert')).toHaveTextContent('PDF, JPEG, PNG')
  })

  it('renders transfer, finishing, then accepted and synchronizes authoritative list', async () => {
    vi.spyOn(api, 'listMaterials').mockImplementation(async (page) => page === 0
      ? { items: [{ ...material, title: 'notes.txt', originalFilename: 'notes.txt', mimeType: 'text/plain' }], page: 0, size: 12, totalElements: 1, totalPages: 1 }
      : { ...empty, page })
    let progress: ((value: UploadProgress) => void) | undefined; let accept: ((value: MaterialUpload) => void) | undefined
    vi.spyOn(api, 'uploadMaterial').mockImplementation((_file, onProgress) => { progress = onProgress; return new Promise((resolve) => { accept = resolve }) })
    const { router } = renderAt('/materials?page=3'); const input = await screen.findByLabelText('Choose file'); fireEvent.change(input, { target: { files: [new File(['notes'], 'notes.txt', { type: 'text/plain' })] } }); fireEvent.click(screen.getByRole('button', { name: 'Upload file' }))
    progress?.({ type: 'determinate', loadedBytes: 37, totalBytes: 100, percentage: 37 }); expect(await screen.findByText('Uploading 37%')).toBeInTheDocument(); expect(screen.queryByRole('heading', { name: 'Upload accepted' })).not.toBeInTheDocument()
    progress?.({ type: 'indeterminate', loadedBytes: 40 }); expect(await screen.findByText('Uploading…')).toBeInTheDocument()
    progress?.({ type: 'determinate', loadedBytes: 100, totalBytes: 100, percentage: 100 }); expect(await screen.findByText('Finishing upload…')).toBeInTheDocument(); expect(screen.queryByRole('heading', { name: 'Upload accepted' })).not.toBeInTheDocument()
    await act(async () => { accept?.(upload) }); expect(await screen.findByRole('heading', { name: 'Upload accepted' })).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.search).toBe('?page=1'))
    expect(await screen.findByRole('heading', { name: 'notes.txt' })).toBeInTheDocument()
  })

  it('maps upload errors, supports explicit retry/cancel, and aborts on private cleanup without late state', async () => {
    vi.spyOn(api, 'listMaterials').mockResolvedValue(empty); vi.spyOn(api, 'uploadMaterial').mockRejectedValueOnce(new ApiError({ kind: 'http', status: 413, code: 'UPLOAD_TOO_LARGE', message: 'private' })).mockResolvedValue(upload)
    const { client } = renderAt('/materials'); const input = await screen.findByLabelText('Choose file'); fireEvent.change(input, { target: { files: [new File(['notes'], 'notes.txt', { type: 'text/plain' })] } }); fireEvent.click(screen.getByRole('button', { name: 'Upload file' })); expect(await screen.findByRole('alert')).toHaveTextContent('upload limit'); fireEvent.click(screen.getByRole('button', { name: 'Try upload again' })); expect(await screen.findByRole('heading', { name: 'Upload accepted' })).toBeInTheDocument(); cleanup()
    let lateResolve: ((value: MaterialUpload) => void) | undefined; vi.spyOn(api, 'listMaterials').mockResolvedValue(empty); vi.spyOn(api, 'uploadMaterial').mockImplementation(() => new Promise((resolve) => { lateResolve = resolve })); const mounted = renderAt('/materials'); const second = await screen.findByLabelText('Choose file'); fireEvent.change(second, { target: { files: [new File(['USER_A'], 'USER_A.txt', { type: 'text/plain' })] } }); fireEvent.click(screen.getByRole('button', { name: 'Upload file' })); await clearPrivateClientState(mounted.client); lateResolve?.(upload); await waitFor(() => expect(screen.queryByText('USER_A.txt')).not.toBeInTheDocument()); expect(screen.queryByRole('heading', { name: 'Upload accepted' })).not.toBeInTheDocument(); expect(mounted.client.getQueryData(['materials', 'detail', id])).toBeUndefined(); expect(client).toBeDefined()
  })

  it('cancels explicitly and remounts without private filename state', async () => {
    vi.spyOn(api, 'listMaterials').mockResolvedValue(empty)
    vi.spyOn(api, 'uploadMaterial').mockImplementation((_file, _progress, signal) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(new ApiError({ kind: 'aborted', status: null, code: 'REQUEST_ABORTED', message: 'canceled' })), { once: true })
    }))
    renderAt('/materials'); const input = await screen.findByLabelText('Choose file')
    fireEvent.change(input, { target: { files: [new File(['USER_A'], 'USER_A.txt', { type: 'text/plain' })] } }); fireEvent.click(screen.getByRole('button', { name: 'Upload file' })); fireEvent.click(await screen.findByRole('button', { name: 'Cancel upload' }))
    expect(await screen.findByText('Upload canceled.')).toBeInTheDocument(); cleanup()
    renderAt('/materials'); await screen.findByLabelText('Choose file'); expect(screen.queryByText('USER_A.txt')).not.toBeInTheDocument(); expect(screen.queryByText('Upload canceled.')).not.toBeInTheDocument()
  })

  it('requires delete confirmation and protects pending deletion', async () => {
    vi.spyOn(api, 'listMaterials').mockResolvedValue({ items: [material], page: 0, size: 12, totalElements: 1, totalPages: 1 }); let resolveDelete: (() => void) | undefined; const deletion = vi.spyOn(api, 'deleteMaterial').mockImplementation(() => new Promise((resolve) => { resolveDelete = resolve }))
    renderAt('/materials'); fireEvent.click(await screen.findByRole('button', { name: 'Delete material' })); const dialog = screen.getByRole('dialog', { name: 'Delete material' }); expect(deletion).not.toHaveBeenCalled(); fireEvent.click(within(dialog).getByRole('button', { name: 'Delete material' })); await waitFor(() => expect(deletion).toHaveBeenCalledWith(id, expect.anything())); expect(within(dialog).getByRole('button', { name: 'Deleting…' })).toBeDisabled(); resolveDelete?.(); await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})

describe('Material detail', () => {
  it('shows only normal nullable metadata and conceals invalid/missing IDs', async () => {
    vi.spyOn(api, 'getMaterial').mockResolvedValue(material); vi.spyOn(api, 'getMaterialProcessing').mockRejectedValue(new Error()); vi.spyOn(api, 'getMaterialStructure').mockResolvedValue(structure(null)); renderAt(`/materials/${id}`); expect(await screen.findByRole('heading', { name: material.title })).toBeInTheDocument(); expect(screen.queryByText(/Unknown|N\/A|null/)).not.toBeInTheDocument(); expect(screen.queryByText(/bytes|processing/i)).not.toBeInTheDocument(); cleanup()
    renderAt('/materials/not-a-uuid'); expect(screen.getByRole('heading', { name: 'Material unavailable' })).toBeInTheDocument(); cleanup()
    vi.spyOn(api, 'getMaterial').mockRejectedValue(new ApiError({ kind: 'http', status: 404, code: 'MATERIAL_NOT_FOUND', message: 'hidden' })); renderAt(`/materials/${id}`); expect(await screen.findByRole('heading', { name: 'Material unavailable' })).toBeInTheDocument(); expect(screen.queryByText(id)).not.toBeInTheDocument()
  })

  it.each(['UPLOADED', 'PROCESSING'])('polls while readiness is %s', async (readiness) => {
    vi.useFakeTimers()
    const nextPoll = vi.fn()
    const interval = processingPollingInterval({ state: { data: materialProcessing(readiness), dataUpdateCount: 1 } })

    setTimeout(nextPoll, interval || 0)
    await advanceTimers(4_999)
    expect(nextPoll).not.toHaveBeenCalled()
    await advanceTimers(1)
    expect(nextPoll).toHaveBeenCalledTimes(1)
  })

  it('backs off polling at five ten twenty then thirty seconds capped', async () => {
    vi.useFakeTimers()
    const intervals = [1, 2, 3, 4, 5].map((dataUpdateCount) =>
      processingPollingInterval({ state: { data: materialProcessing('PROCESSING'), dataUpdateCount } }))
    expect(intervals).toEqual([5_000, 10_000, 20_000, 30_000, 30_000])

    const cappedPoll = vi.fn()
    setTimeout(cappedPoll, intervals.at(-1) || 0)
    await advanceTimers(29_999)
    expect(cappedPoll).not.toHaveBeenCalled()
    await advanceTimers(1)
    expect(cappedPoll).toHaveBeenCalledTimes(1)
  })

  it.each(['READY', 'PARTIALLY_READY', 'FAILED'])('stops polling on terminal readiness %s', (readiness) => {
    expect(processingPollingInterval({ state: { data: materialProcessing(readiness), dataUpdateCount: 1 } }))
      .toBe(false)
  })

  it('continues polling after a transient request failure', async () => {
    vi.useFakeTimers()
    const retryPoll = vi.fn()
    const interval = processingPollingInterval({
      state: { data: materialProcessing('PROCESSING'), dataUpdateCount: 1, fetchFailureCount: 1 },
    })
    expect(interval).toBe(10_000)

    setTimeout(retryPoll, interval || 0)
    await advanceTimers(9_999)
    expect(retryPoll).not.toHaveBeenCalled()
    await advanceTimers(1)
    expect(retryPoll).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['READY', 'Ready to study'],
    ['PARTIALLY_READY', 'Ready with limitations'],
    ['FAILED', 'Needs attention before study'],
  ])('renders controlled terminal readiness text for %s', async (readiness, label) => {
    mockDetail(materialProcessing(readiness, { limitation: 'Some pages or images could not be processed.' }))
    renderAt(`/materials/${id}`)
    expect(await screen.findByText(label)).toBeInTheDocument()
    expect(screen.queryByText(readiness)).not.toBeInTheDocument()
  })

  it('handles null progress as indeterminate without inventing a percent', async () => {
    mockDetail(materialProcessing('PROCESSING', { progress: null }))
    renderAt(`/materials/${id}`)
    expect(await screen.findByRole('heading', { name: 'Processing' })).toBeInTheDocument()
    expect(screen.queryByText('0%')).not.toBeInTheDocument()
    expect(screen.queryByText('Progress')).not.toBeInTheDocument()
  })

  it('fetches structure once when processing reports it became available', async () => {
    vi.useFakeTimers()
    const section = node('11111111-1111-4111-8111-111111111111', 'SECTION', '1.1 Cells', 2, 4)
    const root = node('33333333-3333-4333-8333-333333333333', 'DOCUMENT', 'Document', 1, 10, [section])
    const materialSpy = vi.spyOn(api, 'getMaterial').mockResolvedValue({ ...material, status: 'PROCESSING' })
    vi.spyOn(api, 'getMaterialProcessing')
      .mockResolvedValueOnce(materialProcessing('PROCESSING', { structureAvailable: false }))
      .mockResolvedValueOnce(materialProcessing('PROCESSING', { structureAvailable: true }))
    const structureSpy = vi.spyOn(api, 'getMaterialStructure').mockResolvedValue(structure(root))

    renderAt(`/materials/${id}`)
    await advanceTimers(0)
    expect(screen.getByRole('heading', { name: material.title })).toBeInTheDocument()
    expect(structureSpy).not.toHaveBeenCalled()

    await advanceTimers(10_000)
    await advanceTimers(0)

    expect(screen.getByRole('button', { name: 'Document (1-10)' })).toBeInTheDocument()
    expect(screen.getByText('1.1 Cells (2-4)')).toBeInTheDocument()
    expect(structureSpy).toHaveBeenCalledTimes(1)
    expect(materialSpy).toHaveBeenCalledTimes(1)
  })

  it('uses generic safe text for unknown readiness and hides raw internal jargon', async () => {
    mockDetail(materialProcessing('VECTOR_INDEX_BUILDING', {
      stage: 'OCR',
      limitation: 'RAW_INTERNAL_LIMITATION',
      progress: 44,
    }))
    renderAt(`/materials/${id}`)
    expect(await screen.findByText(/Processing status is being updated/)).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('VECTOR_INDEX_BUILDING')
    expect(document.body).not.toHaveTextContent('OCR')
    expect(document.body).not.toHaveTextContent('NATIVE')
    expect(document.body).not.toHaveTextContent('RAW_INTERNAL_LIMITATION')
  })

  it('renders nested structure collapse controls only for nodes with children', async () => {
    const section = node('11111111-1111-4111-8111-111111111111', 'SECTION', '1.1 Cells', 2, 4)
    const chapter = node('22222222-2222-4222-8222-222222222222', 'CHAPTER', '1 First', 1, 4, [section])
    const root = node('33333333-3333-4333-8333-333333333333', 'DOCUMENT', 'Document', 1, 10, [chapter])
    mockDetail(materialProcessing('READY'), structure(root))
    renderAt(`/materials/${id}`)

    const documentButton = await screen.findByRole('button', { name: 'Document (1-10)' })
    const chapterButton = screen.getByRole('button', { name: '1 First (1-4)' })
    expect(documentButton).toHaveAttribute('aria-expanded', 'true')
    expect(chapterButton).toHaveAttribute('aria-expanded', 'true')
    expect(screen.queryByRole('button', { name: '1.1 Cells (2-4)' })).not.toBeInTheDocument()
    expect(screen.getByText('1.1 Cells (2-4)').tagName).toBe('SPAN')
    expect(screen.getByText('1.1 Cells (2-4)')).not.toHaveAttribute('aria-expanded')

    fireEvent.click(chapterButton)
    expect(chapterButton).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('1.1 Cells (2-4)')).not.toBeInTheDocument()
  })
})
