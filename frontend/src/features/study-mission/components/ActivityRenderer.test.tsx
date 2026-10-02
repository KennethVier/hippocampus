import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { StudyMissionActivity } from '../api/studyMissionContracts'
import { ActivityRenderer } from './ActivityRenderer'
import { SourcePanel } from './SourcePanel'

const base = {
  id: '9a7b3302-b431-45e1-90e3-298c9d80918f',
  status: 'PRESENTED',
  difficulty: null,
  classification: null,
  sources: [],
}

afterEach(cleanup)

describe('ActivityRenderer', () => {
  it('renders explanation content and supplemental classification', () => {
    renderActivity({ ...base, type: 'EXPLANATION', classification: 'SUPPLEMENTAL_GENERATED', content: { concept: 'Afterload', explanation: 'Resistance to ventricular ejection.', keyPoints: ['Arterial pressure contributes'], limitations: ['Clinical context varies'] } })
    expect(screen.getByRole('heading', { name: 'Explanation' })).toBeInTheDocument()
    expect(screen.getByText('Resistance to ventricular ejection.')).toBeInTheDocument()
    expect(screen.getByText('Additional medical context')).toBeInTheDocument()
  })

  it('renders retrieval options as read-only content without answer or submission data', () => {
    renderActivity({ ...base, type: 'RETRIEVAL', content: { subtype: 'SINGLE_CHOICE', concept: 'Brachial plexus', question: 'Which roots form the upper trunk?', options: [{ id: 'a', text: 'C5-C6' }, { id: 'b', text: 'C8-T1' }], difficulty: 'FOUNDATIONAL', limitations: [] } })
    expect(screen.getByRole('heading', { name: 'Retrieval practice' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Answer options' })).toHaveTextContent('C5-C6')
    expect(screen.queryByRole('button', { name: /submit|answer/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/correct answer/i)).not.toBeInTheDocument()
  })

  it('renders a learning connection', () => {
    renderActivity({ ...base, type: 'CONNECTION', content: { fromConcept: 'Preload', toConcept: 'Stroke volume', relationshipType: 'DIRECTLY_INFLUENCES', relationship: 'Greater filling can increase ejection.', whyItMatters: 'It connects venous return to cardiac output.', limitations: [] } })
    expect(screen.getByRole('heading', { name: 'Learning connection' })).toBeInTheDocument()
    expect(screen.getByText('It connects venous return to cardiac output.')).toBeInTheDocument()
  })

  it('renders an educational application without hidden answers or reasoning', () => {
    renderActivity({ ...base, type: 'APPLICATION', content: { scenario: 'A learner compares two pressure-volume loops.', question: 'Which change best explains the wider loop?', targetConcept: 'Stroke volume', difficulty: 'INTERMEDIATE', limitations: [] } })
    expect(screen.getByRole('heading', { name: 'Application' })).toBeInTheDocument()
    expect(screen.getByText('A learner compares two pressure-volume loops.')).toBeInTheDocument()
    expect(screen.queryByText(/expected answer|required reasoning|feedback points/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it.each([
    { activity: { ...base, type: 'EXPLANATION', content: null }, heading: 'Explanation' },
    { activity: { ...base, type: 'RETRIEVAL', content: null }, heading: 'Retrieval practice' },
    { activity: { ...base, type: 'CONNECTION', content: null }, heading: 'Learning connection' },
    { activity: { ...base, type: 'APPLICATION', content: null }, heading: 'Application' },
  ] satisfies Array<{ activity: StudyMissionActivity; heading: string }>)('renders only a bounded neutral state for null-content $activity.type', ({ activity, heading }) => {
    renderActivity(activity)
    const shell = screen.getByRole('article')
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    expect(shell).toHaveTextContent(`Current learning activity${heading}Content is not available for this activity yet.`)
  })

  it.each([
    { activity: { ...base, type: 'FEEDBACK', content: null }, heading: 'Feedback', message: 'Feedback is not available for this activity yet.' },
    { activity: { ...base, type: 'REFLECTION', content: null }, heading: 'Reflection', message: 'Reflection content is not available for this activity yet.' },
    { activity: { ...base, type: 'VISUAL', content: null }, heading: 'Visual activity', message: 'Visual learning content is not available for this activity yet.' },
  ] satisfies Array<{ activity: StudyMissionActivity; heading: string; message: string }>)('renders a bounded null-content $activity.type state', ({ activity, heading, message }) => {
    renderActivity(activity)
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.getByText(message)).toBeInTheDocument()
  })
})

describe('SourcePanel', () => {
  it('renders learner-safe source metadata and supports an accessible local toggle', () => {
    render(<SourcePanel sources={[{ sourceReferenceId: '11111111-1111-4111-8111-111111111111', materialTitle: 'Upper Limb Lecture', pageNumber: 14, displayLabel: 'Posterior Cord' }]} />)
    expect(screen.getByText('Upper Limb Lecture')).toBeInTheDocument()
    expect(screen.getByText('Page 14')).toBeInTheDocument()
    expect(screen.getByText('Posterior Cord')).toBeInTheDocument()
    const toggle = screen.getByRole('button', { name: 'Hide sources' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: 'Show sources' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Upper Limb Lecture')).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('11111111-1111-4111-8111-111111111111')
    expect(screen.queryByRole('link', { name: /open|view original/i })).not.toBeInTheDocument()
  })
})

function renderActivity(activity: StudyMissionActivity) {
  render(<ActivityRenderer activity={activity} />)
}
