import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiClient } from './apiClient'

const CORRELATION_ID = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const BODY_CORRELATION_ID = '8f14e45f-ea3d-4b7e-8bb2-f1b9785f6f75'
const ACCEPT_HEADER_VALUE = 'application/json, application/problem+json'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

class FakeXhr {
  static instances: FakeXhr[] = []
  method = ''; url = ''; withCredentials = false; sent: Document | XMLHttpRequestBodyInit | null = null
  status = 0; responseText = ''; headers = new Map<string, string>(); requestHeaders = new Map<string, string>()
  upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null; onerror: (() => void) | null = null; onabort: (() => void) | null = null
  constructor() { FakeXhr.instances.push(this) }
  open(method: string, url: string) { this.method = method; this.url = url }
  setRequestHeader(name: string, value: string) { this.requestHeaders.set(name.toLowerCase(), value) }
  getResponseHeader(name: string) { return this.headers.get(name.toLowerCase()) ?? null }
  send(body?: Document | XMLHttpRequestBodyInit | null) { this.sent = body ?? null }
  abort() { this.onabort?.() }
  respond(status: number, body: string, contentType = 'application/json') { this.status = status; this.responseText = body; this.headers.set('content-type', contentType); this.onload?.() }
  progress(loaded: number, total: number, lengthComputable = true) { this.upload.onprogress?.({ loaded, total, lengthComputable } as ProgressEvent) }
}

describe('apiClient progress multipart transport', () => {
  function install() { FakeXhr.instances = []; vi.stubGlobal('XMLHttpRequest', FakeXhr) }
  function csrf() { vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockImplementation(async () => csrfResponse('private-csrf-token'))) }

  it('acquires CSRF before starting a credentialed XHR and sends the original FormData without Content-Type', async () => {
    install(); csrf(); const form = new FormData(); form.append('file', new Blob(['notes']), 'notes.txt')
    const promise = apiClient.requestMultipartWithProgress('/api/materials', form, { expectedStatus: 201 })
    expect(FakeXhr.instances).toHaveLength(0); await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(1))
    const xhr = FakeXhr.instances[0]!; expect(xhr.method).toBe('POST'); expect(xhr.url).toMatch(/\/api\/materials$/); expect(xhr.withCredentials).toBe(true)
    expect(xhr.sent).toBe(form); expect(xhr.requestHeaders.get('accept')).toBe(ACCEPT_HEADER_VALUE); expect(xhr.requestHeaders.get('x-csrf-token')).toBe('private-csrf-token'); expect(xhr.requestHeaders.has('content-type')).toBe(false)
    xhr.respond(201, JSON.stringify({ materialId: 'material-1' })); await expect(promise).resolves.toEqual({ materialId: 'material-1' })
  })

  it('resolves the configured origin and maps determinate/indeterminate progress without settling at 100%', async () => {
    install(); csrf(); vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test'); const progress = vi.fn(); let settled = false
    const promise = apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { expectedStatus: 201, onProgress: progress }).finally(() => { settled = true })
    await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(1)); const xhr = FakeXhr.instances[0]!
    expect(xhr.url).toBe('https://api.example.test/api/materials'); xhr.progress(37, 100); xhr.progress(3, 0, false); xhr.progress(100, 100); await Promise.resolve(); expect(settled).toBe(false)
    expect(progress.mock.calls.map((call) => call[0])).toEqual([{ type: 'determinate', loadedBytes: 37, totalBytes: 100, percentage: 37 }, { type: 'indeterminate', loadedBytes: 3 }, { type: 'determinate', loadedBytes: 100, totalBytes: 100, percentage: 100 }])
    xhr.respond(201, '{}'); await promise
  })

  it.each([200, 202])('rejects successful status %s when 201 is expected', async (status) => {
    install(); csrf(); const promise = apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { expectedStatus: 201 })
    await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(1)); FakeXhr.instances[0]!.respond(status, '{"private":"PRIVATE_BODY"}')
    const error = await captureApiError(promise); expect(error).toMatchObject({ kind: 'invalid-response', status, code: 'INVALID_RESPONSE' }); expect(JSON.stringify(error)).not.toContain('PRIVATE_BODY')
  })

  it('shares malformed success, ProblemDetail, fallback, and correlation normalization', async () => {
    install(); csrf(); const malformed = apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { expectedStatus: 201 })
    await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(1)); FakeXhr.instances[0]!.respond(201, '{bad'); expect((await captureApiError(malformed)).code).toBe('INVALID_RESPONSE')
    const problem = apiClient.requestMultipartWithProgress('/api/materials', new FormData())
    await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(2)); const second = FakeXhr.instances[1]!; second.headers.set('x-correlation-id', CORRELATION_ID); second.respond(413, JSON.stringify({ status: 413, code: 'UPLOAD_TOO_LARGE', message: 'Too large.', details: {} }), 'application/problem+json')
    expect(await captureApiError(problem)).toMatchObject({ code: 'UPLOAD_TOO_LARGE', correlationId: CORRELATION_ID })
    const fallback = apiClient.requestMultipartWithProgress('/api/materials', new FormData()); await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(3)); FakeXhr.instances[2]!.respond(500, 'PRIVATE_RAW', 'text/plain'); expect(await captureApiError(fallback)).toMatchObject({ code: 'HTTP_ERROR' })
  })

  it('normalizes network errors, pre-abort, active abort, and settles once', async () => {
    install(); csrf(); const network = apiClient.requestMultipartWithProgress('/api/materials', new FormData()); await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(1)); FakeXhr.instances[0]!.onerror?.(); expect((await captureApiError(network)).code).toBe('NETWORK_ERROR')
    const before = new AbortController(); before.abort(); expect((await captureApiError(apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { signal: before.signal }))).code).toBe('REQUEST_ABORTED')
    const active = new AbortController(); const pending = apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { signal: active.signal }); await vi.waitFor(() => expect(FakeXhr.instances).toHaveLength(2)); const xhr = FakeXhr.instances[1]!; active.abort(); xhr.onerror?.(); expect((await captureApiError(pending)).code).toBe('REQUEST_ABORTED')
  })

  it('does not start XHR when CSRF acquisition fails and keeps controlled headers forbidden', async () => {
    install(); vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(new Response('', { status: 500 })))
    await expect(apiClient.requestMultipartWithProgress('/api/materials', new FormData())).rejects.toBeInstanceOf(ApiError); expect(FakeXhr.instances).toHaveLength(0)
    csrf(); const error = await captureApiError(apiClient.requestMultipartWithProgress('/api/materials', new FormData(), { headers: { Authorization: 'Bearer PRIVATE_TOKEN' } })); expect(error.code).toBe('REQUEST_HEADER_NOT_ALLOWED'); expect(JSON.stringify(error)).not.toContain('PRIVATE_TOKEN')
  })
})

