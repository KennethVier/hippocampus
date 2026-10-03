import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../api/apiClient'
import {
  continueStudyMission,
  getStudyMission,
  pauseStudyMission,
  resumeStudyMission,
  submitActivityResponse,
  type ActivityResponseInput,
} from '../api/studyMissionApi'
import type { ActivitySubmission, StudyMission, StudyMissionLifecycle } from '../api/studyMissionContracts'
import { studyMissionKeys } from '../queries/studyMissionQueries'

const CONFLICT_NOTICE = 'This mission changed in another tab. The latest state has been loaded.'
const CONFLICT_RELOAD_ERROR = 'This mission changed in another tab, but the latest state could not be loaded.'

export function useStudyMission(missionId: string) {
  const queryClient = useQueryClient()
  const mission = useQuery({
    queryKey: studyMissionKeys.detail(missionId),
    queryFn: ({ signal }) => getStudyMission(missionId, signal),
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
  const submittingRef = useRef(false)
  const continuingRef = useRef(false)
  const lifecycleRef = useRef(false)
  const conflictStaleRef = useRef(false)
  const previousActivityId = useRef<string | null | undefined>(undefined)

  useEffect(() => {
    const activityId = mission.data?.currentActivity?.id ?? null
    if (previousActivityId.current !== undefined && previousActivityId.current !== activityId) {
      setSubmission(null)
      setConfirmedSubmissionId(null)
      setConfirmedContinueId(null)
      setInteractionError(null)
    }
    previousActivityId.current = activityId
  }, [mission.data?.currentActivity?.id])

  async function refreshAfterWrite() {
    await queryClient.refetchQueries({ queryKey: studyMissionKeys.detail(missionId), exact: true })
  }

  async function reloadAfterConflict() {
    setIsReloadingConflict(true)
    setNotice(null)
    setInteractionError(null)
    try {
      await queryClient.refetchQueries(
        { queryKey: studyMissionKeys.detail(missionId), exact: true },
        { throwOnError: true },
      )
      conflictStaleRef.current = false
      setConflictStale(false)
      setConflictReloadFailed(false)
      setNotice(CONFLICT_NOTICE)
    } catch {
      conflictStaleRef.current = true
      setConflictStale(true)
      setConflictReloadFailed(true)
    } finally {
      setIsReloadingConflict(false)
    }
  }

  async function handleError(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
      conflictStaleRef.current = true
      setConflictStale(true)
      setConflictReloadFailed(false)
      await reloadAfterConflict()
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
    onSuccess: async (_, activityId) => {
      setConfirmedContinueId(activityId)
      setInteractionError(null)
      await refreshAfterWrite()
    },
    onError: handleError,
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

  return {
    mission,
    submission,
    confirmedSubmissionId,
    confirmedContinueId,
    notice,
    interactionError,
    conflictStale,
    conflictReloadFailed,
    conflictReloadMessage: CONFLICT_RELOAD_ERROR,
    isReloadingConflict,
    dismissNotice: () => setNotice(null),
    reloadMissionState: reloadAfterConflict,
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
    isSubmitting: submitMutation.isPending,
    isContinuing: continueMutation.isPending,
    isPausing: pauseMutation.isPending,
    isResuming: resumeMutation.isPending,
  }
}
