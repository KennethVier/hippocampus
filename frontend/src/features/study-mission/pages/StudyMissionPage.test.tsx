import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import * as api from '../api/studyMissionApi'
import type { StudyMission } from '../api/studyMissionContracts'
import { StudyMissionPage } from './StudyMissionPage'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const mission: StudyMission = {
  id: missionId,
  status: 'ACTIVE',
  stage: 'EXPLAIN',
  currentActivity: {
    id: '9a7b3302-b431-45e1-90e3-298c9d80918f',
    type: 'EXPLANATION',
    status: 'PRESENTED',
    difficulty: null,
    classification: null,
    content: { concept: 'Cardiac output', explanation: 'The volume pumped each minute.', keyPoints: ['Rate multiplied by stroke volume'], limitations: [] },
    sources: [],
  },
  availableTimeMinutes: 15,
  startedAt: '2026-10-02T01:00:00Z',
  completedAt: null,
  stoppedAt: null,
  updatedAt: '2026-10-02T01:01:00Z',
}

afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderMission(path = `/missions/${missionId}`) {
  const client = createAppQueryClient()
  const router = createMemoryRouter([{ path: '/missions/:missionId', element: <StudyMissionPage /> }], { initialEntries: [path] })
  render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
}

describe('StudyMissionPage', () => {
  it('shows the loading state', () => {
    vi.spyOn(api, 'getStudyMission').mockReturnValue(new Promise<StudyMission>(() => undefined))
    renderMission()
    expect(screen.getByRole('status', { name: 'Loading Study Mission' })).toBeInTheDocument()
  })

  it('renders mission context and the current activity', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue(mission)
    renderMission()
    expect(await screen.findByRole('heading', { level: 1, name: 'Study Mission' })).toBeInTheDocument()
    expect(screen.getByText('Explain')).toBeInTheDocument()
    expect(screen.getByText('The volume pumped each minute.')).toBeInTheDocument()
  })

  it('renders a legitimate no-current-activity state', async () => {
    vi.spyOn(api, 'getStudyMission').mockResolvedValue({ ...mission, stage: null, currentActivity: null })
    renderMission()
    expect(await screen.findByRole('heading', { name: 'No activity is available right now' })).toBeInTheDocument()
    expect(screen.queryByText('Stage')).not.toBeInTheDocument()
    expect(screen.queryByText('The volume pumped each minute.')).not.toBeInTheDocument()
  })

  it.each([
    new ApiError({ kind: 'http', status: 404, code: 'STUDY_MISSION_NOT_FOUND', message: 'private' }),
    new ApiError({ kind: 'http', status: 404, code: 'HTTP_ERROR', message: 'private' }),
  ])('uses the safe unavailable state for concealed 404 responses', async (error) => {
    vi.spyOn(api, 'getStudyMission').mockRejectedValue(error)
    renderMission()
    expect(await screen.findByRole('heading', { name: 'Study mission unavailable' })).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('private')
    expect(document.body).not.toHaveTextContent(missionId)
  })

  it.each([
    new ApiError({ kind: 'invalid-response', status: 200, code: 'INVALID_RESPONSE', message: 'internal schema detail' }),
    new ApiError({ kind: 'network', status: null, code: 'NETWORK_ERROR', message: 'network internals' }),
  ])('uses the generic error state for malformed or network responses', async (error) => {
    vi.spyOn(api, 'getStudyMission').mockRejectedValue(error)
    renderMission()
    expect(await screen.findByRole('heading', { name: 'Study mission could not be loaded' })).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent(error.message)
  })

  it('does not request an invalid route identifier', () => {
    const request = vi.spyOn(api, 'getStudyMission')
    renderMission('/missions/not-a-uuid')
    expect(screen.getByRole('heading', { name: 'Study mission unavailable' })).toBeInTheDocument()
    expect(request).not.toHaveBeenCalled()
  })
})
