import { ApiConfigurationError, resolveApiUrl } from './apiConfig'

const ACCEPT_HEADER_VALUE = 'application/json, application/problem+json'
const CORRELATION_ID_HEADER = 'X-Correlation-ID'
const CONTROLLED_HEADERS = ['accept', 'content-type', 'authorization', 'x-csrf-token'] as const
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/
const CORRELATION_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const EMPTY_DETAILS: Readonly<Record<string, unknown>> = Object.freeze({})

export type ApiErrorKind =
  | 'http'
  | 'network'
  | 'aborted'
  | 'invalid-response'
  | 'request'

export interface ApiRequestOptions {
  method?: string
  headers?: HeadersInit
  signal?: AbortSignal
}

export interface JsonRequestOptions extends ApiRequestOptions {
  body?: unknown
}

export type UploadProgress =
  | { readonly type: 'determinate'; readonly loadedBytes: number; readonly totalBytes: number; readonly percentage: number }
  | { readonly type: 'indeterminate'; readonly loadedBytes: number }

export interface MultipartProgressOptions extends ApiRequestOptions {
  expectedStatus?: number
  onProgress?: (progress: UploadProgress) => void
}

interface ApiErrorFields {
  kind: ApiErrorKind
  status: number | null
  code: string
  message: string
  correlationId?: string | null
  details?: Readonly<Record<string, unknown>>
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null
  readonly code: string
  readonly correlationId: string | null
  readonly details: Readonly<Record<string, unknown>>

  constructor(fields: ApiErrorFields) {
    super(fields.message)
    this.name = 'ApiError'
    this.kind = fields.kind
    this.status = fields.status
    this.code = fields.code
    this.correlationId = fields.correlationId ?? null
    this.details = fields.details ?? EMPTY_DETAILS
  }
}

async function requestJson<TResponse>(
  path: string,
  options: JsonRequestOptions = {},
): Promise<TResponse | undefined> {
  const hasBody = options.body !== undefined
  const headers = createHeaders(options.headers, hasBody)
  const body = hasBody ? serializeJsonBody(options.body) : undefined
  const method = effectiveMethod(options.method, 'GET')
  await addCsrfHeaderIfUnsafe(method, headers, options.signal)

  return executeRequest<TResponse>(path, {
    method,
    headers,
    signal: options.signal,
    credentials: 'include',
    body,
  })
}

async function requestMultipart<TResponse>(
  path: string,
  body: FormData,
  options: ApiRequestOptions = {},
): Promise<TResponse | undefined> {
  const headers = createHeaders(options.headers, false)
  const method = effectiveMethod(options.method, 'POST')
  await addCsrfHeaderIfUnsafe(method, headers, options.signal)

  return executeRequest<TResponse>(path, {
    method,
    headers,
    signal: options.signal,
    credentials: 'include',
    body,
  })
}

async function requestMultipartWithProgress<TResponse>(
  path: string,
  body: FormData,
  options: MultipartProgressOptions = {},
): Promise<TResponse | undefined> {
  const headers = createHeaders(options.headers, false)
  const method = effectiveMethod(options.method, 'POST')
  if (options.signal?.aborted) throw abortedError()
  await addCsrfHeaderIfUnsafe(method, headers, options.signal)
  if (options.signal?.aborted) throw abortedError()

  const url = resolvedRequestUrl(path)
  return executeProgressRequest<TResponse>(url, body, method, headers, options)
}

function effectiveMethod(method: string | undefined, defaultMethod: string): string {
  return (method ?? defaultMethod).toUpperCase()
}

function isUnsafeMethod(method: string): boolean {
  return !SAFE_METHODS.has(method)
}

async function addCsrfHeaderIfUnsafe(
  method: string,
  headers: Headers,
  signal: AbortSignal | undefined,
): Promise<void> {
  if (!isUnsafeMethod(method)) {
    return
  }

  const token = await acquireCsrfToken(signal)
  headers.set('X-CSRF-TOKEN', token)
}

