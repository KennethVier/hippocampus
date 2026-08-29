import { ApiError, apiClient } from '../../api/apiClient'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export interface CurrentSession {
  readonly userId: string
}

export interface LoginCredentials {
  readonly email: string
  readonly password: string
}

export async function getCurrentSession(signal?: AbortSignal): Promise<CurrentSession> {
  const response = await apiClient.requestJson<unknown>('/api/auth/me', { signal })
  if (!isRecord(response) || typeof response.userId !== 'string' || !UUID_PATTERN.test(response.userId)) {
    throw new ApiError({
      kind: 'invalid-response',
      status: 200,
      code: 'INVALID_RESPONSE',
      message: 'The server returned an invalid response.',
    })
  }
  return { userId: response.userId.toLowerCase() }
}

export async function login(credentials: LoginCredentials): Promise<void> {
  await apiClient.requestJson('/api/auth/login', { method: 'POST', body: credentials })
}

export async function logout(): Promise<void> {
  await apiClient.requestJson('/api/auth/logout', { method: 'POST' })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
