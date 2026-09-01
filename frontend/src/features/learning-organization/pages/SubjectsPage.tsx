import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'

import { ApiError } from '../../../api/apiClient'
import { Button, Dialog, EmptyState, ErrorState, Skeleton } from '../../../components/ui'
import { archiveSubject, createSubject, listSubjects, updateSubject } from '../api/learningOrganizationApi'
import type { Subject } from '../api/learningOrganizationContracts'
import { ArchiveConfirmation } from '../components/ArchiveConfirmation'
import { OrganizationCard } from '../components/OrganizationCard'
import { OrganizationPagination } from '../components/OrganizationPagination'
import { OrganizationForm } from '../forms/OrganizationForm'
import type { SubjectFormValues } from '../forms/subjectFormSchema'
import { organizationKeys } from '../queries/learningOrganizationQueries'
import '../learningOrganization.css'

const PAGE_SIZE = 12

function pageFrom(value: string | null): number {
  return value !== null && /^\d+$/.test(value) && Number(value) >= 1 ? Number(value) : 1
}

function safeMutationError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'SUBJECT_NAME_CONFLICT') return 'You already have a Subject with this name.'
  if (error instanceof ApiError && error.code === 'SUBJECT_NOT_FOUND') return 'This Subject is no longer available.'
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') return 'Review the form and try again.'
  return 'We could not save this change. Try again.'
}

export function SubjectsPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const rawPage = params.get('page')
  const page = pageFrom(rawPage)
  const [formMode, setFormMode] = useState<'create' | Subject | null>(null)
  const [archiveTarget, setArchiveTarget] = useState<Subject | null>(null)
  const subjects = useQuery({ queryKey: organizationKeys.subjects(page - 1, PAGE_SIZE), queryFn: ({ signal }) => listSubjects(page - 1, PAGE_SIZE, signal) })

  useEffect(() => {
    if (rawPage !== String(page)) setParams({ page: String(page) }, { replace: true })
  }, [page, rawPage, setParams])

  useEffect(() => {
    if (subjects.data && subjects.data.totalPages > 0 && page > subjects.data.totalPages) setParams({ page: String(subjects.data.totalPages) }, { replace: true })
  }, [page, setParams, subjects.data])

  const refreshLists = () => queryClient.invalidateQueries({ queryKey: organizationKeys.subjectLists })
  const createMutation = useMutation({ mutationFn: (values: SubjectFormValues) => createSubject({ name: values.name, description: values.description.trim() || null }), onSuccess: async () => { setFormMode(null); await refreshLists() } })
  const updateMutation = useMutation({ mutationFn: ({ subject, values }: { subject: Subject; values: SubjectFormValues }) => updateSubject(subject.id, { name: values.name, description: values.description.trim() || null, sortOrder: subject.sortOrder }), onSuccess: async (subject) => { queryClient.setQueryData(organizationKeys.subject(subject.id), subject); setFormMode(null); await refreshLists() } })
  const archiveMutation = useMutation({ mutationFn: archiveSubject, onSuccess: async () => { setArchiveTarget(null); await refreshLists() } })

  function setPage(next: number) { setParams({ page: String(next) }) }
  const mutationError = createMutation.error ?? updateMutation.error

  return (
    <section className="organization-page" aria-labelledby="subjects-title">
      <div className="organization-page-header">
        <div><p className="organization-eyebrow">Learning organization</p><h1 id="subjects-title">Subjects</h1><p>Organize the areas of medicine you are studying.</p></div>
        <Button onClick={() => { createMutation.reset(); setFormMode('create') }}>Create Subject</Button>
      </div>
      {subjects.isPending ? <div className="organization-grid" aria-label="Loading Subjects"><Skeleton label="Loading Subjects" /><Skeleton /><Skeleton /></div> : null}
      {subjects.isError ? <ErrorState title="Subjects could not be loaded" description="Try again when you are ready." action={<Button onClick={() => void subjects.refetch()}>Try again</Button>} /> : null}
      {subjects.data?.items.length === 0 ? <EmptyState title="No Subjects yet" description="Create a Subject to begin organizing what you are studying." action={<Button onClick={() => setFormMode('create')}>Create Subject</Button>} /> : null}
      {subjects.data?.items.length ? <div className="organization-grid">{subjects.data.items.map((subject) => <OrganizationCard key={subject.id} kind="Subject" name={subject.name} description={subject.description} detailPath={`/subjects/${subject.id}`} onEdit={() => { updateMutation.reset(); setFormMode(subject) }} onArchive={() => { archiveMutation.reset(); setArchiveTarget(subject) }} />)}</div> : null}
      {subjects.data ? <OrganizationPagination page={page} totalPages={subjects.data.totalPages} onPage={setPage} /> : null}
      <Dialog open={formMode !== null} onClose={() => setFormMode(null)} title={formMode === 'create' ? 'Create Subject' : 'Edit Subject'}>
        {formMode ? <OrganizationForm entity="Subject" initialValues={formMode === 'create' ? undefined : { name: formMode.name, description: formMode.description ?? '' }} pending={createMutation.isPending || updateMutation.isPending} serverError={mutationError ? safeMutationError(mutationError) : undefined} onCancel={() => setFormMode(null)} onSubmit={(values) => formMode === 'create' ? createMutation.mutate(values) : updateMutation.mutate({ subject: formMode, values })} /> : null}
      </Dialog>
      {archiveTarget ? <ArchiveConfirmation entity="Subject" name={archiveTarget.name} open pending={archiveMutation.isPending} error={archiveMutation.error ? safeMutationError(archiveMutation.error) : undefined} onClose={() => setArchiveTarget(null)} onConfirm={() => archiveMutation.mutate(archiveTarget.id)} /> : null}
    </section>
  )
}
