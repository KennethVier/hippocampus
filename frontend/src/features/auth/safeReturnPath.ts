import type { Location } from 'react-router'

export interface ReturnLocation {
  readonly pathname: string
  readonly search: string
  readonly hash: string
}

export function returnStateFromLocation(location: Location): { returnTo: ReturnLocation } {
  return { returnTo: { pathname: location.pathname, search: location.search, hash: location.hash } }
}

export function safeReturnPath(state: unknown): string {
  if (!isRecord(state) || !isRecord(state.returnTo)) return '/home'
  const { pathname, search, hash } = state.returnTo
  if (typeof pathname !== 'string' || typeof search !== 'string' || typeof hash !== 'string') return '/home'
  if (!isSafePart(pathname) || !isSafePart(search) || !isSafePart(hash)) return '/home'
  if (!pathname.startsWith('/') || pathname.startsWith('//') || pathname.includes('\\')) return '/home'
  if ((search && !search.startsWith('?')) || (hash && !hash.startsWith('#'))) return '/home'

  try {
    const candidate = new URL(`${pathname}${search}${hash}`, window.location.origin)
    if (candidate.origin !== window.location.origin || /^\/login\/?$/i.test(candidate.pathname)) return '/home'
    return `${candidate.pathname}${candidate.search}${candidate.hash}`
  } catch {
    return '/home'
  }
}

function isSafePart(value: string): boolean {
  return [...value].every((character) => {
    const codePoint = character.codePointAt(0) ?? 0
    return codePoint > 31 && codePoint !== 127
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
