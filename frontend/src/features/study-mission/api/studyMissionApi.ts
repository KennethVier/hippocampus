import { ApiError, apiClient } from '../../../api/apiClient'
import { studyMissionSchema, type StudyMission } from './studyMissionContracts'

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