async function acquireCsrfToken(signal: AbortSignal | undefined): Promise<string> {
  const response = await executeRequest<unknown>('/api/auth/csrf', {
    method: 'GET',
    headers: createHeaders(undefined, false),
    signal,
    credentials: 'include',
  })

  if (!isRecord(response) || typeof response.token !== 'string' || !response.token.trim()) {
    throw invalidResponse(200, null)
  }

  return response.token.trim()
}

export const apiClient = Object.freeze({
  requestJson,
  requestMultipart,
  requestMultipartWithProgress,
})

function serializeJsonBody(body: unknown): string {
  try {
    const serialized = JSON.stringify(body)
    if (serialized === undefined) {
      throw new TypeError()
    }
    return serialized
  } catch {
    throw apiError({
      kind: 'request',
      status: null,
      code: 'REQUEST_SERIALIZATION_FAILED',
      message: 'The request body could not be serialized.',
    })
  }
}

function createHeaders(input: HeadersInit | undefined, hasJsonBody: boolean): Headers {
  let headers: Headers
  try {
    headers = new Headers(input)
  } catch {
    throw apiError({
      kind: 'request',
      status: null,
      code: 'INVALID_REQUEST_HEADERS',
      message: 'The request headers are invalid.',
    })
  }

  if (CONTROLLED_HEADERS.some((header) => headers.has(header))) {
    throw apiError({
      kind: 'request',
      status: null,
      code: 'REQUEST_HEADER_NOT_ALLOWED',
      message: 'The request includes a header managed by the API client.',
    })
  }

  headers.set('Accept', ACCEPT_HEADER_VALUE)
  if (hasJsonBody) {
    headers.set('Content-Type', 'application/json')
  }

  return headers
}

async function executeRequest<TResponse>(
  path: string,
  init: RequestInit,
): Promise<TResponse | undefined> {
  const url = resolvedRequestUrl(path)

  let response: Response
  try {
    response = await fetch(url, init)
  } catch (error: unknown) {
    if (init.signal?.aborted || isAbortError(error)) {
      throw abortedError()
    }

    throw apiError({
      kind: 'network',
      status: null,
      code: 'NETWORK_ERROR',
      message: 'Unable to reach the server.',
    })
  }

  return parseResponse<TResponse>(response)
}

async function parseResponse<TResponse>(response: Response): Promise<TResponse | undefined> {
  let body: string
  try {
    body = await response.text()
  } catch {
    if (!response.ok) throw httpFallback(response.status, normalizeCorrelationId(response.headers.get(CORRELATION_ID_HEADER)))
    throw invalidResponse(response.status, normalizeCorrelationId(response.headers.get(CORRELATION_ID_HEADER)))
  }
  return normalizeResponse<TResponse>({ status: response.status, contentType: response.headers.get('Content-Type'), correlationId: response.headers.get(CORRELATION_ID_HEADER), body })
}

interface ResponseParts { readonly status: number; readonly contentType: string | null; readonly correlationId: string | null; readonly body: string }

function normalizeResponse<TResponse>(parts: ResponseParts, expectedStatus?: number): TResponse | undefined {
  const correlationId = normalizeCorrelationId(parts.correlationId)
  const successful = parts.status >= 200 && parts.status < 300
  if (successful && expectedStatus !== undefined && parts.status !== expectedStatus) {
    throw invalidResponse(parts.status, correlationId)
  }
  if (!successful) return parseFailure(parts, correlationId)
  if (parts.status === 204) return undefined
  if (!parts.body.trim()) return undefined
  if (!isJsonContentType(parts.contentType)) throw invalidResponse(parts.status, correlationId)
  try {
    return JSON.parse(parts.body) as TResponse
  } catch {
    throw invalidResponse(parts.status, correlationId)
  }
}

