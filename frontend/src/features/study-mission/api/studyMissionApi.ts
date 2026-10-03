import { ApiError, apiClient } from '../../../api/apiClient'
import {
  activitySubmissionSchema,
  studyMissionLifecycleSchema,
  studyMissionSchema,
  type ActivitySubmission,
  type StudyMission,
  type StudyMissionLifecycle,
} from './studyMissionContracts'

export interface ActivityResponseInput {
  responseText?: string
  selectedOption?: string
}

export async function getStudyMission(missionId: string, signal?: AbortSignal): Promise<StudyMission> {
  const response = await apiClient.requestJson<unknown>(
    `/api/study-missions/${encodeURIComponent(missionId)}`,
    { signal },
  )
  const result = studyMissionSchema.safeParse(response)

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
  const response = await apiClient.requestJson<unknown>(
    `/api/study-missions/${encodeURIComponent(missionId)}/activities/${encodeURIComponent(activityId)}/responses`,
    { method: 'POST', body: input },
  )
  return parseResponse(activitySubmissionSchema, response)
}

export async function continueStudyMission(missionId: string, activityId: string): Promise<void> {
  await apiClient.requestJson(
    `/api/study-missions/${encodeURIComponent(missionId)}/activities/${encodeURIComponent(activityId)}/continue`,
    { method: 'POST' },
  )
}

export async function pauseStudyMission(missionId: string): Promise<StudyMissionLifecycle> {
  return changeMissionStatus(missionId, 'pause')
}

export async function resumeStudyMission(missionId: string): Promise<StudyMissionLifecycle> {
  return changeMissionStatus(missionId, 'resume')
}

async function changeMissionStatus(missionId: string, action: 'pause' | 'resume'): Promise<StudyMissionLifecycle> {
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
