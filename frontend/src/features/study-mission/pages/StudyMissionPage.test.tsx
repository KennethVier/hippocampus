import { QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  continuation: null,
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

  it('recovers an ungraded submission on refresh without showing correctness lists', async () => {
    const ungraded = {
      ...submission,
      outcome: 'UNGRADED',
      attemptId: '11111111-1111-4111-8111-111111111111',
      limitationCode: 'EVALUATION_UNRESOLVED',
      correctConcepts: [], missingConcepts: [], misconceptions: [],
      feedback: 'Your response was saved, but it could not be graded reliably.',
    }
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({
      ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' }, continuation: { actionType: 'RETRIEVE', rationaleCode: 'READY_FOR_RETRIEVAL', canMaterialize: true },
      latestSubmission: ungraded,
    })
    renderMission()
    expect(await screen.findByText('Response saved · ungraded')).toBeInTheDocument()
    expect(screen.getByText(ungraded.feedback)).toBeInTheDocument()
    expect(screen.queryByText('Correct concepts')).not.toBeInTheDocument()
    expect(screen.queryByText('Concepts to revisit')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled()
  })

  it.each([false, true])('keeps saved work and Pause after a blocked read, conflict recovery=%s', async (conflictRecovery) => {
    const ungraded: ActivitySubmission = { ...submission, outcome: 'UNGRADED',
      correctConcepts: [], missingConcepts: [], misconceptions: [],
      feedback: 'Your response is saved without a grade.',
      attemptId: '11111111-1111-4111-8111-111111111111', limitationCode: 'EVALUATION_UNRESOLVED' }
    const blocked: StudyMission = { ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' },
      continuation: null, latestSubmission: ungraded }
    const reads = vi.spyOn(api, 'getStudyMission')
    if (conflictRecovery) reads.mockResolvedValueOnce({ ...blocked,
      continuation: { actionType: 'RETRIEVE', rationaleCode: 'READY_FOR_RETRIEVAL', canMaterialize: true } })
    reads.mockResolvedValue(blocked)
    const next = vi.spyOn(api, 'continueStudyMission').mockRejectedValue(new ApiError({
      kind: 'http', status: 409, code: 'FOLLOW_UP_NO_PROGRESS', message: 'No eligible transition.' }))
    const pause = vi.spyOn(api, 'pauseStudyMission').mockImplementation(async () => {
      reads.mockResolvedValue({ ...blocked, status: 'PAUSED' })
      return lifecycle('PAUSED')
    })
    const resume = vi.spyOn(api, 'resumeStudyMission').mockImplementation(async () => {
      reads.mockResolvedValue(blocked)
      return lifecycle('ACTIVE')
    })
    renderMission()
    expect(await screen.findByText(ungraded.feedback)).toBeInTheDocument()
    if (conflictRecovery) {
      fireEvent.click(screen.getByRole('button', { name: /^Continue$/ }))
      await waitFor(() => expect(next).toHaveBeenCalledTimes(1))
      await waitFor(() => expect(screen.queryByRole('button', { name: /^Continue$/ })).not.toBeInTheDocument())
    }
    expect(screen.queryByRole('button', { name: /^Continue$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: /^Pause$/ })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: /^Pause$/ }))
    const resumeButton = await screen.findByRole('button', { name: /^Resume$/ })
    expect(pause).toHaveBeenCalledTimes(1)
    fireEvent.click(resumeButton)
    await waitFor(() => expect(resume).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(screen.getByRole('button', { name: /^Pause$/ })).toBeEnabled())
    expect(screen.getByText(ungraded.feedback)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Continue$/ })).not.toBeInTheDocument()
    expect(next).toHaveBeenCalledTimes(conflictRecovery ? 1 : 0)
    expect(screen.queryByText('Correct concepts')).not.toBeInTheDocument()
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
    ['completed retrieval', { ...retrievalMission, currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' }, continuation: { actionType: 'RETRIEVE', rationaleCode: 'READY_FOR_RETRIEVAL', canMaterialize: true } }],
    ['presentation explanation', mission],
  ])('offers Continue for %s without requiring submission', async (_, value) => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(value as StudyMission)
    renderMission()
    expect(await screen.findByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
  })

  it('continues authoritatively, refetches, and does not resend after a failed refresh', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission).mockRejectedValueOnce(new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'private' }))
    const continueRequest = vi.spyOn(api, 'continueStudyMission').mockResolvedValue({ outcome: 'ACTIVITY_CREATED', activityId: mission.currentActivity!.id })
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


