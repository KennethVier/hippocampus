import { expect, test, type Page } from '@playwright/test'

const owner='11111111-1111-4111-8111-111111111111'
const topic='22222222-2222-4222-8222-222222222222'
const material='33333333-3333-4333-8333-333333333333'
const mission='44444444-4444-4444-8444-444444444444'
async function setup(page: Page) {
  await page.route('**/api/auth/me',route=>route.fulfill({json:{userId:owner}}))
  await page.route('**/api/auth/csrf',route=>route.fulfill({json:{token:'synthetic-csrf'}}))
  await page.route('**/api/topics/*/study-mission-sources?*',route=>route.fulfill({json:{
    topic:{id:topic,name:'Synthetic topic'},items:[{materialId:material,title:'Synthetic material',activeVersionId:material,
      processingStatus:'READY',readiness:'READY'}],nextAfterMaterialId:null}}))
}
function emptyMission(startRequestId: string) { return {id:mission,status:'ACTIVE',stage:'UNDERSTANDING',currentActivity:null,
  availableTimeMinutes:1,startedAt:'2026-10-20T00:00:00Z',completedAt:null,stoppedAt:null,updatedAt:'2026-10-20T00:00:00Z',continuation:null,
  initialActivity:{startRequestId,state:'NO_ACTIVITY',attemptsUsed:0,maximumAttempts:3,canRetry:false,retryAfterSeconds:null,
    reasonCode:'INITIAL_COMPLETION_NOT_ADMISSIBLE',decision:{actionType:'COMPLETE',rationaleCode:'TIME_LIMIT'}}} }

test('ambiguous start requires explicit same-key recovery and preserves STRICT_SOURCE whole-material contract',async({page})=>{
  await setup(page)
  const requests: Record<string,unknown>[]=[]
  await page.route('**/api/study-missions',async route=>{
    const input=route.request().postDataJSON() as Record<string,unknown>;requests.push(input)
    if(requests.length===1){await route.abort('failed');return}
    await route.fulfill({json:{missionId:mission,startRequestId:input.startRequestId,missionStatus:'ACTIVE',replayed:true,
      outcome:'NO_ACTIVITY',actionType:'COMPLETE',rationaleCode:'TIME_LIMIT',reasonCode:'INITIAL_COMPLETION_NOT_ADMISSIBLE',canRetry:false,retryAfterSeconds:null}})
  })
  await page.route('**/api/study-missions/*?*',route=>route.fulfill({json:emptyMission(String(requests[0]?.startRequestId))}))
  await page.goto('/topics/'+topic)
  await page.getByLabel('What do you want to understand?').fill('Explain synthetic concept')
  await page.getByRole('checkbox',{name:'Synthetic material'}).check()
  await page.getByRole('button',{name:'Start mission',exact:true}).click()
  await expect(page.getByRole('alert')).toContainText('could not be confirmed')
  expect(requests).toHaveLength(1)
  expect(requests[0]).toMatchObject({groundingMode:'STRICT_SOURCE',sourceScopes:[{materialId:material}]})
  await expect(page.getByLabel('What do you want to understand?')).toBeDisabled()
  await page.getByRole('button',{name:'Recover this mission'}).click()
  await expect(page).toHaveURL('/missions/'+mission)
  expect(requests).toHaveLength(2);expect(requests[1]).toEqual(requests[0])
  await expect(page.getByText('No learning activity was completed. This mission remains active. You can pause or stop it.')).toBeVisible()
  await expect(page.getByRole('button',{name:'Retry initial activity'})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'Pause',exact:true})).toBeVisible()
  await expect(page.getByRole('button',{name:'Stop mission'})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth)).toBe(true)
})

test('recovery GET and manual refresh never initiate generation for inadmissible empty completion',async({page})=>{
  await setup(page);let reads=0,writes=0
  await page.route('**/api/study-missions/**',async route=>{
    if(route.request().method()!=='GET'){writes++;await route.abort();return}
    reads++;await route.fulfill({json:emptyMission(material)})
  })
  await page.goto('/missions/'+mission)
  await expect(page.getByRole('heading',{name:'No activity is available right now'})).toBeVisible()
  await page.getByRole('button',{name:'Refresh mission state'}).click()
  await expect.poll(()=>reads).toBeGreaterThan(1)
  expect(writes).toBe(0)
})
