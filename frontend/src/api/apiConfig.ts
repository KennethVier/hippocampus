const LOOPBACK_HOSTNAMES = new Set(['localhost', '127.0.0.1', '[::1]'])
const REQUEST_PATH_BASE = 'https://api.invalid'

export class ApiConfigurationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ApiConfigurationError'
  }
}

export class ApiRequestPathError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ApiRequestPathError'
  }
}

export function normalizeApiBaseUrl(value: string | undefined): string {
  const candidate = value?.trim()
  if (!candidate) {
    return ''
  }

  let url: URL
  try {
    url = new URL(candidate)
  } catch {
    throw new ApiConfigurationError('VITE_API_BASE_URL must be a valid HTTP(S) origin.')
  }

  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new ApiConfigurationError('VITE_API_BASE_URL must use HTTP or HTTPS.')
  }

  if (url.protocol === 'http:' && !LOOPBACK_HOSTNAMES.has(url.hostname)) {
    throw new ApiConfigurationError(
      'VITE_API_BASE_URL must use HTTPS outside local development.',
    )
  }

  if (url.username || url.password) {
    throw new ApiConfigurationError('VITE_API_BASE_URL must not contain credentials.')
  }

  if (url.pathname !== '/' || url.search || url.hash) {
    throw new ApiConfigurationError(
      'VITE_API_BASE_URL must be an origin without a path, query, or fragment.',
    )
  }

  return url.origin
}

export function resolveApiUrl(
  path: string,
  baseUrl = normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
): string {
  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) {
    throw new ApiRequestPathError('API request paths must be root-relative.')
  }

  const parsedPath = new URL(path, REQUEST_PATH_BASE)
  if (parsedPath.origin !== REQUEST_PATH_BASE || parsedPath.hash) {
    throw new ApiRequestPathError(
      'API request paths must be root-relative and must not contain fragments.',
    )
  }

  const normalizedPath = `${parsedPath.pathname}${parsedPath.search}`
  return baseUrl ? `${baseUrl}${normalizedPath}` : normalizedPath
}