describe('P7-15 follow-up compatibility', () => {
  it('accepts an answer only through the newly current response-bearing activity', async () => {
    const previous: StudyMission = { ...retrievalMission, currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' }, continuation: { actionType: 'RETRIEVE', rationaleCode: 'GRADED_RESPONSE_FOLLOW_UP', canMaterialize: true } }
    const followUpId = 'a1715015-1111-4111-8111-111111111111'
    const followUp = { ...retrievalMission, currentActivity: { ...retrievalMission.currentActivity!, id: followUpId } } as StudyMission
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(previous).mockResolvedValue(followUp)
    const next = vi.spyOn(api, 'continueStudyMission').mockResolvedValue({ outcome: 'ACTIVITY_CREATED', activityId: followUpId })
    const submit = vi.spyOn(api, 'submitActivityResponse').mockResolvedValue({ ...submission, activityId: followUpId })
    renderMission()
    expect(await screen.findByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    fireEvent.click(await screen.findByRole('radio', { name: 'Increased stroke volume' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit response' }))
    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1))
    expect(next).toHaveBeenCalledWith(missionId, previous.currentActivity!.id)
    expect(submit.mock.calls[0]?.[1]).toBe(followUpId)
  })

  it('allows the existing reflection alternative to continue without fabricating a response', async () => {
    const reflection = { ...mission, currentActivity: { ...mission.currentActivity!, type: 'REFLECTION', content: null } } as StudyMission
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(reflection)
    const submit = vi.spyOn(api, 'submitActivityResponse')
    renderMission()
    expect(await screen.findByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(screen.queryByRole('textbox', { name: 'Response' })).not.toBeInTheDocument()
    expect(submit).not.toHaveBeenCalled()
  })
})


describe('P7-16 assessment limitation', () => {
  it.each(['conflict', 'ambiguous'] as const)('updates failed-reload messaging when a new recovery follows %s', async (firstReason) => {
    const conflict = new ApiError({ kind: 'http', status: 409, code: 'MISSION_CONFLICT', message: 'private conflict' })
    const ambiguous = new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'private network' })
    const conflictNotice = 'This mission changed in another tab. The latest state has been loaded.'
    const recoveryNotice = 'The latest mission state has been loaded. Check it before continuing.'
    const conflictError = 'This mission changed in another tab, but the latest state could not be loaded.'
    const recoveryError = 'The action result could not be confirmed, and the latest mission state could not be loaded. Reload the mission state before trying again.'
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission).mockResolvedValueOnce(mission)
      .mockRejectedValueOnce(ambiguous).mockResolvedValue(mission)
    const request = vi.spyOn(api, 'continueStudyMission')
      .mockRejectedValueOnce(firstReason === 'conflict' ? conflict : ambiguous)
      .mockRejectedValueOnce(firstReason === 'conflict' ? ambiguous : conflict)
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    expect(await screen.findByText(firstReason === 'conflict' ? conflictNotice : recoveryNotice)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    expect(await screen.findByText(firstReason === 'conflict' ? recoveryError : conflictError)).toBeInTheDocument()
    expect(screen.queryByText(firstReason === 'conflict' ? conflictError : recoveryError)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeDisabled()
    expect(request).toHaveBeenCalledTimes(2)
    fireEvent.click(screen.getByRole('button', { name: 'Reload mission state' }))
    expect(await screen.findByText(firstReason === 'conflict' ? recoveryNotice : conflictNotice)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reload mission state' })).not.toBeInTheDocument()
    expect(request).toHaveBeenCalledTimes(2)
  })

  it.each([null, 502, 504])('ignores stale recovery after mission navigation for HTTP %s', async (status) => {
    const nextId = '55555555-5555-4555-8555-555555555555'
    let finishReload: (value: StudyMission) => void = () => { throw new Error('Reload has not started') }
    const pendingReload = new Promise<StudyMission>((resolve) => { finishReload = resolve })
    const networkError = new ApiError({ kind: status === null ? 'network' : 'http', status, code: 'UNKNOWN_RESULT', message: 'private' })
    const get = vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission)
      .mockRejectedValueOnce(networkError).mockReturnValueOnce(pendingReload)
      .mockResolvedValue({ ...mission, id: nextId })
    const request = vi.spyOn(api, 'continueStudyMission').mockRejectedValue(networkError)
    const client = createAppQueryClient()
    const router = createMemoryRouter([{ path: '/missions/:missionId', element: <StudyMissionPage /> }],
      { initialEntries: [`/missions/${missionId}`] })
    render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    expect(await screen.findByRole('button', { name: 'Reload mission state' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reload mission state' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(3))
    await act(async () => { await router.navigate(`/missions/${nextId}`) })
    expect(await screen.findByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Reload mission state' })).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('could not be confirmed')
    await act(async () => { finishReload(mission); await pendingReload })
    expect(document.body).not.toHaveTextContent('The latest mission state has been loaded.')
    expect(document.body).not.toHaveTextContent('changed in another tab')
    expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled()
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('finishes only through explicit Continue and refreshes the unchanged saved receipt', async () => {
    const saved: ActivitySubmission = { ...submission, outcome: 'UNGRADED', correctConcepts: [],
      feedback: 'Saved without a grade.', limitationCode: 'EVALUATION_UNRESOLVED' }
    const ready: StudyMission = { ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity, status: 'COMPLETED' }, latestSubmission: saved,
      continuation: { actionType: 'COMPLETE', rationaleCode: 'TIME_EXHAUSTED', canMaterialize: false } }
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(ready).mockResolvedValue({
      ...ready, status: 'COMPLETED', completedAt: '2026-10-16T10:00:00Z', continuation: null,
    })
    const continueRequest = vi.spyOn(api, 'continueStudyMission').mockResolvedValue({
      outcome: 'NO_ACTIVITY', actionType: 'COMPLETE', rationaleCode: 'TIME_EXHAUSTED',
    })
    renderMission()
    const finish = await screen.findByRole('button', { name: 'Finish session' })
    expect(continueRequest).not.toHaveBeenCalled()
    fireEvent.click(finish)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Finish session' })).not.toBeInTheDocument())
    expect(continueRequest).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Saved without a grade.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit response' })).not.toBeInTheDocument()
  })

  it.each(['network', 'invalid-response', 500, 502, 503, 504, 599] as const)('refetches an ambiguous %s Continue result without retrying the mutation', async (kind) => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission).mockResolvedValue({
      ...retrievalMission, currentActivity: { ...retrievalMission.currentActivity, status: 'COMPLETED' },
      continuation: { actionType: 'COMMUNICATE_LIMITATION', rationaleCode: 'ASSESSMENT_UNAVAILABLE', canMaterialize: false },
    })
    const continueRequest = vi.spyOn(api, 'continueStudyMission').mockRejectedValue(new ApiError({
      kind: typeof kind === 'number' ? 'http' : kind, status: typeof kind === 'number' ? kind : null, code: 'UNKNOWN_RESULT', message: 'Private network details',
    }))
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    expect(await screen.findByText(/Automatic assessment is unavailable/)).toBeInTheDocument()
    expect(continueRequest).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('Private network details')
    expect(screen.getByText('The latest mission state has been loaded. Check it before continuing.')).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('changed in another tab')
  })

  it.each(['network', 'invalid-response', 500, 502, 503, 504, 599] as const)('blocks writes after ambiguous %s recovery fails until manual reload succeeds', async (kind) => {
    const get = vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission)
      .mockRejectedValueOnce(new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'private read' }))
      .mockResolvedValue(connectionMission)
    const request = vi.spyOn(api, 'continueStudyMission').mockRejectedValue(new ApiError({
      kind: typeof kind === 'number' ? 'http' : kind, status: typeof kind === 'number' ? kind : null, code: 'UNKNOWN_RESULT', message: 'private write',
    }))
    const submit = vi.spyOn(api, 'submitActivityResponse')
    const pause = vi.spyOn(api, 'pauseStudyMission')
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    expect(await screen.findByText('The action result could not be confirmed, and the latest mission state could not be loaded. Reload the mission state before trying again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    expect(request).toHaveBeenCalledTimes(1)
    expect(pause).not.toHaveBeenCalled()
    expect(submit).not.toHaveBeenCalled()
    expect(document.body).not.toHaveTextContent('changed in another tab')
    fireEvent.click(screen.getByRole('button', { name: 'Reload mission state' }))
    expect(await screen.findByRole('textbox', { name: 'Response' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeEnabled()
    expect(screen.getByText('The latest mission state has been loaded. Check it before continuing.')).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('changed in another tab')
    expect(request).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledTimes(3)
    expect(document.body).not.toHaveTextContent('private')
  })

  it('requires explicit recovery before a new user Continue after an indeterminate server error', async () => {
    const next = { ...mission, currentActivity: { ...mission.currentActivity!, id: '66666666-6666-4666-8666-666666666666' } }
    const get = vi.spyOn(api, 'getStudyMission').mockResolvedValueOnce(mission)
      .mockRejectedValueOnce(new ApiError({ kind: 'http', status: 503, code: 'UNAVAILABLE', message: 'private read' }))
      .mockResolvedValue(next)
    const request = vi.spyOn(api, 'continueStudyMission')
      .mockRejectedValueOnce(new ApiError({ kind: 'http', status: 502, code: 'BAD_GATEWAY', message: 'private write' }))
      .mockResolvedValue({ outcome: 'NO_ACTIVITY', actionType: 'RETRY_DEPENDENCY', rationaleCode: 'DEPENDENCY_UNAVAILABLE' })
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    await screen.findByRole('button', { name: 'Reload mission state' })
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    expect(request).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Reload mission state' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled())
    expect(get).toHaveBeenCalledTimes(3)
    expect(request).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
    expect(request).toHaveBeenLastCalledWith(missionId, next.currentActivity.id)
  })

  it.each([400, 403, 404, 422])('keeps deterministic HTTP %s errors outside ambiguous recovery', async (status) => {
    const get = vi.spyOn(api, 'getStudyMission').mockResolvedValue(mission)
    const request = vi.spyOn(api, 'continueStudyMission').mockRejectedValue(new ApiError({
      kind: 'http', status, code: 'REJECTED', message: 'private',
    }))
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    await screen.findByRole('alert')
    expect(get).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Reload mission state' })).not.toBeInTheDocument()
  })

  it('keeps saved ungraded work and Pause without offering another unassessable response', async () => {
    const saved = { ...submission, outcome: 'UNGRADED', correctConcepts: [], missingConcepts: [], misconceptions: [],
      feedback: 'Response saved.', limitationCode: 'EVALUATION_UNRESOLVED' }
    const unavailable: StudyMission = { ...retrievalMission,
      currentActivity: { ...retrievalMission.currentActivity!, status: 'COMPLETED' },
      latestSubmission: saved,
      continuation: { actionType: 'COMMUNICATE_LIMITATION', rationaleCode: 'ASSESSMENT_UNAVAILABLE', canMaterialize: false } }
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(unavailable)
    const continueRequest = vi.spyOn(api, 'continueStudyMission')
    renderMission()
    expect(await screen.findByText(/Automatic assessment is unavailable/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()
    expect(screen.getByText('Response saved.')).toBeInTheDocument()
    expect(continueRequest).not.toHaveBeenCalled()
  })
})

describe('empty initial mission recovery', () => {
  it('keeps initial COMPLETE nonterminal and offers Pause/Stop without Finish or generation', async () => {
    const retry = vi.spyOn(api, 'retryInitialActivity')
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({ ...mission, currentActivity: null,
      initialActivity: { startRequestId: missionId, state: 'NO_ACTIVITY', attemptsUsed: 0, maximumAttempts: 3,
        canRetry: false, retryAfterSeconds: null, reasonCode: 'INITIAL_COMPLETION_NOT_ADMISSIBLE',
        decision: { actionType: 'COMPLETE', rationaleCode: 'TIME_LIMIT' } } })
    renderMission()
    expect(await screen.findByText('No learning activity was completed. This mission remains active. You can pause or stop it.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Stop mission' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Finish|Retry initial/ })).not.toBeInTheDocument()
    expect(retry).not.toHaveBeenCalled()
  })
  it('makes one explicit initial retry and never turns refresh into a POST', async () => {
    const initial = { startRequestId: missionId, state: 'RETRYABLE' as const, attemptsUsed: 1,
      maximumAttempts: 3 as const, canRetry: true, retryAfterSeconds: null, reasonCode: 'DEPENDENCY_UNAVAILABLE' as const, decision: null }
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({ ...mission, currentActivity: null, initialActivity: initial })
    const retry = vi.spyOn(api, 'retryInitialActivity').mockResolvedValue({ missionId, startRequestId: missionId,
      missionStatus: 'ACTIVE', replayed: true, outcome: 'PREPARING', retryAfterSeconds: 5 })
    renderMission()
    fireEvent.click(await screen.findByRole('button', { name: 'Retry initial activity' }))
    await waitFor(() => expect(retry).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh mission state' })).not.toBeDisabled())
    fireEvent.click(screen.getByRole('button', { name: 'Refresh mission state' }))
    await waitFor(() => expect(retry).toHaveBeenCalledTimes(1))
  })
})
