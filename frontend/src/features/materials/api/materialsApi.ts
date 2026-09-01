import { ApiError, apiClient, type UploadProgress } from '../../../api/apiClient'
import { materialPageSchema, materialSchema, materialUploadSchema, type Material, type MaterialPage, type MaterialUpload } from './materialContracts'

export const MATERIALS_PAGE_SIZE = 12

function required(value: unknown, status = 200): unknown {
  if (value !== undefined) return value
  throw new ApiError({ kind: 'invalid-response', status, code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' })
}
function parse<T>(schema: { safeParse(value: unknown): { success: true; data: T } | { success: false } }, value: unknown, status = 200): T {
  const result = schema.safeParse(value)
  if (result.success) return result.data
  throw new ApiError({ kind: 'invalid-response', status, code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' })
}

export async function listMaterials(page: number, size: number, signal?: AbortSignal): Promise<MaterialPage> {
  const response = await apiClient.requestJson<unknown>(`/api/materials?page=${page}&size=${size}`, { signal })
  return parse(materialPageSchema, required(response))
}
export async function getMaterial(materialId: string, signal?: AbortSignal): Promise<Material> {
  const response = await apiClient.requestJson<unknown>(`/api/materials/${encodeURIComponent(materialId)}`, { signal })
  return parse(materialSchema, required(response))
}
export async function uploadMaterial(file: File, onProgress: (progress: UploadProgress) => void, signal: AbortSignal): Promise<MaterialUpload> {
  const form = new FormData(); form.append('file', file)
  const response = await apiClient.requestMultipartWithProgress<unknown>('/api/materials', form, { expectedStatus: 201, onProgress, signal })
  return parse(materialUploadSchema, required(response, 201), 201)
}
export async function deleteMaterial(materialId: string): Promise<void> {
  await apiClient.requestJson(`/api/materials/${encodeURIComponent(materialId)}`, { method: 'DELETE' })
}
