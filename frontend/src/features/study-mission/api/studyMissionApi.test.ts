import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'

const transport = vi.hoisted(() => ({ json: vi.fn() }))
vi.mock('../../../api/apiClient', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../../api/apiClient')>()
  return { ...original, apiClient: { requestJson: transport.json } }
})

import { getStudyMission } from './studyMissionApi'

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
})
