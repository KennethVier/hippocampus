import { registerPrivateClientCleanup } from '../../auth/clearPrivateClientState'
import { ApiError, apiClient } from '../../../api/apiClient'
import {
  activitySubmissionSchema,
  studyMissionWithInitialSchema, startMissionRequestSchema, startMissionResultSchema, missionSourceSelectorSchema,
  type StartMissionRequest,
  studyMissionLifecycleSchema,
  studyMissionWithContinuationSchema,
  continuationResultSchema,
  type ActivitySubmission,
  type StudyMission,
  type StudyMissionLifecycle,
} from './studyMissionContracts'

export interface ActivityResponseInput {
  responseText?: string
  selectedOption?: string
  requestId?: string
}

export async function getStudyMission(missionId: string, signal?: AbortSignal, initialOptIn = false): Promise<StudyMission> {
  const response = await apiClient.requestJson<unknown>(
    `/api/study-missions/${encodeURIComponent(missionId)}?continuationContract=1${initialOptIn ? '&initialActivityContract=1' : ''}`,
    { signal },
  )
  const result = (initialOptIn ? studyMissionWithInitialSchema : studyMissionWithContinuationSchema).safeParse(response)

  if (!result.success) {
    throw new ApiError({
      kind: 'invalid-response',
      status: 200,
      code: 'INVALID_RESPONSE',
      message: 'The server returned an invalid response.',
    })
  }

  return result.data
}

export async function submitActivityResponse(
  missionId: string,
  activityId: string,
  input: ActivityResponseInput,
): Promise<ActivitySubmission> {
  const requestId = input.requestId ?? submissionRequestId(missionId, activityId)
  try {
    const response = await apiClient.requestJson<unknown>(
      `/api/study-missions/${encodeURIComponent(missionId)}/activities/${encodeURIComponent(activityId)}/responses`,
      { method: 'POST', body: { ...input, requestId } },
    )
    return parseResponse(activitySubmissionSchema, response)
  } catch (error) {
    if (input.requestId === undefined && error instanceof ApiError && error.kind === 'http' && error.status === 400) {
      const key = submissionRequestKey(missionId, activityId)
      if (sessionStorage.getItem(key) === requestId) sessionStorage.removeItem(key)
    }
    throw error
  }
}

function submissionRequestId(missionId: string, activityId: string): string {
  const key = submissionRequestKey(missionId, activityId)
  const existing = sessionStorage.getItem(key)
  if (existing) return existing
  const requestId = crypto.randomUUID()
  sessionStorage.setItem(key, requestId)
  return requestId
}

function submissionRequestKey(missionId: string, activityId: string): string {
  return `hippocampus:submission:${missionId}:${activityId}`
}

export async function continueStudyMission(missionId: string, activityId: string) {
  const response = await apiClient.requestJson<unknown>(
    `/api/study-missions/${encodeURIComponent(missionId)}/activities/${encodeURIComponent(activityId)}/continue?continuationContract=1`,
    { method: 'POST' },
  )
  return parseResponse(continuationResultSchema, response)
}

export async function pauseStudyMission(missionId: string): Promise<StudyMissionLifecycle> {
  return changeMissionStatus(missionId, 'pause')
}

export async function resumeStudyMission(missionId: string): Promise<StudyMissionLifecycle> {
  return changeMissionStatus(missionId, 'resume')
}

async function changeMissionStatus(missionId: string, action: 'pause' | 'resume' | 'stop'): Promise<StudyMissionLifecycle> {
  const response = await apiClient.requestJson<unknown>(
    `/api/study-missions/${encodeURIComponent(missionId)}/${action}`,
    { method: 'POST' },
  )
  return parseResponse(studyMissionLifecycleSchema, response)
}

function parseResponse<T>(schema: { safeParse: (value: unknown) => { success: true; data: T } | { success: false } }, value: unknown): T {
  const result = schema.safeParse(value)
  if (!result.success) {
    throw new ApiError({ kind: 'invalid-response', status: 200, code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' })
  }
  return result.data
}

export function stopStudyMission(missionId: string): Promise<StudyMissionLifecycle> {
  return changeMissionStatus(missionId, 'stop')
}
export async function startStudyMission(input: StartMissionRequest) {
  const validated = startMissionRequestSchema.parse(input)
  return parseResponse(startMissionResultSchema, await apiClient.requestJson<unknown>('/api/study-missions',
    { method: 'POST', body: validated }))
}
export async function retryInitialActivity(missionId: string, startRequestId: string) {
  return parseResponse(startMissionResultSchema, await apiClient.requestJson<unknown>(
    '/api/study-missions/' + encodeURIComponent(missionId) + '/initial-activity',
    { method: 'POST', body: { startRequestId } }))
}
export async function eligibleMissionSources(topicId: string, after: string | null, signal?: AbortSignal) {
  const query = new URLSearchParams({ limit: '20' })
  if (after) query.set('afterMaterialId', after)
  return parseResponse(missionSourceSelectorSchema, await apiClient.requestJson<unknown>(
    '/api/topics/' + encodeURIComponent(topicId) + '/study-mission-sources?' + query.toString(), { signal }))
}
const pendingPrefix = 'hippocampus:mission-start:'
registerPrivateClientCleanup(() => {
  for (const key of Object.keys(sessionStorage)) if (key.startsWith(pendingPrefix)) sessionStorage.removeItem(key)
})
export function pendingMissionStart(ownerId: string, topicId: string): string | null {
  const key = sessionStorage.getItem(pendingPrefix + ownerId + ':' + topicId)
  return key && /^[0-9a-f-]{36}$/.test(key) ? key : null
}
export function rememberMissionStart(ownerId: string, topicId: string, requestId: string): void {
  sessionStorage.setItem(pendingPrefix + ownerId + ':' + topicId, requestId)
}
export function clearMissionStart(ownerId: string, topicId: string): void {
  sessionStorage.removeItem(pendingPrefix + ownerId + ':' + topicId)
}
