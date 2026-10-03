import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import * as api from '../api/studyMissionApi'
import type { ActivitySubmission, StudyMission, StudyMissionLifecycle } from '../api/studyMissionContracts'
import { studyMissionKeys } from '../queries/studyMissionQueries'
import { StudyMissionPage } from './StudyMissionPage'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const mission: StudyMission = {
  id: missionId,
  status: 'ACTIVE',
  stage: 'EXPLAIN',
  currentActivity: {
    id: '9a7b3302-b431-45e1-90e3-298c9d80918f',
    type: 'EXPLANATION',
    status: 'PRESENTED',
    difficulty: null,
    classification: null,
    content: { concept: 'Cardiac output', explanation: 'The volume pumped each minute.', keyPoints: ['Rate multiplied by stroke volume'], limitations: [] },
    sources: [],
  },
  availableTimeMinutes: 15,
  startedAt: '2026-10-02T01:00:00Z',
  completedAt: null,
  stoppedAt: null,
  updatedAt: '2026-10-02T01:01:00Z',
}

afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderMission(path = `/missions/${missionId}`) {
  const client = createAppQueryClient()
  const router = createMemoryRouter([{ path: '/missions/:missionId', element: <StudyMissionPage /> }], { initialEntries: [path] })
  render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
  return client
}

const retrievalMission = {
  ...mission,
  stage: 'RETRIEVE',
  currentActivity: {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', type: 'RETRIEVAL', status: 'PRESENTED', difficulty: 'STANDARD', classification: null,
    content: { subtype: 'MCQ', concept: 'Hemodynamics', question: 'Which factor increases cardiac output?', difficulty: 'STANDARD', limitations: [], options: [
      { id: 'option-a', text: 'Reduced heart rate' }, { id: 'option-b', text: 'Increased stroke volume' },
    ] }, sources: [],
  },
} satisfies StudyMission

const connectionMission = {
  ...mission,
  stage: 'CONNECTION',
  currentActivity: {
    id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', type: 'CONNECTION', status: 'PRESENTED', difficulty: 'STANDARD', classification: null,
    content: {
      fromConcept: 'Preload', toConcept: 'Stroke volume', relationshipType: 'DIRECTLY_INFLUENCES',
      relationship: 'Greater filling can increase ejection.', whyItMatters: 'This connects venous return to cardiac output.',
      question: 'Explain how preload influences stroke volume.', limitations: [],
    },
    sources: [],
  },
} satisfies StudyMission

const historicalConnectionMission = {
  ...connectionMission,
  currentActivity: {
    ...connectionMission.currentActivity,
    content: { ...connectionMission.currentActivity.content, question: null },
  },
} satisfies StudyMission

const submission: ActivitySubmission = {
  missionId,
  activityId: retrievalMission.currentActivity!.id,
  outcome: 'CORRECT', correctConcepts: ['Stroke volume'], missingConcepts: [], misconceptions: [],
  feedback: 'Stroke volume contributes directly to cardiac output.', missionStatus: 'ACTIVE', stage: 'RETRIEVE',
  updatedAt: '2026-10-02T01:02:00Z', continuationAvailable: true,
}

const lifecycle = (status: string) => ({
  id: missionId, status, currentActivityId: mission.currentActivity!.id, sourceScopes: [],
  startedAt: mission.startedAt, completedAt: null, stoppedAt: null, updatedAt: '2026-10-02T01:02:00Z',
})

