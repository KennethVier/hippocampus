import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../../../api/apiClient'
import { Button, ErrorState, Skeleton } from '../../../components/ui'
import { currentSessionQueryOptions } from '../../auth/currentSessionQuery'
import { clearMissionStart, eligibleMissionSources, pendingMissionStart, rememberMissionStart, startStudyMission } from '../api/studyMissionApi'
import { startMissionRequestSchema, type StartMissionRequest } from '../api/studyMissionContracts'
import { studyMissionKeys } from '../queries/studyMissionQueries'
import '../studyMission.css'

export function StudyMissionStartPage() {
  const rawTopic = useParams().topicId
  const topic = rawTopic && z.uuid().safeParse(rawTopic).success ? rawTopic : null
  const session = useQuery(currentSessionQueryOptions)
  if (!topic) return <ErrorState title="Topic unavailable" description="Select an active Topic from your Subjects." />
  if (session.isError) return <ErrorState title="Session unavailable" description="Check your session before starting a mission." action={<Button onClick={() => void session.refetch()}>Try again</Button>} />
  if (!session.data) return <Skeleton label="Checking your session" />
  return <StartForm key={session.data.userId + topic} ownerId={session.data.userId} topicId={topic} />
}

function StartForm({ ownerId, topicId }: { readonly ownerId: string; readonly topicId: string }) {
  const navigate = useNavigate()
  const [objective, setObjective] = useState('')
  const [minutes, setMinutes] = useState('20')
  const [selected, setSelected] = useState<readonly string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [pendingKey, setPendingKey] = useState(() => pendingMissionStart(ownerId, topicId))
  const frozenSubmission = useRef<StartMissionRequest | null>(null)
  const sending = useRef(false)
  const sources = useInfiniteQuery({
    queryKey: studyMissionKeys.sources(ownerId, topicId),
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) => eligibleMissionSources(topicId, pageParam, signal),
    getNextPageParam: (page) => page.nextAfterMaterialId ?? undefined,
  })
  const start = useMutation({
    mutationFn: startStudyMission,
    retry: false,
    onSuccess: (result) => {
      clearMissionStart(ownerId, topicId)
      navigate('/missions/' + result.missionId)
    },
    onError: (failure) => {
      setError(failure instanceof ApiError && failure.code === 'STUDY_MISSION_START_REQUEST_CONFLICT'
        ? 'This request belongs to different mission details. Restore the original details or explicitly start a new mission.'
        : 'The start result could not be confirmed. Resubmit the same details to recover the same mission.')
    },
    onSettled: () => { sending.current = false },
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sending.current) return
    const key = pendingKey ?? crypto.randomUUID()
    const input = frozenSubmission.current ?? {
      startRequestId: key, topicId, learningObjectiveText: objective,
      availableTimeMinutes: Number(minutes), groundingMode: 'STRICT_SOURCE',
      sourceScopes: selected.map((materialId) => ({ materialId })),
    }
    const checked = startMissionRequestSchema.safeParse(input)
    if (!checked.success || objective.trim().length === 0) {
      setError('Enter an objective, choose 1–8 materials, and set 1–240 whole minutes.')
      return
    }
    frozenSubmission.current = checked.data
    rememberMissionStart(ownerId, topicId, key)
    setPendingKey(key)
    setError(null)
    sending.current = true
    start.mutate(checked.data)
  }
  if (sources.isPending) return <Skeleton label="Loading eligible mission materials" />
  if (!sources.data) return <ErrorState title="Mission sources unavailable" description="Check that the Topic is active and try again." action={<Button onClick={() => void sources.refetch()}>Try again</Button>} />
  const pages = sources.data.pages
  const materials = pages.flatMap((page) => page.items)
  const locked = start.isPending || frozenSubmission.current !== null
  return <section className="study-mission-page" aria-labelledby="start-mission-title">
    <header className="mission-header"><div><p className="mission-eyebrow">{pages[0]?.topic.name}</p><h1 id="start-mission-title">Start a Study Mission</h1></div></header>
    <p>Study from your selected processed materials. Your sources are fixed when the mission starts.</p>
    {pendingKey ? <p role="status">A previous start may already exist. Use the original details to recover it. Form text is not saved after refresh.</p> : null}
    <form onSubmit={submit}>
      <fieldset disabled={locked}>
        <legend>Mission objective and sources</legend>
        <label htmlFor="mission-objective">What do you want to understand?</label>
        <textarea id="mission-objective" required maxLength={2000} value={objective} onChange={(event) => setObjective(event.target.value)} />
        <label htmlFor="mission-minutes">Available time in minutes</label>
        <input id="mission-minutes" type="number" required min={1} max={240} step={1} value={minutes} onChange={(event) => setMinutes(event.target.value)} />
        <fieldset><legend>Choose 1–8 whole materials</legend>
          {materials.length === 0 ? <p>No eligible processed materials are linked to this Topic.</p> : null}
          {materials.map((material) => <label key={material.materialId}>
            <input type="checkbox" checked={selected.includes(material.materialId)}
              disabled={!selected.includes(material.materialId) && selected.length >= 8}
              onChange={(event) => setSelected((current) => event.target.checked ? [...current, material.materialId] : current.filter((id) => id !== material.materialId))} />
            {material.title}{material.readiness === 'LIMITED' ? ' — Partially ready' : ''}
          </label>)}
        </fieldset>
      </fieldset>
      {sources.hasNextPage ? <Button type="button" disabled={locked || sources.isFetchingNextPage} onClick={() => void sources.fetchNextPage()} variant="secondary">Load more materials</Button> : null}
      {sources.isError ? <p role="alert">The latest source list could not be loaded.</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      <Button type="submit" disabled={start.isPending || materials.length === 0}>{start.isPending ? 'Preparing mission…' : pendingKey ? 'Recover this mission' : 'Start mission'}</Button>
      {pendingKey ? <Button type="button" variant="secondary" disabled={start.isPending} onClick={() => {
        clearMissionStart(ownerId, topicId); setPendingKey(null); frozenSubmission.current = null; setError(null)
      }}>Start a new mission instead</Button> : null}
    </form>
  </section>
}
