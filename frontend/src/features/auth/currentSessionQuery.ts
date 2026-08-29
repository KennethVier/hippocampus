import { queryOptions } from '@tanstack/react-query'

import { ApiError } from '../../api/apiClient'
import { getCurrentSession } from './authApi'

export const currentSessionQueryKey = ['auth', 'current-session'] as const

export const currentSessionQueryOptions = queryOptions({
  queryKey: currentSessionQueryKey,
  queryFn: ({ signal }) => getCurrentSession(signal),
  staleTime: 0,
  refetchOnMount: 'always',
  refetchOnWindowFocus: true,
  retry(failureCount, error) {
    if (!(error instanceof ApiError) || failureCount >= 1) return false
    return error.kind === 'network' || (error.kind === 'http' && (error.status ?? 0) >= 500)
  },
  retryDelay: 100,
})

export function isAuthenticationRequired(error: unknown): boolean {
  return error instanceof ApiError
    && error.status === 401
    && error.code === 'AUTHENTICATION_REQUIRED'
}
