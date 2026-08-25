import { describe, expect, it } from 'vitest'

describe('CI gate validation', () => {
  it('intentionally fails to prove merge blocking', () => {
    expect(true).toBe(false)
  })
})