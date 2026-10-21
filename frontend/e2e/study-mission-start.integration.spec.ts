import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { access, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { z } from 'zod'
import { startMissionResultSchema, studyMissionWithInitialSchema } from '../src/features/study-mission/api/studyMissionContracts'

const ready=z.strictObject({schemaVersion:z.literal(1),apiBaseUrl:z.string().url(),frontendBaseUrl:z.literal('http://127.0.0.1:4175'),
  ownerId:z.uuid(),topicId:z.uuid(),materialId:z.uuid(),versionId:z.uuid(),email:z.string(),password:z.string(),syntheticOnly:z.literal(true),
  processedMaterialStatus:z.enum(['READY','PARTIALLY_READY'])}).parse(JSON.parse(readFileSync(process.env.HIPPOCAMPUS_P714_READY_FILE??'','utf8')))
const api=new URL(ready.apiBaseUrl)
if(api.hostname!=='127.0.0.1' || api.protocol!=='http:' || api.username || api.password)throw new Error('Loopback fixture only')
async function signIn(page:Page){
  await page.goto('/topics/'+ready.topicId)
  await page.getByLabel('Email',{exact:true}).fill(ready.email)
  await page.getByLabel('Password',{exact:true}).fill(ready.password)
  await page.getByRole('button',{name:'Sign in',exact:true}).click()
  await expect(page.getByRole('heading',{name:'Start a Study Mission'})).toBeVisible()
}
async function csrf(page:Page){const response=await page.request.get(api.origin+'/api/auth/csrf');expect(response.ok()).toBe(true);return z.object({token:z.string()}).parse(await response.json()).token}
async function readMission(page:Page,id:string){const response=await page.request.get(api.origin+'/api/study-missions/'+id+'?continuationContract=1&initialActivityContract=1');expect(response.ok()).toBe(true);return studyMissionWithInitialSchema.parse(await response.json())}

test('real processed-material mission starts once, replays durably and preserves activity through lifecycle reads',async({page})=>{
  await signIn(page)
  const posted=page.waitForRequest(request=>request.method()==='POST' && request.url()===api.origin+'/api/study-missions')
  await page.getByLabel('What do you want to understand?').fill('Explain the synthetic concept')
  await page.getByRole('checkbox',{name:/Synthetic concept.pdf/}).check()
  await page.getByRole('button',{name:'Start mission',exact:true}).click()
  const request=await posted
  await expect(page).toHaveURL(/\/missions\/[0-9a-f-]{36}$/)
  const id=page.url().split('/').at(-1)??''
  const before=await readMission(page,id)
  expect(before.status).toBe('ACTIVE');expect(before.currentActivity).not.toBeNull()
  expect(before.currentActivity?.type).toBe('EXPLANATION');expect(before.currentActivity?.sources.length).toBeGreaterThan(0)
  expect(before.initialActivity?.state).toBe('ACTIVITY_CREATED')
  const replay=await page.request.post(api.origin+'/api/study-missions',{headers:{'X-CSRF-TOKEN':await csrf(page)},data:request.postDataJSON()})
  expect(replay.ok()).toBe(true)
  expect(startMissionResultSchema.parse(await replay.json())).toMatchObject({missionId:id,replayed:true,outcome:'ACTIVITY_CREATED',activityId:before.currentActivity?.id})
  await page.reload();expect((await readMission(page,id)).currentActivity?.id).toBe(before.currentActivity?.id)
  await page.getByRole('button',{name:'Pause',exact:true}).click()
  await expect(page.getByRole('button',{name:'Resume',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Resume',exact:true}).click()
  await expect(page.getByRole('button',{name:'Pause',exact:true})).toBeVisible()
  expect((await readMission(page,id)).currentActivity?.id).toBe(before.currentActivity?.id)
  await page.getByRole('button',{name:'Stop mission',exact:true}).click()
  await expect(page.getByRole('button',{name:'Pause',exact:true})).toHaveCount(0)
  expect((await readMission(page,id)).status).toBe('STOPPED')
})

test('real one-minute empty COMPLETE remains nonterminal, read-only and non-scoring',async({page})=>{
  await signIn(page)
  const key=randomUUID()
  const response=await page.request.post(api.origin+'/api/study-missions',{headers:{'X-CSRF-TOKEN':await csrf(page)},data:{
    startRequestId:key,topicId:ready.topicId,learningObjectiveText:'Explain synthetic concept',availableTimeMinutes:1,
    groundingMode:'STRICT_SOURCE',sourceScopes:[{materialId:ready.materialId}]}})
  expect(response.ok()).toBe(true)
  const result=startMissionResultSchema.parse(await response.json())
  expect(result).toMatchObject({outcome:'NO_ACTIVITY',missionStatus:'ACTIVE',reasonCode:'INITIAL_COMPLETION_NOT_ADMISSIBLE',canRetry:false})
  const before=await readMission(page,result.missionId)
  expect(before.currentActivity).toBeNull();expect(before.completedAt).toBeNull();expect(before.initialActivity?.attemptsUsed).toBe(0)
  await page.goto('/missions/'+result.missionId)
  await expect(page.getByText('No learning activity was completed. This mission remains active. You can pause or stop it.')).toBeVisible()
  await page.getByRole('button',{name:'Refresh mission state'}).click()
  expect(await readMission(page,result.missionId)).toEqual(before)
  await expect(page.getByRole('button',{name:'Retry initial activity'})).toHaveCount(0)
  await page.getByRole('button',{name:'Stop mission',exact:true}).click()
  await expect.poll(async()=>(await readMission(page,result.missionId)).status).toBe('STOPPED')
})


test('concurrent identical authenticated starts bind one mission and one accepted initial activity',async({page})=>{
  await signIn(page)
  const token=await csrf(page)
  const body={startRequestId:randomUUID(),topicId:ready.topicId,learningObjectiveText:'Explain synthetic concept',availableTimeMinutes:20,
    groundingMode:'STRICT_SOURCE',sourceScopes:[{materialId:ready.materialId}]}
  const responses=await Promise.all([1,2].map(()=>page.request.post(api.origin+'/api/study-missions',{headers:{'X-CSRF-TOKEN':token},data:body})))
  for(const response of responses)expect(response.ok()).toBe(true)
  const results=await Promise.all(responses.map(async response=>startMissionResultSchema.parse(await response.json())))
  expect(results[0]?.missionId).toBe(results[1]?.missionId)
  const id=results[0]?.missionId??''
  await expect.poll(async()=>(await readMission(page,id)).initialActivity?.state).toBe('ACTIVITY_CREATED')
  const saved=await readMission(page,id)
  expect(saved.initialActivity?.attemptsUsed).toBe(1)
  expect(saved.currentActivity).not.toBeNull()
  const replay=await page.request.post(api.origin+'/api/study-missions',{headers:{'X-CSRF-TOKEN':await csrf(page)},data:body})
  expect(startMissionResultSchema.parse(await replay.json())).toMatchObject({missionId:id,replayed:true,outcome:'ACTIVITY_CREATED',activityId:saved.currentActivity?.id})
})

test('Pause during a held fake provider fences the stale worker and Resume does not generate',async({page})=>{
  await signIn(page)
  const control=randomUUID(),directory=dirname(process.env.HIPPOCAMPUS_P714_READY_FILE??'')
  const entered=join(directory,'hold-'+control+'.entered'),release=join(directory,'hold-'+control+'.release')
  const body={startRequestId:randomUUID(),topicId:ready.topicId,learningObjectiveText:'P714-HOLD:'+control,availableTimeMinutes:20,
    groundingMode:'STRICT_SOURCE',sourceScopes:[{materialId:ready.materialId}]}
  const headers={'X-CSRF-TOKEN':await csrf(page)}
  const pending=page.request.post(api.origin+'/api/study-missions',{headers,data:body})
  try {
    await expect.poll(async()=>{try{await access(entered);return true}catch{return false}}).toBe(true)
    const replay=await page.request.post(api.origin+'/api/study-missions',{headers,data:body})
    const recovering=startMissionResultSchema.parse(await replay.json())
    expect(recovering.outcome).toBe('PREPARING')
    const paused=await page.request.post(api.origin+'/api/study-missions/'+recovering.missionId+'/pause',{headers})
    expect(paused.ok()).toBe(true)
    await writeFile(release,'release-synthetic-provider',{flag:'wx'})
    const finished=await pending;expect(finished.ok()).toBe(true)
    const result=startMissionResultSchema.parse(await finished.json())
    expect(result.missionId).toBe(recovering.missionId)
    const saved=await readMission(page,recovering.missionId)
    expect(saved.status).toBe('PAUSED');expect(saved.currentActivity).toBeNull()
    expect(saved.initialActivity?.state).not.toBe('ACTIVITY_CREATED')
    const resumed=await page.request.post(api.origin+'/api/study-missions/'+recovering.missionId+'/resume',{headers:{'X-CSRF-TOKEN':await csrf(page)}})
    expect(resumed.ok()).toBe(true)
    const after=await readMission(page,recovering.missionId)
    expect(after.status).toBe('ACTIVE');expect(after.currentActivity).toBeNull();expect(after.initialActivity?.attemptsUsed).toBe(1)
    const stopped=await page.request.post(api.origin+'/api/study-missions/'+recovering.missionId+'/stop',{headers:{'X-CSRF-TOKEN':await csrf(page)}})
    expect(stopped.ok()).toBe(true)
  } finally {await writeFile(release,'release-synthetic-provider').catch(()=>undefined);await pending.catch(()=>undefined)}
})
