import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'

const transport = vi.hoisted(() => ({ json: vi.fn(), multipart: vi.fn() }))
vi.mock('../../../api/apiClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../../api/apiClient')>()
  return { ...original, apiClient: { requestJson: transport.json, requestMultipart: vi.fn(), requestMultipartWithProgress: transport.multipart } }
})

import { deleteMaterial, getMaterial, listMaterials, uploadMaterial } from './materialsApi'
const id = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'; const versionId = '9a7b3302-b431-45e1-90e3-298c9d80918f'
const material = { id, title: 'Notes', materialType: 'TEXT', originalFilename: null, mimeType: null, status: 'FUTURE_NONEMPTY_STATUS', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:01Z' }
const upload = { materialId: id, versionId, title: 'Notes', materialType: 'TEXT', originalFilename: null, mimeType: 'text/plain', fileSizeBytes: 5, materialStatus: 'UPLOADED', processingStatus: 'UPLOADED', createdAt: '2026-09-01T10:00:00Z' }
afterEach(() => { transport.json.mockReset(); transport.multipart.mockReset() })

describe('materials API contracts', () => {
  it('accepts nullable normal metadata and unknown nonempty status', async () => { transport.json.mockResolvedValue(material); await expect(getMaterial(id)).resolves.toEqual(material) })
  it('parses a valid bounded page', async () => { const page = { items: [material], page: 0, size: 12, totalElements: 1, totalPages: 1 }; transport.json.mockResolvedValue(page); await expect(listMaterials(0, 12)).resolves.toEqual(page) })
  it.each([{ ...material, id: 'bad' }, { ...material, createdAt: 'yesterday' }, undefined])('rejects malformed or missing detail %#', async (value) => { transport.json.mockResolvedValue(value); await expect(getMaterial(id)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' }) })
  it('rejects invalid pagination', async () => { transport.json.mockResolvedValue({ items: [], page: -1, size: 101, totalElements: -1, totalPages: -1 }); await expect(listMaterials(0, 12)).rejects.toBeInstanceOf(ApiError) })
  it('uploads exactly one file and requires valid upload MIME/schema', async () => {
    transport.multipart.mockResolvedValue(upload); const file = new File(['notes'], 'notes.txt', { type: 'text/plain' }); const signal = new AbortController().signal
    await expect(uploadMaterial(file, vi.fn(), signal)).resolves.toEqual(upload); const form = transport.multipart.mock.calls[0]?.[1] as FormData; expect(form.getAll('file')).toEqual([file]); expect(transport.multipart.mock.calls[0]?.[2]).toMatchObject({ expectedStatus: 201, signal })
    transport.multipart.mockResolvedValue({ ...upload, mimeType: '' }); await expect(uploadMaterial(file, vi.fn(), signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('permits nullable upload filename but rejects UUID/timestamp/missing upload body', async () => {
    transport.multipart.mockResolvedValue(upload); const file = new File(['a'], 'a.txt'); const signal = new AbortController().signal
    await expect(uploadMaterial(file, vi.fn(), signal)).resolves.toMatchObject({ originalFilename: null })
    for (const value of [{ ...upload, versionId: 'bad' }, { ...upload, createdAt: 'bad' }, undefined]) { transport.multipart.mockResolvedValue(value); await expect(uploadMaterial(file, vi.fn(), signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' }) }
  })
  it('deletes through the centralized empty 204 contract', async () => { transport.json.mockResolvedValue(undefined); await deleteMaterial(id); expect(transport.json).toHaveBeenCalledWith(`/api/materials/${id}`, { method: 'DELETE' }) })
})