describe('apiClient success handling', () => {
  it('returns a successful JSON response with owned headers and credentials', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(csrfResponse('synthetic-csrf-token'))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'subject-1' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json; charset=utf-8' },
        }),
      )
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiClient.requestJson<{ id: string }>('/subjects', {
      method: 'POST',
      signal: controller.signal,
      body: { name: 'Anatomy' },
    })

    expect(result).toEqual({ id: 'subject-1' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [csrfUrl, csrfInit] = fetchMock.mock.calls[0] ?? []
    const [url, init] = fetchMock.mock.calls[1] ?? []
    const csrfHeaders = new Headers(csrfInit?.headers)
    const headers = new Headers(init?.headers)
    expect(csrfUrl).toBe('/api/auth/csrf')
    expect(csrfInit?.method).toBe('GET')
    expect(csrfInit?.credentials).toBe('include')
    expect(csrfInit?.signal).toBe(controller.signal)
    expect(csrfHeaders.get('Accept')).toBe(ACCEPT_HEADER_VALUE)
    expect(csrfHeaders.has('Content-Type')).toBe(false)
    expect(url).toBe('/subjects')
    expect(init?.credentials).toBe('include')
    expect(init?.signal).toBe(controller.signal)
    expect(init?.body).toBe(JSON.stringify({ name: 'Anatomy' }))
    expect(headers.get('Accept')).toBe(ACCEPT_HEADER_VALUE)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-CSRF-TOKEN')).toBe('synthetic-csrf-token')
  })

  it('returns undefined for 204 and empty successful responses', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiClient.requestJson('/first')).resolves.toBeUndefined()
    await expect(apiClient.requestJson('/second')).resolves.toBeUndefined()
  })

  it('does not set Content-Type when a JSON request has no body', async () => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }))

    await apiClient.requestJson('/subjects')

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers)
    expect(headers.get('Accept')).toBe(ACCEPT_HEADER_VALUE)
    expect(headers.has('Content-Type')).toBe(false)
  })

  it('passes FormData unchanged and leaves multipart Content-Type to the browser', async () => {
    const formData = new FormData()
    formData.append('file', new Blob(['synthetic']), 'notes.txt')
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(csrfResponse('synthetic-csrf-token'))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ materialId: 'material-1' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiClient.requestMultipart<{ materialId: string }>(
      '/materials',
      formData,
    )

    expect(result).toEqual({ materialId: 'material-1' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const csrfInit = fetchMock.mock.calls[0]?.[1]
    const init = fetchMock.mock.calls[1]?.[1]
    expect(csrfInit?.credentials).toBe('include')
    const headers = new Headers(init?.headers)
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe(formData)
    expect(init?.credentials).toBe('include')
    expect(headers.get('Accept')).toBe(ACCEPT_HEADER_VALUE)
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.get('X-CSRF-TOKEN')).toBe('synthetic-csrf-token')
  })

  it('resolves CSRF acquisition against the configured origin with the API path prefix', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(csrfResponse('synthetic-csrf-token'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiClient.requestJson('/subjects', { method: 'POST', body: { name: 'Anatomy' } })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('https://api.example.test/api/auth/csrf')
  })

  it('allows safe custom headers without surrendering transport ownership', async () => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }))

    await apiClient.requestJson('/subjects', {
      headers: {
        'Idempotency-Key': 'synthetic-idempotency-key',
      },
    })

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers)
    expect(headers.get('Idempotency-Key')).toBe('synthetic-idempotency-key')
    expect(headers.get('Accept')).toBe(ACCEPT_HEADER_VALUE)
  })

  it('rejects caller-supplied CSRF headers before any request', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': 'caller-token' },
        body: { name: 'Anatomy' },
      }),
    )

    expect(error.code).toBe('REQUEST_HEADER_NOT_ALLOWED')
    expect(JSON.stringify(error)).not.toContain('caller-token')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

