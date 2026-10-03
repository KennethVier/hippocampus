import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'

const transport = vi.hoisted(() => ({ json: vi.fn() }))
vi.mock('../../../api/apiClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../../api/apiClient')>()
  return { ...original, apiClient: { requestJson: transport.json } }
})

import {
  continueStudyMission,
  getStudyMission,
  pauseStudyMission,
  resumeStudyMission,
  submitActivityResponse,
} from './studyMissionApi'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const activityId = '9a7b3302-b431-45e1-90e3-298c9d80918f'
const sourceReferenceId = '11111111-1111-4111-8111-111111111111'
const mission = {
  id: missionId,
  status: 'ACTIVE',
  stage: 'EXPLAIN',
  currentActivity: {
    id: activityId,
    type: 'EXPLANATION',
    status: 'PRESENTED',
    difficulty: null,
    classification: null,
    content: { concept: 'Cardiac output', explanation: 'Flow per minute.', keyPoints: [], limitations: [] },
    sources: [{ sourceReferenceId, materialTitle: 'Physiology', pageNumber: 14, displayLabel: 'Cardiac cycle' }],
  },
  availableTimeMinutes: 20,
  startedAt: '2026-10-02T01:00:00Z',
  completedAt: null,
  stoppedAt: null,
  updatedAt: '2026-10-02T01:01:00Z',
}

afterEach(() => transport.json.mockReset())

describe('study mission API contract', () => {
  it('requests through the API client with the query abort signal and validates the response', async () => {
    transport.json.mockResolvedValue(mission)
    const signal = new AbortController().signal
    await expect(getStudyMission(missionId, signal)).resolves.toEqual(mission)
    expect(transport.json).toHaveBeenCalledWith(`/api/study-missions/${missionId}`, { signal })
  })

  it('accepts a mission with no current learning stage', async () => {
    const missionWithoutLearningState = { ...mission, stage: null, currentActivity: null }
    transport.json.mockResolvedValue(missionWithoutLearningState)
    await expect(getStudyMission(missionId)).resolves.toEqual(missionWithoutLearningState)
  })

  it.each(['EXPLANATION', 'RETRIEVAL', 'CONNECTION', 'APPLICATION'] as const)('accepts %s with null generated content', async (type) => {
    const missionWithoutGeneratedContent = {
      ...mission,
      currentActivity: { ...mission.currentActivity, type, content: null },
    }
    transport.json.mockResolvedValue(missionWithoutGeneratedContent)
    await expect(getStudyMission(missionId)).resolves.toEqual(missionWithoutGeneratedContent)
  })

  it.each([
    undefined,
    { ...mission, id: 'not-a-uuid' },
    { ...mission, currentActivity: { ...mission.currentActivity, type: 'UNKNOWN' } },
    { ...mission, currentActivity: { ...mission.currentActivity, content: { rawText: 'provider output' } } },
  ])('rejects malformed mission presentation %#', async (value) => {
    transport.json.mockResolvedValue(value)
    await expect(getStudyMission(missionId)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('returns the established ApiError shape for invalid responses', async () => {
    transport.json.mockResolvedValue({})
    await expect(getStudyMission(missionId)).rejects.toBeInstanceOf(ApiError)
  })

  it('submits an activity response and validates learner-safe feedback', async () => {
    const result = {
      missionId,
      activityId,
      outcome: 'PARTIAL',
      correctConcepts: ['Preload'],
      missingConcepts: ['Afterload'],
      misconceptions: [],
      feedback: 'Review the effect of afterload.',
      missionStatus: 'ACTIVE',
      stage: 'RETRIEVE',
      updatedAt: '2026-10-02T01:02:00Z',
      continuationAvailable: true,
    }
    transport.json.mockResolvedValue(result)

    await expect(submitActivityResponse(missionId, activityId, { selectedOption: 'option-b' })).resolves.toEqual(result)
    expect(transport.json).toHaveBeenCalledWith(
      `/api/study-missions/${missionId}/activities/${activityId}/responses`,
      { method: 'POST', body: { selectedOption: 'option-b' } },
    )
  })

  it('rejects submission responses containing private evaluation fields', async () => {
    transport.json.mockResolvedValue({
      missionId, activityId, outcome: 'CORRECT', correctConcepts: [], missingConcepts: [], misconceptions: [],
      feedback: 'Good work.', missionStatus: 'ACTIVE', stage: 'RETRIEVE', updatedAt: '2026-10-02T01:02:00Z',
      continuationAvailable: true, expectedAnswer: 'private',
    })
    await expect(submitActivityResponse(missionId, activityId, { responseText: 'answer' })).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('continues without sending a client-selected next action', async () => {
    transport.json.mockResolvedValue(undefined)
    await expect(continueStudyMission(missionId, activityId)).resolves.toBeUndefined()
    expect(transport.json).toHaveBeenCalledWith(
      `/api/study-missions/${missionId}/activities/${activityId}/continue`,
      { method: 'POST' },
    )
  })

  it.each(['pause', 'resume'] as const)('validates the %s lifecycle response', async (action) => {
    const lifecycle = {
      id: missionId,
      status: action === 'pause' ? 'PAUSED' : 'ACTIVE',
      currentActivityId: activityId,
      sourceScopes: [{
        materialId: '22222222-2222-4222-8222-222222222222',
        materialVersionId: '33333333-3333-4333-8333-333333333333',
        documentNodeId: '44444444-4444-4444-8444-444444444444',
      }],
      startedAt: '2026-10-02T01:00:00Z', completedAt: null, stoppedAt: null, updatedAt: '2026-10-02T01:02:00Z',
    }
    transport.json.mockResolvedValue(lifecycle)
    const request = action === 'pause' ? pauseStudyMission : resumeStudyMission
    await expect(request(missionId)).resolves.toEqual(lifecycle)
    expect(transport.json).toHaveBeenCalledWith(`/api/study-missions/${missionId}/${action}`, { method: 'POST' })
  })
})
