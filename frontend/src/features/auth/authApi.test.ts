import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../api/apiClient'
import { getCurrentSession } from './authApi'

afterEach(() => vi.unstubAllGlobals())

describe('getCurrentSession', () => {
  it('accepts the privacy-minimal session response', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({
      userId: '3F2504E0-4F89-41D3-9A0C-0305E82C3301',
    })))
    await expect(getCurrentSession()).resolves.toEqual({
      userId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301',
    })
  })

  it.each([undefined, {}, { userId: 42 }, { userId: 'not-a-uuid' }])(
    'fails closed for malformed session data',
    async (body) => {
      vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(body)))
      const error = await getCurrentSession().catch((failure: unknown) => failure)
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({ kind: 'invalid-response', code: 'INVALID_RESPONSE' })
    },
  )
})

function jsonResponse(body: unknown) {
  return new Response(body === undefined ? '' : JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
