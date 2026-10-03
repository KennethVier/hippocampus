import { useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../../../api/apiClient'
import { Button, ErrorState, Skeleton } from '../../../components/ui'
import { ActivityRenderer } from '../components/ActivityRenderer'
import { MissionInteraction } from '../components/MissionInteraction'
import { MissionTimer } from '../components/MissionTimer'
import { SourcePanel } from '../components/SourcePanel'
import { useStudyMission } from '../hooks/useStudyMission'
import '../studyMission.css'

export function StudyMissionPage() {
  const rawMissionId = useParams().missionId
  const missionId = rawMissionId && z.uuid().safeParse(rawMissionId).success ? rawMissionId : null
  const studyMission = useStudyMission(missionId ?? '')
  const { mission } = studyMission

  if (missionId === null) return <UnavailableMission />
  if (mission.isPending) return <MissionLoading />
  if (mission.data === undefined) {
    if (isMissingMission(mission.error)) return <UnavailableMission />
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
        <div className="mission-header-actions">
          <dl className="mission-context">
            {current.stage !== null ? <div><dt>Stage</dt><dd>{displayLabel(current.stage)}</dd></div> : null}
            <div><dt>Status</dt><dd>{displayLabel(current.status)}</dd></div>
          </dl>
          <MissionTimer availableTimeMinutes={current.availableTimeMinutes} status={current.status} />
          {current.status === 'ACTIVE' ? <Button disabled={studyMission.isPausing || studyMission.conflictStale} onClick={studyMission.pause} variant="secondary">{studyMission.isPausing ? 'Pausing…' : 'Pause'}</Button> : null}
          {current.status === 'PAUSED' ? <Button disabled={studyMission.isResuming || studyMission.conflictStale} onClick={studyMission.resume}>{studyMission.isResuming ? 'Resuming…' : 'Resume'}</Button> : null}
        </div>
      </header>

      {studyMission.notice ? <div className="mission-conflict" role="status"><span>{studyMission.notice}</span><Button onClick={studyMission.dismissNotice} variant="tertiary">Dismiss</Button></div> : null}
      {studyMission.conflictReloadFailed ? <div className="mission-refetch-error" role="alert"><span>{studyMission.conflictReloadMessage}</span><Button disabled={studyMission.isReloadingConflict} onClick={() => void studyMission.reloadMissionState()} variant="secondary">{studyMission.isReloadingConflict ? 'Reloading…' : 'Reload mission state'}</Button></div> : null}
      {mission.isError && mission.data !== undefined && !studyMission.conflictStale ? <div className="mission-refetch-error" role="alert"><span>The latest mission state could not be loaded.</span><Button onClick={() => void mission.refetch()} variant="secondary">Try again</Button></div> : null}

      {current.currentActivity === null ? (
        <section className="mission-empty" aria-labelledby="mission-empty-heading">
          <p className="mission-eyebrow">Current learning activity</p>
          <h2 id="mission-empty-heading">No activity is available right now</h2>
          <p>This mission has no current activity to present.</p>
        </section>
      ) : (
        <div className="mission-learning-layout">
          <div className="mission-main-column">
            <ActivityRenderer activity={current.currentActivity} />
            <MissionInteraction
              activity={current.currentActivity}
              confirmedContinue={studyMission.confirmedContinueId === current.currentActivity.id}
              confirmedSubmission={studyMission.confirmedSubmissionId === current.currentActivity.id}
              error={studyMission.interactionError}
              isContinuing={studyMission.isContinuing}
              isSubmitting={studyMission.isSubmitting}
              interactionBlocked={studyMission.conflictStale}
              key={current.currentActivity.id}
              missionStatus={current.status}
              onContinue={() => studyMission.continueMission(current.currentActivity!.id)}
              onRetryState={() => void mission.refetch()}
              onSubmit={(input) => studyMission.submitResponse(current.currentActivity!.id, input)}
              submission={studyMission.submission}
            />
          </div>
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
