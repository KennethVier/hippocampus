import { describe, expect, it } from 'vitest'

import { normalizeApiBaseUrl, resolveApiUrl } from './apiConfig'

describe('API configuration', () => {
  it.each([undefined, '', '   '])('uses same-origin when the base URL is %s', (value) => {
    expect(normalizeApiBaseUrl(value)).toBe('')
  })

  it.each([
    ['http://127.0.0.1:8080/', 'http://127.0.0.1:8080'],
    ['http://localhost:8080', 'http://localhost:8080'],
    ['http://[::1]:8080/', 'http://[::1]:8080'],
    ['https://api.example.test/', 'https://api.example.test'],
  ])('normalizes the approved origin %s', (value, expected) => {
    expect(normalizeApiBaseUrl(value)).toBe(expected)
  })

  it.each([
    'not-a-url',
    'ftp://api.example.test',
    'http://api.example.test',
    'https://student:secret@api.example.test',
    'https://api.example.test/v1',
    'https://api.example.test?environment=pilot',
    'https://api.example.test#fragment',
  ])('rejects the unsafe API base URL %s without echoing it', (value) => {
    expect(() => normalizeApiBaseUrl(value)).toThrowError(/VITE_API_BASE_URL/)

    try {
      normalizeApiBaseUrl(value)
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(Error)
      expect((error as Error).message).not.toContain(value)
    }
  })

  it('resolves same-origin and configured-origin API paths', () => {
    expect(resolveApiUrl('/subjects?page=2', '')).toBe('/subjects?page=2')
    expect(resolveApiUrl('/subjects?page=2', 'https://api.example.test')).toBe(
      'https://api.example.test/subjects?page=2',
    )
  })

  it.each([
    'subjects',
    'https://other.example.test/subjects',
    '//other.example.test/subjects',
    '/\\other.example.test/subjects',
    '/subjects#private-fragment',
  ])('rejects the unsafe request path %s', (path) => {
    expect(() => resolveApiUrl(path, 'https://api.example.test')).toThrowError(
      /root-relative|fragments/,
    )
  })
})
