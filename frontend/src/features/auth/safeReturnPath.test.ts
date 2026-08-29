import { describe, expect, it } from 'vitest'

import { safeReturnPath } from './safeReturnPath'

describe('safeReturnPath', () => {
  it('preserves an internal application destination', () => {
    expect(safeReturnPath({ returnTo: { pathname: '/subjects/one', search: '?view=recent', hash: '#notes' } }))
      .toBe('/subjects/one?view=recent#notes')
  })

  it.each([
    undefined,
    { returnTo: 'https://attacker.example' },
    { returnTo: { pathname: '//attacker.example', search: '', hash: '' } },
    { returnTo: { pathname: '/\\attacker.example', search: '', hash: '' } },
    { returnTo: { pathname: '/login', search: '', hash: '' } },
    { returnTo: { pathname: '/home\u0000', search: '', hash: '' } },
    { returnTo: { pathname: '/home', search: 'bad', hash: '' } },
  ])('falls back safely for untrusted return state', (state) => {
    expect(safeReturnPath(state)).toBe('/home')
  })
})
