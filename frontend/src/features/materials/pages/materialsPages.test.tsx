import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type UploadProgress } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import { clearPrivateClientState } from '../../auth/clearPrivateClientState'
import * as api from '../api/materialsApi'
import type { MaterialPage, MaterialUpload } from '../api/materialContracts'
import { MaterialDetailPage } from './MaterialDetailPage'
import { MaterialsPage } from './MaterialsPage'

const id = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const material = { id, title: '<script>Private notes</script>', materialType: 'TEXT', originalFilename: null, mimeType: null, status: 'UPLOADED', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:01:00Z' }
const upload: MaterialUpload = { materialId: id, versionId: '9a7b3302-b431-45e1-90e3-298c9d80918f', title: 'notes.txt', materialType: 'TEXT', originalFilename: 'notes.txt', mimeType: 'text/plain', fileSizeBytes: 5, materialStatus: 'UPLOADED', processingStatus: 'UPLOADED', createdAt: '2026-09-01T10:00:00Z' }
const empty: MaterialPage = { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }
afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderAt(path: string) {
  const client = createAppQueryClient()
  const router = createMemoryRouter([{ path: '/materials', element: <MaterialsPage /> }, { path: '/materials/:materialId', element: <MaterialDetailPage /> }], { initialEntries: [path] })
  render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
  return { router, client }
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
    vi.spyOn(api, 'listMaterials').mockResolvedValueOnce(empty).mockResolvedValue({ items: [{ ...material, title: 'notes.txt', originalFilename: 'notes.txt', mimeType: 'text/plain' }], page: 0, size: 12, totalElements: 1, totalPages: 1 })
    let progress: ((value: UploadProgress) => void) | undefined; let accept: ((value: MaterialUpload) => void) | undefined
    vi.spyOn(api, 'uploadMaterial').mockImplementation((_file, onProgress) => { progress = onProgress; return new Promise((resolve) => { accept = resolve }) })
    renderAt('/materials?page=3'); const input = await screen.findByLabelText('Choose file'); fireEvent.change(input, { target: { files: [new File(['notes'], 'notes.txt', { type: 'text/plain' })] } }); fireEvent.click(screen.getByRole('button', { name: 'Upload file' }))
    progress?.({ type: 'determinate', loadedBytes: 37, totalBytes: 100, percentage: 37 }); expect(await screen.findByText('Uploading 37%')).toBeInTheDocument(); expect(screen.queryByRole('heading', { name: 'Upload accepted' })).not.toBeInTheDocument()
    progress?.({ type: 'indeterminate', loadedBytes: 40 }); expect(await screen.findByText('Uploading…')).toBeInTheDocument()
    progress?.({ type: 'determinate', loadedBytes: 100, totalBytes: 100, percentage: 100 }); expect(await screen.findByText('Finishing upload…')).toBeInTheDocument(); expect(screen.queryByRole('heading', { name: 'Upload accepted' })).not.toBeInTheDocument()
    accept?.(upload); expect(await screen.findByRole('heading', { name: 'Upload accepted' })).toBeInTheDocument(); expect(await screen.findByRole('heading', { name: 'notes.txt' })).toBeInTheDocument()
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
    vi.spyOn(api, 'getMaterial').mockResolvedValue(material); renderAt(`/materials/${id}`); expect(await screen.findByRole('heading', { name: material.title })).toBeInTheDocument(); expect(screen.queryByText(/Unknown|N\/A|null/)).not.toBeInTheDocument(); expect(screen.queryByText(/bytes|processing/i)).not.toBeInTheDocument(); cleanup()
    renderAt('/materials/not-a-uuid'); expect(screen.getByRole('heading', { name: 'Material unavailable' })).toBeInTheDocument(); cleanup()
    vi.spyOn(api, 'getMaterial').mockRejectedValue(new ApiError({ kind: 'http', status: 404, code: 'MATERIAL_NOT_FOUND', message: 'hidden' })); renderAt(`/materials/${id}`); expect(await screen.findByRole('heading', { name: 'Material unavailable' })).toBeInTheDocument(); expect(screen.queryByText(id)).not.toBeInTheDocument()
  })
})
