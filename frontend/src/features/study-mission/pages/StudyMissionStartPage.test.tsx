import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/apiClient'
import { createAppQueryClient } from '../../../app/providers/queryClient'
import * as auth from '../../auth/authApi'
import * as api from '../api/studyMissionApi'
import { StudyMissionStartPage } from './StudyMissionStartPage'

const owner='11111111-1111-4111-8111-111111111111'
const topic='22222222-2222-4222-8222-222222222222'
const material='33333333-3333-4333-8333-333333333333'
const mission='44444444-4444-4444-8444-444444444444'
afterEach(()=>{cleanup();vi.restoreAllMocks();sessionStorage.clear()})

function page() {
  vi.spyOn(auth,'getCurrentSession').mockResolvedValue({userId:owner})
  vi.spyOn(api,'eligibleMissionSources').mockResolvedValue({topic:{id:topic,name:'Synthetic topic'},
    items:[{materialId:material,title:'Synthetic material',activeVersionId:material,processingStatus:'PARTIALLY_READY',readiness:'LIMITED'}],
    nextAfterMaterialId:null})
  const client=createAppQueryClient()
  const router=createMemoryRouter([{path:'/topics/:topicId',element:<StudyMissionStartPage/>},
    {path:'/missions/:missionId',element:<h1>Recovered mission</h1>}],{initialEntries:['/topics/'+topic]})
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>)
}
async function fill() {
  await screen.findByRole('heading',{name:'Start a Study Mission'})
  fireEvent.change(screen.getByLabelText('What do you want to understand?'),{target:{value:'Explain the synthetic concept'}})
  fireEvent.click(screen.getByRole('checkbox',{name:'Synthetic material — Partially ready'}))
}

it('uses strict whole-material sources and retains only UUID identity after an ambiguous result',async()=>{
  const start=vi.spyOn(api,'startStudyMission').mockRejectedValue(new ApiError({kind:'network',status:null,code:'NETWORK_ERROR',message:'Offline'}))
  page();await fill();fireEvent.click(screen.getByRole('button',{name:'Start mission'}))
  await screen.findByRole('alert')
  expect(start).toHaveBeenCalledTimes(1)
  expect(start.mock.calls[0]?.[0]).toMatchObject({topicId:topic,groundingMode:'STRICT_SOURCE',
    learningObjectiveText:'Explain the synthetic concept',availableTimeMinutes:20,sourceScopes:[{materialId:material}]})
  expect(screen.queryByText('GENERAL_KNOWLEDGE')).not.toBeInTheDocument()
  expect(sessionStorage.length).toBe(1)
  expect(sessionStorage.getItem(sessionStorage.key(0)??'')).toMatch(/^[0-9a-f-]{36}$/)
  expect(JSON.stringify({...sessionStorage})).not.toContain('Explain the synthetic concept')
  expect(screen.getByLabelText('What do you want to understand?')).toBeDisabled()
})

it('makes no automatic retry and reuses the exact frozen submission on explicit recovery',async()=>{
  const start=vi.spyOn(api,'startStudyMission').mockRejectedValueOnce(new ApiError({kind:'network',status:null,code:'NETWORK_ERROR',message:'Offline'}))
  page();await fill();fireEvent.click(screen.getByRole('button',{name:'Start mission'}))
  await screen.findByRole('alert');expect(start).toHaveBeenCalledTimes(1)
  const original=start.mock.calls[0]?.[0]
  if(!original) throw new Error('Missing submission')
  start.mockResolvedValueOnce({missionId:mission,startRequestId:original.startRequestId,missionStatus:'ACTIVE',
    replayed:true,outcome:'ACTIVITY_CREATED',activityId:material})
  fireEvent.click(screen.getByRole('button',{name:'Recover this mission'}))
  await screen.findByRole('heading',{name:'Recovered mission'})
  expect(start).toHaveBeenCalledTimes(2)
  expect(start.mock.calls[1]?.[0]).toEqual(original)
  expect(sessionStorage.length).toBe(0)
})

it('creates another request identity only after explicit new-mission intent',async()=>{
  const start=vi.spyOn(api,'startStudyMission').mockRejectedValue(new ApiError({kind:'network',status:null,code:'NETWORK_ERROR',message:'Offline'}))
  page();await fill();fireEvent.click(screen.getByRole('button',{name:'Start mission'}));await screen.findByRole('alert')
  const first=start.mock.calls[0]?.[0].startRequestId
  fireEvent.click(screen.getByRole('button',{name:'Start a new mission instead'}))
  fireEvent.click(screen.getByRole('button',{name:'Start mission'}))
  await waitFor(()=>expect(start).toHaveBeenCalledTimes(2))
  expect(start.mock.calls[1]?.[0].startRequestId).not.toBe(first)
})
