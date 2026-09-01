import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { z } from 'zod'

import { ApiError } from '../../../api/apiClient'
import { Badge, Button, Dialog, EmptyState, ErrorState, Skeleton } from '../../../components/ui'
import { archiveSubject, archiveTopic, createTopic, getSubject, listTopics, updateSubject, updateTopic } from '../api/learningOrganizationApi'
import type { Subject, Topic } from '../api/learningOrganizationContracts'
import { ArchiveConfirmation } from '../components/ArchiveConfirmation'
import { OrganizationCard } from '../components/OrganizationCard'
import { OrganizationPagination } from '../components/OrganizationPagination'
import { SubjectForm } from '../forms/SubjectForm'
import type { SubjectFormValues } from '../forms/subjectFormSchema'
import { TopicForm } from '../forms/TopicForm'
import type { TopicFormValues } from '../forms/topicFormSchema'
import { subjectMutationError, topicMutationError } from '../learningOrganizationErrors'
import { organizationKeys } from '../queries/learningOrganizationQueries'
import '../learningOrganization.css'

const PAGE_SIZE = 12
const uuid = z.uuid()

function pageFrom(value: string | null): number { return value !== null && /^\d+$/.test(value) && Number(value) >= 1 ? Number(value) : 1 }
function isCode(error: unknown, code: string): boolean { return error instanceof ApiError && error.code === code }
function nullableDescription(value: string): string | null { return value.trim().length === 0 ? null : value }