describe('StudyMissionPage', () => {
  it('shows the loading state', () => {
    vi.spyOn(api, 'getStudyMission').mockReturnValue(new Promise<StudyMission>(() => undefined))
    renderMission()
    expect(screen.getByRole('status', { name: 'Loading Study Mission' })).toBeInTheDocument()
  })

  it('renders mission context and the current activity', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(mission)
    renderMission()
    expect(await screen.findByRole('heading', { level: 1, name: 'Study Mission' })).toBeInTheDocument()
    expect(screen.getByText('Explain')).toBeInTheDocument()
    expect(screen.getByText('The volume pumped each minute.')).toBeInTheDocument()
  })

  it('renders a legitimate no-current-activity state', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({ ...mission, stage: null, currentActivity: null })
    renderMission()
    expect(await screen.findByRole('heading', { name: 'No activity is available right now' })).toBeInTheDocument()
    expect(screen.queryByText('Stage')).not.toBeInTheDocument()
    expect(screen.queryByText('The volume pumped each minute.')).not.toBeInTheDocument()
  })

  it.each([
    new ApiError({ kind: 'http', status: 404, code: 'STUDY_MISSION_NOT_FOUND', message: 'private' }),
    new ApiError({ kind: 'http', status: 404, code: 'HTTP_ERROR', message: 'private' }),
  ])('uses the safe unavailable state for concealed 404 responses', async (error) => {
    vi.spyOn(api, 'getStudyMission').mockRejectedValue(error)
    renderMission()
    expect(await screen.findByRole('heading', { name: 'Study mission unavailable' })).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('private')
    expect(document.body).not.toHaveTextContent(missionId)
  })

  it.each([
    new ApiError({ kind: 'invalid-response', status: 200, code: 'INVALID_RESPONSE', message: 'internal schema detail' }),
    new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'network internals' }),
  ])('uses the generic error state for malformed or network responses', async (error) => {
    vi.spyOn(api, 'getStudyMission').mockRejectedValue(error)
    renderMission()
    expect(await screen.findByRole('heading', { name: 'Study mission could not be loaded' })).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent(error.message)
  })

  it('does not request an invalid route identifier', () => {
    const request = vi.spyOn(api, 'getStudyMission')
    renderMission('/missions/not-a-uuid')
    expect(screen.getByRole('heading', { name: 'Study mission unavailable' })).toBeInTheDocument()
    expect(request).not.toHaveBeenCalled()
  })

  it('submits the selected MCQ option and displays confirmed backend feedback', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(retrievalMission)
    const submit = vi.spyOn(api, 'submitActivityResponse').mockResolvedValue(submission)
    renderMission()

    fireEvent.click(await screen.findByRole('radio', { name: 'Increased stroke volume' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))

    await waitFor(() => expect(submit).toHaveBeenCalledWith(missionId, retrievalMission.currentActivity!.id, { selectedOption: 'option-b' }))
    expect(await screen.findByText(submission.feedback)).toBeInTheDocument()
    expect(screen.getByText('Correct')).toBeInTheDocument()
  })

  it('submits a short-answer retrieval response as responseText', async () => {
    const submit = vi.spyOn(api, 'submitActivityResponse').mockImplementation(async (_, activityId) => ({ ...submission, activityId }))
    const shortAnswer = {
      ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, content: { ...retrievalMission.currentActivity!.content!, subtype: 'SHORT_ANSWER', options: [] } },
    } as StudyMission
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(shortAnswer).mockResolvedValue(shortAnswer)
    renderMission()
    fireEvent.change(await screen.findByRole('textbox', { name: 'Response' }), { target: { value: 'Frank-Starling mechanism' } })
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await waitFor(() => expect(submit).toHaveBeenCalledWith(missionId, shortAnswer.currentActivity!.id, { responseText: 'Frank-Starling mechanism' }))
  })

  it('submits an application response as responseText', async () => {
    const submit = vi.spyOn(api, 'submitActivityResponse').mockImplementation(async (_, activityId) => ({ ...submission, activityId }))
    const application = { ...mission, currentActivity: {
      id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', type: 'APPLICATION' as const, status: 'PRESENTED', difficulty: 'STANDARD', classification: null,
      content: { scenario: 'A patient is hypotensive.', question: 'Explain the response.', targetConcept: 'Cardiac output', difficulty: 'STANDARD', limitations: [] }, sources: [],
    } } satisfies StudyMission
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(application)
    renderMission()
    fireEvent.change(await screen.findByRole('textbox', { name: 'Response' }), { target: { value: 'Increase stroke volume' } })
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await waitFor(() => expect(submit).toHaveBeenCalledWith(missionId, application.currentActivity.id, { responseText: 'Increase stroke volume' }))
  })

  it('requires and submits an unfinished connection response through the normal endpoint', async () => {
    const submit = vi.spyOn(api, 'submitActivityResponse').mockImplementation(async (_, activityId) => ({ ...submission, activityId }))
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(connectionMission)
    renderMission()

    expect(await screen.findByText('Explain how preload influences stroke volume.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Response' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox', { name: 'Response' }), { target: { value: 'More preload stretches the ventricle and increases ejection.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))

    await waitFor(() => expect(submit).toHaveBeenCalledWith(
      missionId,
      connectionMission.currentActivity.id,
      { responseText: 'More preload stretches the ventricle and increases ejection.' },
    ))
    expect(await screen.findByText(submission.feedback)).toBeInTheDocument()
  })

  it('presents a historical connection without fabricating response evaluation', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(historicalConnectionMission)
    renderMission()

    expect(await screen.findByText('Greater filling can increase ejection.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Response' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled()
  })

  it('preserves the response draft when the same activity is refetched', async () => {
    const shortAnswer = {
      ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, content: { ...retrievalMission.currentActivity!.content!, subtype: 'SHORT_ANSWER', options: [] } },
    } as StudyMission
    const get = vi.spyOn(api, 'getStudyMission')
      .mockResolvedValueOnce(shortAnswer)
      .mockResolvedValue({ ...shortAnswer, updatedAt: '2026-10-02T01:03:00Z' })
    const client = renderMission()

    const response = await screen.findByRole('textbox', { name: 'Response' })
    fireEvent.change(response, { target: { value: 'Frank-Starling draft' } })
    await client.refetchQueries({ queryKey: studyMissionKeys.detail(missionId), exact: true })

    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('textbox', { name: 'Response' })).toHaveValue('Frank-Starling draft')
  })

  it('starts with an empty response draft when the authoritative activity ID changes', async () => {
    const shortAnswer = {
      ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, content: { ...retrievalMission.currentActivity!.content!, subtype: 'SHORT_ANSWER', options: [] } },
    } as StudyMission
    const nextActivity = {
      ...shortAnswer,
      currentActivity: { ...shortAnswer.currentActivity!, id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc' },
      updatedAt: '2026-10-02T01:03:00Z',
    } satisfies StudyMission
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(shortAnswer).mockResolvedValue(nextActivity)
    const client = renderMission()

    fireEvent.change(await screen.findByRole('textbox', { name: 'Response' }), { target: { value: 'Old activity draft' } })
    await client.refetchQueries({ queryKey: studyMissionKeys.detail(missionId), exact: true })

    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Response' })).toHaveValue(''))
  })

  it('disables submission while pending and prevents an intentional double submit', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(retrievalMission)
    const submit = vi.spyOn(api, 'submitActivityResponse').mockReturnValue(new Promise<ActivitySubmission>(() => undefined))
    renderMission()
    fireEvent.click(await screen.findByRole('radio', { name: 'Increased stroke volume' }))
    const button = screen.getByRole('button', { name: 'Submit response' })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled())
    expect(submit).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['completed retrieval', { ...retrievalMission, currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' } }],
    ['presentation explanation', mission],
  ])('offers Continue for %s without requiring submission', async (_, value) => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(value as StudyMission)
    renderMission()
    expect(await screen.findByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
  })

  it('continues authoritatively, refetches, and does not resend after a failed refresh', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission).mockRejectedValueOnce(new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'private' }))
    const continueRequest = vi.spyOn(api, 'continueStudyMission').mockResolvedValue()
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    expect(await screen.findByRole('button', { name: 'Reload mission state' })).toBeInTheDocument()
    expect(continueRequest).toHaveBeenCalledWith(missionId, mission.currentActivity!.id)
    expect(continueRequest).toHaveBeenCalledTimes(1)
  })

  it('refetches on a stale conflict, shows a safe notice, and never retries the write', async () => {
    const get = vi.spyOn(api, 'getStudyMission')
      .mockResolvedValueOnce(retrievalMission)
      .mockResolvedValue(connectionMission)
    const submit = vi.spyOn(api, 'submitActivityResponse').mockRejectedValue(new ApiError({ kind: 'http', status: 409, code: 'MISSION_CONFLICT', message: 'private' }))
    renderMission()
    fireEvent.click(await screen.findByRole('radio', { name: 'Increased stroke volume' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    expect(await screen.findByText('This mission changed in another tab. The latest state has been loaded.')).toBeInTheDocument()
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Explain how preload influences stroke volume.')).toBeInTheDocument()
    expect(submit).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent('private')
  })

  it('blocks stale writes when conflict recovery fails and unlocks after manual reload', async () => {
    const get = vi.spyOn(api, 'getStudyMission')
      .mockResolvedValueOnce(retrievalMission)
      .mockRejectedValueOnce(new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'private get failure' }))
      .mockResolvedValue(connectionMission)
    const submit = vi.spyOn(api, 'submitActivityResponse').mockRejectedValue(
      new ApiError({ kind: 'http', status: 409, code: 'MISSION_CONFLICT', message: 'private write failure' }),
    )
    renderMission()
    fireEvent.click(await screen.findByRole('radio', { name: 'Increased stroke volume' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))

    expect(await screen.findByText('This mission changed in another tab, but the latest state could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText('This mission changed in another tab. The latest state has been loaded.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit response' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeDisabled()
    expect(submit).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent('private')

    fireEvent.click(screen.getByRole('button', { name: 'Reload mission state' }))

    expect(await screen.findByText('Explain how preload influences stroke volume.')).toBeInTheDocument()
    expect(screen.getByText('This mission changed in another tab. The latest state has been loaded.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Response' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeEnabled()
    expect(submit).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledTimes(3)
  })

  it('exposes only Pause for ACTIVE and waits for confirmation', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(mission)
    vi.spyOn(api, 'pauseStudyMission').mockReturnValue(new Promise<StudyMissionLifecycle>(() => undefined))
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Pause' }))
    expect(await screen.findByRole('button', { name: 'Pausing…' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Resume' })).not.toBeInTheDocument()
  })

  it('reconstructs PAUSED state after refresh and resumes only after backend confirmation', async () => {
    const paused = { ...mission, status: 'PAUSED' }
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(paused).mockResolvedValue({ ...paused, status: 'ACTIVE' })
    vi.spyOn(api, 'resumeStudyMission').mockResolvedValue(lifecycle('ACTIVE'))
    renderMission()
    expect((await screen.findAllByText('Paused')).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }))
    await waitFor(() => expect(screen.getByText('Active')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Resume' })).not.toBeInTheDocument()
  })

  it('shows a display-only timer and accessible source controls without mutating mission state', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({ ...mission, currentActivity: { ...mission.currentActivity!, sources: [{ sourceReferenceId: '11111111-1111-4111-8111-111111111111', materialTitle: 'Physiology', pageNumber: 2, displayLabel: 'Flow' }] } })
    const pause = vi.spyOn(api, 'pauseStudyMission')
    const submit = vi.spyOn(api, 'submitActivityResponse')
    renderMission()
    expect(await screen.findByLabelText('Mission time')).toHaveTextContent('15 min')
    expect(screen.getByRole('button', { name: 'Hide sources' })).toHaveAttribute('aria-expanded', 'true')
    expect(pause).not.toHaveBeenCalled()
    expect(submit).not.toHaveBeenCalled()
  })
})
