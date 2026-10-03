import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { StrictMode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { SourcePanel } from './SourcePanel'

afterEach(cleanup)

describe('SourcePanel', () => {
  it('opens and closes the accessible mobile sources dialog', () => {
    render(
      <StrictMode>
        <SourcePanel
          sources={[{
            sourceReferenceId: '11111111-1111-4111-8111-111111111111',
            materialTitle: 'Cardiovascular Physiology',
            pageNumber: 12,
            displayLabel: 'Cardiac output',
          }]}
        />
      </StrictMode>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'View sources' }))

    const sources = screen.getByRole('dialog', { name: 'Sources' })
    expect(within(sources).getByText('Cardiovascular Physiology')).toBeVisible()

    fireEvent.click(within(sources).getByRole('button', { name: 'Close sources' }))

    expect(screen.queryByRole('dialog', { name: 'Sources' })).not.toBeInTheDocument()
  })
})
