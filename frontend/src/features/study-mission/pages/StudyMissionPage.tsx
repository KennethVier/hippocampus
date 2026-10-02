import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../../../api/apiClient'
import { Button, ErrorState, Skeleton } from '../../../components/ui'
import { getStudyMission } from '../api/studyMissionApi'
import { ActivityRenderer } from '../components/ActivityRenderer'
import { SourcePanel } from '../components/SourcePanel'
import { studyMissionKeys } from '../queries/studyMissionQueries'
import '../studyMission.css'

export function StudyMissionPage() {
  const rawMissionId = useParams().missionId
  const missionId = rawMissionId && z.uuid().safeParse(rawMissionId).success ? rawMissionId : null
  const mission = useQuery({
    queryKey: studyMissionKeys.detail(missionId ?? 'invalid'),
    queryFn: ({ signal }) => getStudyMission(missionId ?? '', signal),
    enabled: missionId !== null,
  })

  if (missionId === null || isMissingMission(mission.error)) return <UnavailableMission />
  if (mission.isPending) return <MissionLoading />
  if (mission.isError) {
    return <ErrorState title="Study mission could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void mission.refetch()}>Try again</Button>} />
  }

  const current = mission.data
  return (
    <section aria-labelledby="study-mission-title" className="study-mission-page">
      <header className="mission-header">
        <div>
          <p className="mission-eyebrow">Focused study</p>
          <h1 id="study-mission-title">Study Mission</h1>
        </div>
        <dl className="mission-context">
          {current.stage !== null ? <div><dt>Stage</dt><dd>{displayLabel(current.stage)}</dd></div> : null}
          <div><dt>Status</dt><dd>{displayLabel(current.status)}</dd></div>
          {current.availableTimeMinutes !== null ? <div><dt>Available time</dt><dd>{current.availableTimeMinutes} minutes</dd></div> : null}
        </dl>
      </header>

      {current.currentActivity === null ? (
        <section className="mission-empty" aria-labelledby="mission-empty-heading">
          <p className="mission-eyebrow">Current learning activity</p>
          <h2 id="mission-empty-heading">No activity is available right now</h2>
          <p>This mission has no current activity to present.</p>
        </section>
      ) : (
        <div className="mission-learning-layout">
          <ActivityRenderer activity={current.currentActivity} />
          <SourcePanel sources={current.currentActivity.sources} />
        </div>
      )}
    </section>
  )
}

function MissionLoading() {
  return <section aria-label="Loading Study Mission" className="study-mission-page"><Skeleton label="Loading Study Mission" /><div className="mission-loading-layout"><Skeleton /><Skeleton /></div></section>
}

function UnavailableMission() {
  return <ErrorState title="Study mission unavailable" description="This study mission could not be found or is not available to you." />
}

function isMissingMission(error: unknown): boolean {
  return error instanceof ApiError && (error.code === 'STUDY_MISSION_NOT_FOUND' || error.status === 404)
}

function displayLabel(value: string): string {
  return value.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')
}