function parseFailure(parts: ResponseParts, headerCorrelationId: string | null): never {
  if (!isProblemContentType(parts.contentType) || !parts.body.trim()) throw httpFallback(parts.status, headerCorrelationId)

  let problem: unknown
  try {
    problem = JSON.parse(parts.body)
  } catch {
    throw httpFallback(parts.status, headerCorrelationId)
  }

  if (!isRecord(problem)) throw httpFallback(parts.status, headerCorrelationId)

  const { status, code, message, correlationId, details } = problem
  if (
    !Number.isInteger(status) ||
    status !== parts.status ||
    typeof code !== 'string' ||
    !ERROR_CODE_PATTERN.test(code) ||
    typeof message !== 'string' ||
    !message.trim()
  ) {
    throw httpFallback(parts.status, headerCorrelationId)
  }

  throw apiError({
    kind: 'http',
    status: parts.status,
    code,
    message: message.trim(),
    correlationId: headerCorrelationId ?? normalizeCorrelationId(correlationId),
    details: isRecord(details) ? Object.freeze({ ...details }) : EMPTY_DETAILS,
  })
}

function executeProgressRequest<TResponse>(
  url: string,
  body: FormData,
  method: string,
  headers: Headers,
  options: MultipartProgressOptions,
): Promise<TResponse | undefined> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    let settled = false
    const signal = options.signal
    const cleanup = () => {
      signal?.removeEventListener('abort', abort)
      xhr.onload = null; xhr.onerror = null; xhr.onabort = null; xhr.upload.onprogress = null
    }
    const finish = (action: () => void) => {
      if (settled) return
      settled = true; cleanup(); action()
    }
    const abort = () => { if (!settled) xhr.abort() }

    xhr.open(method, url)
    xhr.withCredentials = true
    headers.forEach((value, name) => xhr.setRequestHeader(name, value))
    xhr.upload.onprogress = (event) => {
      if (settled) return
      if (event.lengthComputable && event.total > 0) {
        const percentage = Math.min(100, Math.max(0, Math.floor((event.loaded / event.total) * 100)))
        options.onProgress?.({ type: 'determinate', loadedBytes: event.loaded, totalBytes: event.total, percentage })
      } else options.onProgress?.({ type: 'indeterminate', loadedBytes: event.loaded })
    }
    xhr.onload = () => finish(() => {
      try {
        resolve(normalizeResponse<TResponse>({ status: xhr.status, contentType: xhr.getResponseHeader('Content-Type'), correlationId: xhr.getResponseHeader(CORRELATION_ID_HEADER), body: xhr.responseText }, options.expectedStatus))
      } catch (error: unknown) { reject(error) }
    })
    xhr.onerror = () => finish(() => reject(apiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'Unable to reach the server.' })))
    xhr.onabort = () => finish(() => reject(abortedError()))
    signal?.addEventListener('abort', abort, { once: true })
    xhr.send(body)
  })
}

function resolvedRequestUrl(path: string): string {
  try { return resolveApiUrl(path) }
  catch (error: unknown) {
    if (error instanceof ApiConfigurationError) throw apiError({ kind: 'request', status: null, code: 'INVALID_API_CONFIGURATION', message: 'The API client configuration is invalid.' })
    throw apiError({ kind: 'request', status: null, code: 'INVALID_REQUEST_PATH', message: 'The API request path is invalid.' })
  }
}

function abortedError(): ApiError {
  return apiError({ kind: 'aborted', status: null, code: 'REQUEST_ABORTED', message: 'The request was canceled.' })
}

function httpFallback(status: number, correlationId: string | null): ApiError {
  return apiError({
    kind: 'http',
    status,
    code: 'HTTP_ERROR',
    message: 'The request could not be completed.',
    correlationId,
  })
}

function invalidResponse(status: number, correlationId: string | null): ApiError {
  return apiError({
    kind: 'invalid-response',
    status,
    code: 'INVALID_RESPONSE',
    message: 'The server returned an invalid response.',
    correlationId,
  })
}

function apiError(fields: ApiErrorFields): ApiError {
  return new ApiError(fields)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function normalizeCorrelationId(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const candidate = value.trim()
  return CORRELATION_ID_PATTERN.test(candidate) ? candidate.toLowerCase() : null
}

function normalizedContentType(value: string | null): string {
  return value?.split(';', 1)[0]?.trim().toLowerCase() ?? ''
}

function isProblemContentType(value: string | null): boolean {
  return normalizedContentType(value) === 'application/problem+json'
}

function isJsonContentType(value: string | null): boolean {
  const contentType = normalizedContentType(value)
  return contentType === 'application/json' || contentType.endsWith('+json')
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
