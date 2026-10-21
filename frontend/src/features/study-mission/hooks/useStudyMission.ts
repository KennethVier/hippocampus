import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../api/apiClient'
import {
  continueStudyMission,
  getStudyMission,
  pauseStudyMission,
  resumeStudyMission,
  stopStudyMission, retryInitialActivity,
  submitActivityResponse,
  type ActivityResponseInput,
} from '../api/studyMissionApi'
import type { ActivitySubmission, StudyMission, StudyMissionLifecycle } from '../api/studyMissionContracts'
import { studyMissionKeys } from '../queries/studyMissionQueries'

const CONFLICT_NOTICE = 'This mission changed in another tab. The latest state has been loaded.'
const CONFLICT_RELOAD_ERROR = 'This mission changed in another tab, but the latest state could not be loaded.'
const RECOVERY_NOTICE = 'The latest mission state has been loaded. Check it before continuing.'
const RECOVERY_RELOAD_ERROR = 'The action result could not be confirmed, and the latest mission state could not be loaded. Reload the mission state before trying again.'
type RecoveryReason = 'conflict' | 'ambiguous'

export function useStudyMission(missionId: string) {
  const queryClient = useQueryClient()
  const mission = useQuery({
    queryKey: studyMissionKeys.detail(missionId),
    queryFn: ({ signal }) => getStudyMission(missionId, signal, true),
    enabled: missionId.length > 0,
  })
  const [submission, setSubmission] = useState<ActivitySubmission | null>(null)
  const [confirmedSubmissionId, setConfirmedSubmissionId] = useState<string | null>(null)
  const [confirmedContinueId, setConfirmedContinueId] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [interactionError, setInteractionError] = useState<string | null>(null)
  const [conflictStale, setConflictStale] = useState(false)
  const [conflictReloadFailed, setConflictReloadFailed] = useState(false)
  const [isReloadingConflict, setIsReloadingConflict] = useState(false)
  const [recoveryReason, setRecoveryReason] = useState<RecoveryReason | null>(null)
  const submittingRef = useRef(false)
  const continuingRef = useRef(false)
  const initialRef = useRef(false)
  const lifecycleRef = useRef(false)
  const conflictStaleRef = useRef(false)
  const previousMissionId = useRef(missionId)
  const previousActivityId = useRef<string | null | undefined>(undefined)

  useEffect(() => {
    if (previousMissionId.current !== missionId) {
      previousMissionId.current = missionId
      setRecoveryReason(null)
      setNotice(null)
      setInteractionError(null)
      setConflictStale(false)
      setConflictReloadFailed(false)
      setIsReloadingConflict(false)
      conflictStaleRef.current = false
    }
    const activityId = mission.data?.currentActivity?.id ?? null
    if (previousActivityId.current !== undefined && previousActivityId.current !== activityId) {
      setSubmission(null)
      setConfirmedSubmissionId(null)
      setConfirmedContinueId(null)
      setInteractionError(null)
    }
    previousActivityId.current = activityId
  }, [missionId, mission.data?.currentActivity?.id])

  async function refreshAfterWrite() {
    await queryClient.refetchQueries({ queryKey: studyMissionKeys.detail(missionId), exact: true })
  }

  async function reloadAfterConflict(reason: RecoveryReason = recoveryReason ?? 'conflict') {
    if (previousMissionId.current !== missionId) return
    setRecoveryReason(reason)
    setIsReloadingConflict(true)
    setNotice(null)
    setInteractionError(null)
    try {
      await queryClient.refetchQueries(
        { queryKey: studyMissionKeys.detail(missionId), exact: true },
        { throwOnError: true },
      )
      if (previousMissionId.current !== missionId) return
      conflictStaleRef.current = false
      setConflictStale(false)
      setConflictReloadFailed(false)
      setNotice(reason === 'conflict' ? CONFLICT_NOTICE : RECOVERY_NOTICE)
      setRecoveryReason(null)
    } catch {
      if (previousMissionId.current !== missionId) return
      conflictStaleRef.current = true
      setConflictStale(true)
      setConflictReloadFailed(true)
    } finally {
      if (previousMissionId.current === missionId) setIsReloadingConflict(false)
    }
  }

  async function handleError(error: unknown) {
    if (previousMissionId.current !== missionId) return
    if (error instanceof ApiError && error.status === 409) {
      conflictStaleRef.current = true
      setConflictStale(true)
      setConflictReloadFailed(false)
      await reloadAfterConflict('conflict')
      return
    }
    setInteractionError(error instanceof ApiError && error.status === 400
      ? 'Check your response and try again.'
      : 'That action could not be completed. Please try again.')
  }

  const submitMutation = useMutation({
    mutationFn: ({ activityId, input }: { activityId: string; input: ActivityResponseInput }) =>
      submitActivityResponse(missionId, activityId, input),
    retry: false,
    onSuccess: async (result) => {
      setSubmission(result)
      setConfirmedSubmissionId(result.activityId)
      setInteractionError(null)
      await refreshAfterWrite()
    },
    onError: handleError,
    onSettled: () => { submittingRef.current = false },
  })

  const continueMutation = useMutation({
    mutationFn: (activityId: string) => continueStudyMission(missionId, activityId),
    retry: false,
    onSuccess: async (result, activityId) => {
      setConfirmedContinueId(result.outcome === 'ACTIVITY_CREATED' ? activityId : null)
      setInteractionError(null)
      await refreshAfterWrite()
    },
    onError: async (error) => {
      if (previousMissionId.current !== missionId) return
      if (error instanceof ApiError && (error.kind === 'network' || error.kind === 'invalid-response'
        || error.kind === 'http' && error.status !== null && error.status >= 500 && error.status <= 599)) {
        conflictStaleRef.current = true
        setConflictStale(true)
        await reloadAfterConflict('ambiguous')
        return
      }
      await handleError(error)
    },
    onSettled: () => { continuingRef.current = false },
  })

  function applyLifecycle(result: StudyMissionLifecycle) {
    queryClient.setQueryData<StudyMission>(studyMissionKeys.detail(missionId), (current) => current ? {
      ...current,
      status: result.status,
      startedAt: result.startedAt,
      completedAt: result.completedAt,
      stoppedAt: result.stoppedAt,
      updatedAt: result.updatedAt,
    } : current)
  }

  const pauseMutation = useMutation({
    mutationFn: () => pauseStudyMission(missionId), retry: false,
    onSuccess: async (result) => { applyLifecycle(result); setInteractionError(null); await refreshAfterWrite() },
    onError: handleError,
    onSettled: () => { lifecycleRef.current = false },
  })
  const resumeMutation = useMutation({
    mutationFn: () => resumeStudyMission(missionId), retry: false,
    onSuccess: async (result) => { applyLifecycle(result); setInteractionError(null); await refreshAfterWrite() },
    onError: handleError,
    onSettled: () => { lifecycleRef.current = false },
  })

  const stopMutation = useMutation({
    mutationFn: () => stopStudyMission(missionId), retry: false,
    onSuccess: async (result) => { applyLifecycle(result); setInteractionError(null); await refreshAfterWrite() },
    onError: handleError,
    onSettled: () => { lifecycleRef.current = false },
  })
  const initialMutation = useMutation({
    mutationFn: (key: string) => retryInitialActivity(missionId, key), retry: false,
    onSuccess: async () => { setInteractionError(null); await refreshAfterWrite() },
    onError: async () => { await reloadAfterConflict('ambiguous') },
    onSettled: () => { initialRef.current = false },
  })

  return {
    mission,
    submission: submission ?? mission.data?.latestSubmission ?? null,
    confirmedSubmissionId: confirmedSubmissionId ?? mission.data?.latestSubmission?.activityId ?? null,
    confirmedContinueId,
    notice,
    interactionError,
    conflictStale,
    conflictReloadFailed,
    conflictReloadMessage: recoveryReason === 'ambiguous' ? RECOVERY_RELOAD_ERROR : CONFLICT_RELOAD_ERROR,
    isReloadingConflict,
    dismissNotice: () => setNotice(null),
    reloadMissionState: () => reloadAfterConflict(),
    submitResponse(activityId: string, input: ActivityResponseInput) {
      if (conflictStaleRef.current || submittingRef.current || confirmedSubmissionId === activityId) return
      submittingRef.current = true
      setInteractionError(null)
      submitMutation.mutate({ activityId, input })
    },
    continueMission(activityId: string) {
      if (conflictStaleRef.current || continuingRef.current || confirmedContinueId === activityId) return
      continuingRef.current = true
      setInteractionError(null)
      continueMutation.mutate(activityId)
    },
    pause: () => {
      if (conflictStaleRef.current || lifecycleRef.current) return
      lifecycleRef.current = true
      pauseMutation.mutate()
    },
    resume: () => {
      if (conflictStaleRef.current || lifecycleRef.current) return
      lifecycleRef.current = true
      resumeMutation.mutate()
    },
    retryInitial: () => {
      const initial = mission.data?.initialActivity
      if (!initial || !initial.canRetry || initial.retryAfterSeconds !== null || initialRef.current
          || conflictStaleRef.current || lifecycleRef.current || mission.isFetching) return
      initialRef.current = true
      initialMutation.mutate(initial.startRequestId)
    },
    stop: () => {
      if (conflictStaleRef.current || lifecycleRef.current) return
      lifecycleRef.current = true
      stopMutation.mutate()
    },
    isRetryingInitial: initialMutation.isPending,
    isStopping: stopMutation.isPending,
    isSubmitting: submitMutation.isPending,
    isContinuing: continueMutation.isPending,
    isPausing: pauseMutation.isPending,
    isResuming: resumeMutation.isPending,
  }
}