describe('apiClient header ownership', () => {
  it.each([
    ['Accept', 'text/plain'],
    ['Content-Type', 'application/xml'],
    ['Authorization', 'Bearer private-token'],
    ['X-CSRF-TOKEN', 'private-csrf-token'],
  ])('rejects caller-controlled JSON header %s', async (header, value) => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', {
        method: 'POST',
        body: { name: 'Anatomy' },
        headers: { [header]: value },
      }),
    )

    expect(error.kind).toBe('request')
    expect(error.code).toBe('REQUEST_HEADER_NOT_ALLOWED')
    expect(error.message).not.toContain(value)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects caller-controlled multipart Content-Type', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestMultipart('/materials', new FormData(), {
        headers: { 'Content-Type': 'multipart/form-data; boundary=caller-owned' },
      }),
    )

    expect(error.code).toBe('REQUEST_HEADER_NOT_ALLOWED')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each(['GET', 'HEAD', 'OPTIONS', 'TRACE'])('does not acquire CSRF for safe method %s', async (method) => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }))

    await apiClient.requestJson('/subjects', { method })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/subjects')
  })

  it.each(['post', 'PUT', 'patch', 'DELETE', 'PROPFIND'])('acquires CSRF for unsafe method %s', async (method) => {
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(csrfResponse('synthetic-csrf-token'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiClient.requestJson('/subjects', { method })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/csrf')
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-CSRF-TOKEN'))
      .toBe('synthetic-csrf-token')
  })

  it('defaults bodyless JSON requests to safe GET and multipart requests to unsafe POST', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(csrfResponse('synthetic-csrf-token'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiClient.requestJson('/subjects')
    await apiClient.requestMultipart('/materials', new FormData())

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('GET')
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe('GET')
    expect(fetchMock.mock.calls[2]?.[1]?.method).toBe('POST')
  })
})

describe('apiClient failure normalization', () => {
  it('normalizes ProblemDetail and prefers the response correlation header', async () => {
    stubFetch(
      problemResponse({
        status: 409,
        code: 'MISSION_STATE_CONFLICT',
        message: 'The mission state changed.',
        correlationId: BODY_CORRELATION_ID,
        details: { retryable: false },
      }),
    )

    const error = await captureApiError(apiClient.requestJson('/missions/mission-1'))

    expect(error).toMatchObject({
      kind: 'http',
      status: 409,
      code: 'MISSION_STATE_CONFLICT',
      message: 'The mission state changed.',
      correlationId: CORRELATION_ID,
      details: { retryable: false },
    })
  })

  it('uses the ProblemDetail correlation ID when the header is absent', async () => {
    stubFetch(
      problemResponse(
        {
          status: 404,
          code: 'MATERIAL_NOT_FOUND',
          message: 'The material was not found.',
          correlationId: BODY_CORRELATION_ID.toUpperCase(),
          details: {},
        },
        false,
      ),
    )

    const error = await captureApiError(apiClient.requestJson('/materials/missing'))

    expect(error.correlationId).toBe(BODY_CORRELATION_ID)
  })

  it('omits an invalid correlation ID', async () => {
    stubFetch(
      problemResponse(
        {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Request validation failed.',
          correlationId: 'private invalid correlation value',
          details: {},
        },
        false,
      ),
    )

    const error = await captureApiError(apiClient.requestJson('/subjects'))

    expect(error.correlationId).toBeNull()
  })

  it.each([
    ['non-Problem JSON', '{"private":"PRIVATE_JSON_BODY"}', 'application/json'],
    ['plain text', 'PRIVATE_TEXT_BODY', 'text/plain'],
    ['empty body', '', 'application/problem+json'],
    ['malformed problem', '{PRIVATE_MALFORMED_BODY', 'application/problem+json'],
  ])('uses a safe fallback for %s', async (_name, body, contentType) => {
    stubFetch(
      new Response(body, {
        status: 500,
        headers: {
          'Content-Type': contentType,
          'X-Correlation-ID': CORRELATION_ID,
        },
      }),
    )

    const error = await captureApiError(apiClient.requestJson('/failure'))

    expect(error).toMatchObject({
      kind: 'http',
      status: 500,
      code: 'HTTP_ERROR',
      message: 'The request could not be completed.',
      correlationId: CORRELATION_ID,
      details: {},
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
  })

  it('rejects a ProblemDetail whose body status does not match HTTP status', async () => {
    stubFetch(
      problemResponse({
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'Private mismatched message.',
        correlationId: BODY_CORRELATION_ID,
        details: { private: 'PRIVATE_DETAILS' },
      }, true, 409),
    )

    const error = await captureApiError(apiClient.requestJson('/failure'))

    expect(error.code).toBe('HTTP_ERROR')
    expect(error.status).toBe(409)
    expect(JSON.stringify(error)).not.toContain('PRIVATE_')
  })

  it.each([
    [
      'missing code',
      {
        status: 422,
        message: 'PRIVATE_MISSING_CODE_MESSAGE',
        correlationId: BODY_CORRELATION_ID,
        details: { private: 'PRIVATE_MISSING_CODE_DETAILS' },
      },
    ],
    [
      'invalid code',
      {
        status: 422,
        code: 'private-invalid-code',
        message: 'PRIVATE_INVALID_CODE_MESSAGE',
        correlationId: BODY_CORRELATION_ID,
        details: { private: 'PRIVATE_INVALID_CODE_DETAILS' },
      },
    ],
    [
      'missing message',
      {
        status: 422,
        code: 'VALIDATION_FAILED',
        correlationId: BODY_CORRELATION_ID,
        details: { private: 'PRIVATE_MISSING_MESSAGE_DETAILS' },
      },
    ],
    [
      'blank message',
      {
        status: 422,
        code: 'VALIDATION_FAILED',
        message: '   ',
        correlationId: BODY_CORRELATION_ID,
        details: { private: 'PRIVATE_BLANK_MESSAGE_DETAILS' },
      },
    ],
  ])('discards a ProblemDetail with %s', async (_name, body) => {
    stubFetch(problemResponse(body))

    const error = await captureApiError(apiClient.requestJson('/failure'))

    expect(error).toMatchObject({
      kind: 'http',
      status: 422,
      code: 'HTTP_ERROR',
      message: 'The request could not be completed.',
      correlationId: CORRELATION_ID,
      details: {},
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_')
  })

  it.each([
    ['malformed JSON', '{PRIVATE_PARSER_MARKER', 'application/json'],
    ['non-JSON success', 'PRIVATE_RESPONSE_TEXT', 'text/plain'],
  ])('normalizes a %s success without retaining parser or response data', async (
    _name,
    body,
    contentType,
  ) => {
    stubFetch(new Response(body, { status: 200, headers: { 'Content-Type': contentType } }))

    const error = await captureApiError(apiClient.requestJson('/invalid-success'))

    expect(error).toMatchObject({
      kind: 'invalid-response',
      status: 200,
      code: 'INVALID_RESPONSE',
      message: 'The server returned an invalid response.',
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
  })

  it('normalizes network failure without retaining the original error', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(
      new Error('PRIVATE_NETWORK_EXCEPTION'),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(apiClient.requestJson('/subjects'))

    expect(error).toMatchObject({
      kind: 'network',
      status: null,
      code: 'NETWORK_ERROR',
      message: 'Unable to reach the server.',
      correlationId: null,
      details: {},
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_NETWORK_EXCEPTION')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
  })

  it('does not send an unsafe request when CSRF acquisition returns an HTTP failure', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      problemResponse({
        status: 403,
        code: 'CSRF_VALIDATION_FAILED',
        message: 'CSRF validation failed.',
        details: {},
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { method: 'POST', body: { name: 'Anatomy' } }),
    )

    expect(error).toMatchObject({ kind: 'http', status: 403, code: 'CSRF_VALIDATION_FAILED' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['missing token', {}],
    ['blank token', { token: '   ' }],
    ['non-string token', { token: 42 }],
  ])('fails closed for %s CSRF payload', async (_name, payload) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { method: 'POST', body: { name: 'Anatomy' } }),
    )

    expect(error).toMatchObject({
      kind: 'invalid-response',
      status: 200,
      code: 'INVALID_RESPONSE',
      message: 'The server returned an invalid response.',
    })
    expect(JSON.stringify(error)).not.toContain('42')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('distinguishes caller cancellation and passes the signal to fetch', async () => {
    const controller = new AbortController()
    controller.abort()
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new DOMException('PRIVATE_ABORT_EXCEPTION', 'AbortError'))
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { signal: controller.signal }),
    )

    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
    expect(error).toMatchObject({
      kind: 'aborted',
      status: null,
      code: 'REQUEST_ABORTED',
      message: 'The request was canceled.',
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_ABORT_EXCEPTION')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
  })

  it('normalizes abort during CSRF acquisition and never sends the mutation', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(
      new DOMException('PRIVATE_ABORT_EXCEPTION', 'AbortError'),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { method: 'POST', signal: controller.signal }),
    )

    expect(error.code).toBe('REQUEST_ABORTED')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
  })

  it('normalizes abort during the mutation after successful CSRF acquisition', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(csrfResponse('private-csrf-token'))
      .mockRejectedValueOnce(new DOMException('PRIVATE_ABORT_EXCEPTION', 'AbortError'))
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { method: 'POST', signal: controller.signal }),
    )

    expect(error.code).toBe('REQUEST_ABORTED')
    expect(JSON.stringify(error)).not.toContain('private-csrf-token')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]?.[1]?.signal).toBe(controller.signal)
  })

  it('normalizes JSON serialization failure without retaining the source value', async () => {
    const body: { private: string; self?: unknown } = { private: 'PRIVATE_REQUEST_VALUE' }
    body.self = body

    const error = await captureApiError(
      apiClient.requestJson('/subjects', { method: 'POST', body }),
    )

    expect(error).toMatchObject({
      kind: 'request',
      status: null,
      code: 'REQUEST_SERIALIZATION_FAILED',
      message: 'The request body could not be serialized.',
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_REQUEST_VALUE')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
  })

  it('normalizes an invalid request path before fetch', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(
      apiClient.requestJson('https://other.example.test/private'),
    )

    expect(error).toMatchObject({
      kind: 'request',
      status: null,
      code: 'INVALID_REQUEST_PATH',
      message: 'The API request path is invalid.',
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('normalizes invalid API configuration without exposing the configured value', async () => {
    const privateConfiguration = 'https://student:PRIVATE_CONFIGURATION@api.example.test'
    vi.stubEnv('VITE_API_BASE_URL', privateConfiguration)
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const error = await captureApiError(apiClient.requestJson('/subjects'))

    expect(error).toMatchObject({
      kind: 'request',
      status: null,
      code: 'INVALID_API_CONFIGURATION',
      message: 'The API client configuration is invalid.',
      correlationId: null,
      details: {},
    })
    expect(JSON.stringify(error)).not.toContain('PRIVATE_CONFIGURATION')
    expect(Object.prototype.hasOwnProperty.call(error, 'cause')).toBe(false)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

function stubFetch(response: Response): ReturnType<typeof vi.fn<typeof fetch>> {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function csrfResponse(token: string): Response {
  return new Response(JSON.stringify({ token }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemResponse(
  body: Record<string, unknown>,
  includeCorrelationHeader = true,
  responseStatus = Number(body.status),
): Response {
  const headers = new Headers({ 'Content-Type': 'application/problem+json; charset=utf-8' })
  if (includeCorrelationHeader) {
    headers.set('X-Correlation-ID', CORRELATION_ID)
  }

  return new Response(JSON.stringify(body), { status: responseStatus, headers })
}

async function captureApiError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error: unknown) {
    expect(error).toBeInstanceOf(ApiError)
    return error as ApiError
  }

  throw new Error('Expected the API request to reject.')
}
