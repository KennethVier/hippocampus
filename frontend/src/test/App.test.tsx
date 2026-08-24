import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { App } from '../app/App'

describe('App', () => {
  it('renders the Hippocampus base surface', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: 'Hippocampus' }),
    ).toBeInTheDocument()
  })
})

