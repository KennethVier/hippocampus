import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import * as api from '../api/learningOrganizationApi'
import type { SubjectPage } from '../api/learningOrganizationContracts'
import { SubjectDetailPage } from './SubjectDetailPage'
import { SubjectsPage } from './SubjectsPage'

const subject = { id: '3f2504e0-4f89-41d3-9a0c-0305e82c3301', name: '<script>Clinical Anatomy</script>', description: 'Upper limb', sortOrder: 9, status: 'ACTIVE' as const, createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z' }
const topic = { id: '9a7b3302-b431-45e1-90e3-298c9d80918f', subjectId: subject.id, name: 'Thorax', description: 'Chest anatomy', status: 'ACTIVE' as const, createdAt: subject.createdAt, updatedAt: subject.updatedAt }

afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderAt(path: string, detail = false) {
  const router = createMemoryRouter([{ path: '/subjects', element: <SubjectsPage /> }, { path: '/subjects/:subjectId', element: detail ? <SubjectDetailPage /> : <p>Opened detail</p> }], { initialEntries: [path] })
  render(<QueryClientProvider client={createAppQueryClient()}><RouterProvider router={router} /></QueryClientProvider>)
  return router
}

describe('Subjects page', () => {
  it('renders loading then an empty state', async () => {
    let resolve: ((value: SubjectPage) => void) | undefined
    vi.spyOn(api, 'listSubjects').mockReturnValue(new Promise((done) => { resolve = done }))
    renderAt('/subjects?page=1')
    expect(screen.getByRole('status', { name: 'Loading Subjects' })).toBeInTheDocument()
    resolve?.({ items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 })
    expect(await screen.findByRole('heading', { name: 'No Subjects yet' })).toBeInTheDocument()
  })

  it('renders hostile-looking names as text, descriptions, pagination, and navigation', async () => {
    vi.spyOn(api, 'listSubjects').mockResolvedValue({ items: [subject], page: 0, size: 12, totalElements: 13, totalPages: 2 })
    const router = renderAt('/subjects?page=abc')
    expect(await screen.findByRole('heading', { name: subject.name })).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    expect(screen.getByText('Upper limb')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?page=1')
    fireEvent.click(screen.getByRole('link', { name: 'Open Subject' }))
    expect(await screen.findByText('Opened detail')).toBeInTheDocument()
  })

  it('validates create and maps a name conflict', async () => {
    vi.spyOn(api, 'listSubjects').mockResolvedValue({ items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 })
    vi.spyOn(api, 'createSubject').mockRejectedValue(new ApiError({ kind: 'http', status: 409, code: 'SUBJECT_NAME_CONFLICT', message: 'raw' }))
    renderAt('/subjects?page=1')
    fireEvent.click(await screen.findByRole('button', { name: 'Create Subject' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save Subject' }))
    expect(await screen.findByText('Enter a subject name.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Anatomy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save Subject' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already have a Subject')
  })

  it('preserves hidden sortOrder during edit and archives only after confirmation', async () => {
    vi.spyOn(api, 'listSubjects').mockResolvedValue({ items: [subject], page: 0, size: 12, totalElements: 1, totalPages: 1 })
    const update = vi.spyOn(api, 'updateSubject').mockResolvedValue({ ...subject, name: 'Anatomy' })
    const archive = vi.spyOn(api, 'archiveSubject').mockResolvedValue({ ...subject, status: 'ARCHIVED' })
    renderAt('/subjects?page=1')
    fireEvent.click(await screen.findByRole('button', { name: 'Edit Subject' }))
    expect(screen.queryByLabelText(/sort/i)).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Anatomy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save Subject' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith(subject.id, expect.objectContaining({ sortOrder: 9 })))
    fireEvent.click(screen.getByRole('button', { name: 'Archive Subject' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('not deleted')
    fireEvent.click(within(screen.getByRole('dialog', { name: 'Archive Subject' })).getByRole('button', { name: 'Archive Subject' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith(subject.id, expect.anything()))
  })
})

describe('Subject detail', () => {
  it('does not request Topics or show Topic controls for an archived Subject', async () => {
    vi.spyOn(api, 'getSubject').mockResolvedValue({ ...subject, status: 'ARCHIVED' })
    const listTopics = vi.spyOn(api, 'listTopics')
    renderAt(`/subjects/${subject.id}`, true)
    expect(await screen.findByRole('heading', { name: subject.name })).toBeInTheDocument()
    expect(screen.getByText('Archived')).toBeInTheDocument()
    expect(listTopics).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Create Topic' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Study Mission/i)).not.toBeInTheDocument()
  })

  it('renders active Topics without linking to a Topic workspace and archives a Topic', async () => {
    vi.spyOn(api, 'getSubject').mockResolvedValue(subject)
    vi.spyOn(api, 'listTopics').mockResolvedValue({ items: [topic], page: 0, size: 12, totalElements: 1, totalPages: 1 })
    const archive = vi.spyOn(api, 'archiveTopic').mockResolvedValue({ ...topic, status: 'ARCHIVED' })
    renderAt(`/subjects/${subject.id}`, true)
    expect(await screen.findByRole('heading', { name: 'Thorax' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Thorax|Open Topic/ })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Archive Topic' }))
    fireEvent.click(within(screen.getByRole('dialog', { name: 'Archive Topic' })).getByRole('button', { name: 'Archive Topic' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith(topic.id, expect.anything()))
  })

  it('renders foreign and missing Subjects through the same unavailable state', async () => {
    vi.spyOn(api, 'getSubject').mockRejectedValue(new ApiError({ kind: 'http', status: 404, code: 'SUBJECT_NOT_FOUND', message: 'Subject was not found.' }))
    renderAt(`/subjects/${subject.id}`, true)
    expect(await screen.findByRole('heading', { name: 'Subject unavailable' })).toBeInTheDocument()
    expect(screen.queryByText(subject.id)).not.toBeInTheDocument()
  })

  it('refetches Subject authority when a Topic mutation reports an unavailable parent', async () => {
    vi.spyOn(api, 'getSubject').mockResolvedValueOnce(subject).mockResolvedValue({ ...subject, status: 'ARCHIVED' })
    vi.spyOn(api, 'listTopics').mockResolvedValue({ items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 })
    vi.spyOn(api, 'createTopic').mockRejectedValue(new ApiError({ kind: 'http', status: 404, code: 'SUBJECT_NOT_FOUND', message: 'hidden' }))
    renderAt(`/subjects/${subject.id}`, true)
    fireEvent.click(await screen.findByRole('button', { name: 'Create Topic' }))
    const dialog = screen.getByRole('dialog', { name: 'Create Topic' })
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Thorax' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save Topic' }))
    expect(await screen.findByRole('heading', { name: 'This Subject is archived' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create Topic' })).not.toBeInTheDocument()
  })
})