export function SubjectDetailPage() {
  const { subjectId: rawSubjectId } = useParams()
  const subjectId = rawSubjectId && uuid.safeParse(rawSubjectId).success ? rawSubjectId : null
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const rawPage = params.get('topicsPage')
  const page = pageFrom(rawPage)
  const [subjectFormOpen, setSubjectFormOpen] = useState(false)
  const [subjectArchiveOpen, setSubjectArchiveOpen] = useState(false)
  const [topicForm, setTopicForm] = useState<'create' | Topic | null>(null)
  const [topicArchive, setTopicArchive] = useState<Topic | null>(null)
  const subject = useQuery({ queryKey: organizationKeys.subject(subjectId ?? 'invalid'), queryFn: ({ signal }) => getSubject(subjectId ?? '', signal), enabled: subjectId !== null })
  const topicsEnabled = subjectId !== null && subject.isSuccess && subject.data.status === 'ACTIVE'
  const topics = useQuery({ queryKey: organizationKeys.topics(subjectId ?? 'invalid', page - 1, PAGE_SIZE), queryFn: ({ signal }) => listTopics(subjectId ?? '', page - 1, PAGE_SIZE, signal), enabled: topicsEnabled })

  useEffect(() => {
    if (rawPage !== null && rawPage !== String(page)) setParams({ topicsPage: String(page) }, { replace: true })
  }, [page, rawPage, setParams])
  useEffect(() => {
    if (topics.data && topics.data.totalPages > 0 && page > topics.data.totalPages) setParams({ topicsPage: String(topics.data.totalPages) }, { replace: true })
  }, [page, setParams, topics.data])
  useEffect(() => {
    if (isCode(topics.error, 'SUBJECT_NOT_FOUND') && subjectId) {
      void queryClient.invalidateQueries({ queryKey: organizationKeys.subject(subjectId) })
    }
  }, [queryClient, subjectId, topics.error])

  const invalidateSubjectLists = () => queryClient.invalidateQueries({ queryKey: organizationKeys.subjectLists })
  const invalidateTopics = (id: string) => queryClient.invalidateQueries({ queryKey: organizationKeys.topicLists(id) })
  async function reconcileSubject(error: unknown) {
    if (isCode(error, 'SUBJECT_NOT_FOUND') && subjectId) {
      setSubjectFormOpen(false); setSubjectArchiveOpen(false); setTopicForm(null); setTopicArchive(null)
      await queryClient.invalidateQueries({ queryKey: organizationKeys.subject(subjectId) })
    }
  }
  async function reconcileTopic(error: unknown) {
    if (isCode(error, 'SUBJECT_NOT_FOUND')) {
      await reconcileSubject(error)
    } else if (isCode(error, 'TOPIC_NOT_FOUND') && subjectId) {
      setTopicForm(null); setTopicArchive(null)
      await invalidateTopics(subjectId)
    }
  }

  const updateSubjectMutation = useMutation({ mutationFn: ({ current, values }: { current: Subject; values: SubjectFormValues }) => updateSubject(current.id, { name: values.name, description: nullableDescription(values.description), sortOrder: current.sortOrder }), onSuccess: async (value) => { queryClient.setQueryData(organizationKeys.subject(value.id), value); setSubjectFormOpen(false); await invalidateSubjectLists() }, onError: reconcileSubject })
  const archiveSubjectMutation = useMutation({ mutationFn: archiveSubject, onSuccess: async () => { await invalidateSubjectLists(); setSubjectArchiveOpen(false); navigate('/subjects') }, onError: reconcileSubject })
  const createTopicMutation = useMutation({ mutationFn: (values: TopicFormValues) => createTopic(subjectId ?? '', { name: values.name, description: nullableDescription(values.description) }), onSuccess: async (value) => { setTopicForm(null); await invalidateTopics(value.subjectId) }, onError: reconcileTopic })
  const updateTopicMutation = useMutation({ mutationFn: ({ current, values }: { current: Topic; values: TopicFormValues }) => updateTopic(current.id, { name: values.name, description: nullableDescription(values.description) }), onSuccess: async (value) => { setTopicForm(null); await invalidateTopics(value.subjectId) }, onError: reconcileTopic })
  const archiveTopicMutation = useMutation({ mutationFn: archiveTopic, onSuccess: async (value) => { setTopicArchive(null); await invalidateTopics(value.subjectId) }, onError: reconcileTopic })

  if (subjectId === null || isCode(subject.error, 'SUBJECT_NOT_FOUND')) return <UnavailableSubject />
  if (subject.isPending) return <section className="organization-page"><Skeleton label="Loading Subject" /><Skeleton /></section>
  if (subject.isError) return <ErrorState title="Subject could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void subject.refetch()}>Try again</Button>} />

  const current = subject.data
  const archived = current.status === 'ARCHIVED'
  const activeTopicError = topicForm === 'create' ? createTopicMutation.error : topicForm ? updateTopicMutation.error : null

  return (
    <section className="organization-page" aria-labelledby="subject-title">
      <nav aria-label="Breadcrumb"><Link to="/subjects">Subjects</Link><span aria-hidden="true"> / </span><span>{current.name}</span></nav>
      <div className="organization-page-header">
        <div><p className="organization-eyebrow">Subject</p><div className="organization-title-row"><h1 id="subject-title">{current.name}</h1>{archived ? <Badge>Archived</Badge> : null}</div>{current.description ? <p>{current.description}</p> : null}</div>
        <div className="organization-actions"><Button onClick={() => { updateSubjectMutation.reset(); setSubjectFormOpen(true) }} variant="tertiary">Edit Subject</Button>{!archived ? <Button onClick={() => { archiveSubjectMutation.reset(); setSubjectArchiveOpen(true) }} variant="tertiary">Archive Subject</Button> : null}</div>
      </div>
      {archived ? <EmptyState title="This Subject is archived" description="It no longer appears in active Subjects. Its Topics and study information were not deleted." action={<Link className="organization-link-action" to="/subjects">Back to Subjects</Link>} /> : (
        <section className="organization-section" aria-labelledby="topics-title">
          <div className="organization-section-header"><div><h2 id="topics-title">Topics</h2><p>Organize the areas you want to study within this Subject.</p></div><Button onClick={() => { createTopicMutation.reset(); setTopicForm('create') }}>Create Topic</Button></div>
          {topics.isPending ? <div className="organization-grid" aria-label="Loading Topics"><Skeleton label="Loading Topics" /><Skeleton /></div> : null}
          {topics.isError ? <ErrorState title="Topics could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void topics.refetch()}>Try again</Button>} /> : null}
          {topics.data?.items.length === 0 ? <EmptyState title="No Topics yet" description="Create a Topic to organize what you want to study." action={<Button onClick={() => { createTopicMutation.reset(); setTopicForm('create') }}>Create Topic</Button>} /> : null}
          {topics.data?.items.length ? <div className="organization-grid">{topics.data.items.map((topic) => <OrganizationCard key={topic.id} kind="Topic" name={topic.name} description={topic.description} headingLevel={3} onEdit={() => { updateTopicMutation.reset(); setTopicForm(topic) }} onArchive={() => { archiveTopicMutation.reset(); setTopicArchive(topic) }} />)}</div> : null}
          {topics.data ? <OrganizationPagination page={page} totalPages={topics.data.totalPages} onPage={(next) => setParams({ topicsPage: String(next) })} /> : null}
        </section>
      )}
      {subjectFormOpen ? (
        <Dialog open onClose={() => setSubjectFormOpen(false)} title="Edit Subject">
          <SubjectForm initialValues={{ name: current.name, description: current.description ?? '' }} pending={updateSubjectMutation.isPending} serverError={updateSubjectMutation.error ? subjectMutationError(updateSubjectMutation.error) : undefined} onCancel={() => setSubjectFormOpen(false)} onSubmit={(values) => updateSubjectMutation.mutate({ current, values })} />
        </Dialog>
      ) : null}
      <ArchiveConfirmation entity="Subject" name={current.name} open={subjectArchiveOpen} pending={archiveSubjectMutation.isPending} error={archiveSubjectMutation.error ? subjectMutationError(archiveSubjectMutation.error) : undefined} onClose={() => setSubjectArchiveOpen(false)} onConfirm={() => archiveSubjectMutation.mutate(current.id)} />
      {topicForm ? (
        <Dialog open onClose={() => setTopicForm(null)} title={topicForm === 'create' ? 'Create Topic' : 'Edit Topic'}>
          <TopicForm initialValues={topicForm === 'create' ? undefined : { name: topicForm.name, description: topicForm.description ?? '' }} pending={createTopicMutation.isPending || updateTopicMutation.isPending} serverError={activeTopicError ? topicMutationError(activeTopicError) : undefined} onCancel={() => setTopicForm(null)} onSubmit={(values) => topicForm === 'create' ? createTopicMutation.mutate(values) : updateTopicMutation.mutate({ current: topicForm, values })} />
        </Dialog>
      ) : null}
      {topicArchive ? <ArchiveConfirmation entity="Topic" name={topicArchive.name} open pending={archiveTopicMutation.isPending} error={archiveTopicMutation.error ? topicMutationError(archiveTopicMutation.error) : undefined} onClose={() => setTopicArchive(null)} onConfirm={() => archiveTopicMutation.mutate(topicArchive.id)} /> : null}
    </section>
  )
}

function UnavailableSubject() {
  return <ErrorState title="Subject unavailable" description="This Subject could not be found or is not available to you." action={<Link className="organization-link-action" to="/subjects">Back to Subjects</Link>} />
}
