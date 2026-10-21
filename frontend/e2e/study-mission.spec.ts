import { expect, test, type Page } from '@playwright/test'
import { z } from 'zod'
import { activitySubmissionSchema, type ActivitySubmission } from '../src/features/study-mission/api/studyMissionContracts'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'

test('student opens a Study Mission and inspects learner-safe sources', async ({ page }, testInfo) => {
  await mockSession(page)
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: missionId,
        status: 'ACTIVE',
        stage: 'EXPLAIN',
        currentActivity: {
          id: '9a7b3302-b431-45e1-90e3-298c9d80918f',
          type: 'EXPLANATION',
          status: 'PRESENTED',
          difficulty: null,
          classification: 'SUPPLEMENTAL_GENERATED',
          content: {
            concept: 'Cardiac output',
            explanation: 'Cardiac output is the volume of blood pumped each minute.',
            keyPoints: ['Heart rate and stroke volume determine cardiac output.'],
            limitations: [],
          },
          sources: [{
            sourceReferenceId: '11111111-1111-4111-8111-111111111111',
            materialTitle: 'Cardiovascular Physiology',
            pageNumber: 14,
            displayLabel: 'Cardiac cycle',
          }],
        },
        availableTimeMinutes: 20,
        startedAt: '2026-10-02T01:00:00Z',
        completedAt: null,
        stoppedAt: null,
        updatedAt: '2026-10-02T01:01:00Z',
        initialActivity: null, continuation: null,
      }),
    })
  })

  await page.goto(`/missions/${missionId}`)

  await expect(page.getByRole('heading', { level: 1, name: 'Study Mission' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Explanation' })).toBeVisible()
  await expect(page.getByText('Additional medical context')).toBeVisible()

  if (testInfo.project.name === 'mobile-chromium') {
    await expect(page.getByRole('button', { name: 'View sources' })).toBeVisible()
    await expect(page.getByText('Cardiovascular Physiology')).not.toBeVisible()
    await page.getByRole('button', { name: 'View sources' }).click()
    const sources = page.getByRole('dialog', { name: 'Sources' })
    await expect(sources).toBeVisible()
    await expect(sources.getByText('Cardiovascular Physiology')).toBeVisible()
    await sources.getByRole('button', { name: 'Close sources' }).click()
    await expect(sources).toHaveCount(0)
  } else {
    const sources = page.getByRole('complementary', { name: 'Sources' })
    await expect(sources).toBeVisible()
    await expect(sources.getByText('Cardiovascular Physiology')).toBeVisible()
    await sources.getByRole('button', { name: 'Hide sources' }).click()
    await expect(sources.getByText('Cardiovascular Physiology')).toHaveCount(0)
    await expect(sources.getByRole('button', { name: 'Show sources' })).toHaveAttribute('aria-expanded', 'false')
    await sources.getByRole('button', { name: 'Show sources' }).click()
    await expect(sources.getByText('Cardiovascular Physiology')).toBeVisible()
  }

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
})

test('conceals a missing Study Mission', async ({ page }) => {
  await mockSession(page)
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 404, code: 'STUDY_MISSION_NOT_FOUND', message: 'Private ownership detail' }),
    })
  })

  await page.goto(`/missions/${missionId}`)
  await expect(page.getByRole('heading', { name: 'Study mission unavailable' })).toBeVisible()
  await expect(page.getByText('Private ownership detail')).toHaveCount(0)
})

test('a stale tab refetches authoritative mission state without resubmitting', async ({ browser }) => {
  const context = await browser.newContext()
  let completed = false
  let responseWrites = 0
  await mockContextSession(context)
  await context.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(missionPresentation(completed ? 'COMPLETED' : 'PRESENTED')) })
  })
  await context.route(`**/api/study-missions/${missionId}/activities/*/responses`, async (route) => {
    responseWrites += 1
    if (completed) {
      await route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ status: 409, code: 'MISSION_CONFLICT', message: 'stale version' }) })
      return
    }
    completed = true
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      missionId, activityId: '9a7b3302-b431-45e1-90e3-298c9d80918f', outcome: 'CORRECT',
      correctConcepts: ['Cardiac output'], missingConcepts: [], misconceptions: [], feedback: 'Correct.',
      missionStatus: 'ACTIVE', stage: 'RETRIEVE', updatedAt: '2026-10-02T01:02:00Z', continuationAvailable: true,
    }) })
  })

  const tabA = await context.newPage()
  const tabB = await context.newPage()
  await Promise.all([tabA.goto(`/missions/${missionId}`), tabB.goto(`/missions/${missionId}`)])
  await Promise.all([
    tabA.getByRole('radio', { name: 'Increased stroke volume' }).click(),
    tabB.getByRole('radio', { name: 'Increased stroke volume' }).click(),
  ])
  await tabA.getByRole('button', { name: 'Submit response' }).click()
  await expect(tabA.getByText('Correct.')).toBeVisible()

  await tabB.getByRole('button', { name: 'Submit response' }).click()
  await expect(tabB.getByText('This mission changed in another tab. The latest state has been loaded.')).toBeVisible()
  await expect(tabB.getByRole('button', { name: 'Continue' })).toBeVisible()
  await expect(tabB.getByRole('button', { name: 'Submit response' })).toHaveCount(0)
  expect(responseWrites).toBe(2)
  await context.close()
})

test('completed retrieval continues to a response-bearing connection submitted once', async ({ page }) => {
  let activity: 'RETRIEVAL' | 'CONNECTION' = 'RETRIEVAL'
  let connectionWrites = 0
  let savedSubmission: ActivitySubmission | null = null
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }) }))
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(activity === 'RETRIEVAL'
        ? missionPresentation('COMPLETED')
        : { ...connectionMissionPresentation(savedSubmission ? 'COMPLETED' : 'PRESENTED'), latestSubmission: savedSubmission }),
    })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/continue?continuationContract=1`, async (route) => {
    activity = 'CONNECTION'
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ outcome: 'ACTIVITY_CREATED', activityId: '8b8b3302-b431-45e1-90e3-298c9d80918f' }) })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/responses`, async (route) => {
    connectionWrites += 1
    const payload = route.request().postDataJSON()
    expect(payload).toEqual({
      responseText: 'Preload stretches the ventricle and increases stroke volume.',
      requestId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    })
    expect(z.uuid().safeParse(payload.requestId).success).toBe(true)
    savedSubmission = activitySubmissionSchema.parse({
      missionId, activityId: '8b8b3302-b431-45e1-90e3-298c9d80918f', outcome: 'UNGRADED',
      correctConcepts: [], missingConcepts: [], misconceptions: [],
      feedback: 'Your response was saved, but it could not be graded reliably. You can continue safely.',
      missionStatus: 'ACTIVE', stage: 'CONNECTION',
      attemptId: 'f1715015-1111-4111-8111-111111111111', limitationCode: 'EVALUATION_UNRESOLVED',
      updatedAt: '2026-10-03T01:02:00Z', continuationAvailable: true,
    })
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(savedSubmission) })
  })

  await page.goto(`/missions/${missionId}`)
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByText('Explain how preload influences stroke volume.')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Response' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Continue' })).toHaveCount(0)

  await page.getByRole('textbox', { name: 'Response' }).fill('Preload stretches the ventricle and increases stroke volume.')
  await page.getByRole('button', { name: 'Submit response' }).click()
  await expect(page.getByText('Your response was saved, but it could not be graded reliably. You can continue safely.')).toBeVisible()
  await expect(page.getByText('Response saved \u00b7 ungraded', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Correct concepts', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Concepts to revisit', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Misconceptions', exact: true })).toHaveCount(0)
  expect(connectionWrites).toBe(1)
})

test('pause survives refresh and resume restores authoritative ACTIVE state', async ({ page }) => {
  let status = 'ACTIVE'
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }) }))
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...missionPresentation('PRESENTED'), status }) })
  })
  await page.route(`**/api/study-missions/${missionId}/pause`, async (route) => {
    status = 'PAUSED'
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(lifecyclePresentation(status)) })
  })
  await page.route(`**/api/study-missions/${missionId}/resume`, async (route) => {
    status = 'ACTIVE'
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(lifecyclePresentation(status)) })
  })

  await page.goto(`/missions/${missionId}`)
  await page.getByRole('button', { name: 'Pause' }).click()
  await expect(page.getByRole('button', { name: 'Resume' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Resume' })).toBeVisible()
  await page.getByRole('button', { name: 'Resume' }).click()
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
})

test('assessment limitation survives refresh and learner-controlled Pause and resume without another assessment', async ({ page }) => {
  let status = 'ACTIVE'
  let unexpectedWrites = 0
  const base = missionPresentation('COMPLETED')
  const receipt = {
    missionId, activityId: base.currentActivity.id, outcome: 'UNGRADED', correctConcepts: [],
    missingConcepts: [], misconceptions: [], feedback: 'Synthetic response saved without a grade.',
    limitationCode: 'EVALUATION_UNRESOLVED', missionStatus: 'ACTIVE', stage: 'RETRIEVAL',
    updatedAt: base.updatedAt, continuationAvailable: true,
  }
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }),
  }))
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      ...base, status, latestSubmission: receipt,
      currentActivity: { ...base.currentActivity, content: { ...base.currentActivity.content,
        subtype: 'SHORT_ANSWER', options: [], question: 'Synthetic free-text practice question',
      } },
      initialActivity: null, continuation: status === 'ACTIVE'
        ? { actionType: 'COMMUNICATE_LIMITATION', rationaleCode: 'ASSESSMENT_UNAVAILABLE', canMaterialize: false } : null,
    }) })
  })
  for (const action of ['pause', 'resume']) {
    await page.route(`**/api/study-missions/${missionId}/${action}`, async (route) => {
      status = action === 'pause' ? 'PAUSED' : 'ACTIVE'
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(lifecyclePresentation(status)) })
    })
  }
  await page.route(`**/api/study-missions/${missionId}/activities/**`, async (route) => {
    unexpectedWrites += 1
    await route.fulfill({ status: 500 })
  })
  await page.goto(`/missions/${missionId}`)
  await expect(page.getByText(/Automatic assessment is unavailable/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Continue' })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText(receipt.feedback)).toBeVisible()
  await page.getByRole('button', { name: 'Pause' }).click()
  await expect(page.getByRole('button', { name: 'Resume' })).toBeVisible()
  await page.reload()
  await page.getByRole('button', { name: 'Resume' }).click()
  await expect(page.getByText(/Automatic assessment is unavailable/)).toBeVisible()
  await expect(page.getByText(receipt.feedback)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Submit response' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Continue' })).toHaveCount(0)
  expect(unexpectedWrites).toBe(0)
})

async function mockSession(page: Page) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: '22222222-2222-4222-8222-222222222222' }),
    })
  })
}

for (const unknownOutcome of ['network', 'invalid-response', 500, 502, 504] as const) {
  test(`ambiguous ${unknownOutcome} Continue blocks writes until manual state recovery`, async ({ page }) => {
    await mockContextSession(page.context())
    let reads = 0
    let continues = 0
    let otherWrites = 0
    await page.route(`**/api/study-missions/${missionId}/**`, async (route) => {
      if (route.request().method() === 'POST') otherWrites += 1
      await route.fulfill({ status: 500, body: '' })
    })
    await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
      reads += 1
      if (reads === 2) {
        await route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{}' })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify(reads === 1 ? missionPresentation('COMPLETED') : connectionMissionPresentation('PRESENTED')) })
    })
    await page.route(`**/api/study-missions/${missionId}/activities/*/continue?continuationContract=1`, async (route) => {
      continues += 1
      if (typeof unknownOutcome === 'number') await route.fulfill({ status: unknownOutcome, body: '' })
      else if (unknownOutcome === 'network') await route.abort('failed')
      else await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    })
    await page.goto(`/missions/${missionId}`)
    await page.getByRole('button', { name: 'Continue' }).click()
    await expect(page.getByText('The action result could not be confirmed, and the latest mission state could not be loaded. Reload the mission state before trying again.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Continue' })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Pause' })).toBeDisabled()
    await expect(page.getByText(/changed in another tab/)).toHaveCount(0)
    expect(continues).toBe(1)
    await page.getByRole('button', { name: 'Reload mission state' }).click()
    await expect(page.getByRole('textbox', { name: 'Response' })).toBeEnabled()
    await expect(page.getByRole('button', { name: 'Pause' })).toBeEnabled()
    await expect(page.getByText('The latest mission state has been loaded. Check it before continuing.')).toBeVisible()
    await expect(page.getByText(/changed in another tab/)).toHaveCount(0)
    expect(continues).toBe(1)
    expect(otherWrites).toBe(0)
    expect(reads).toBe(3)
  })
}

for (const status of [502, 504]) {
  test(`HTTP ${status} after backend commit recovers the new activity without resending Continue`, async ({ page }) => {
    await mockContextSession(page.context())
    let committed = false
    let writes = 0
    let reads = 0
    await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
      reads += 1
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify(committed ? connectionMissionPresentation('PRESENTED') : missionPresentation('COMPLETED')) })
    })
    await page.route(`**/api/study-missions/${missionId}/activities/*/continue?continuationContract=1`, async (route) => {
      writes += 1
      committed = true
      await route.fulfill({ status, contentType: 'application/problem+json', body: '{}' })
    })
    await page.goto(`/missions/${missionId}`)
    await page.getByRole('button', { name: 'Continue' }).click()
    await expect(page.getByRole('textbox', { name: 'Response' })).toBeEnabled()
    await expect(page.getByText('The latest mission state has been loaded. Check it before continuing.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Continue' })).toHaveCount(0)
    expect(writes).toBe(1)
    expect(reads).toBe(2)
  })
}

async function mockContextSession(context: import('@playwright/test').BrowserContext) {
  await context.route('**/api/auth/me', async (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ userId: '22222222-2222-4222-8222-222222222222' }),
  }))
  await context.route('**/api/auth/csrf', async (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }),
  }))
}

function missionPresentation(activityStatus: string) {
  return {
    id: missionId, status: 'ACTIVE', stage: 'RETRIEVE',
    initialActivity: null, continuation: activityStatus === 'COMPLETED' ? { actionType: 'CONNECT', rationaleCode: 'CONNECTION_READY', canMaterialize: true } : null,
    currentActivity: {
      id: '9a7b3302-b431-45e1-90e3-298c9d80918f', type: 'RETRIEVAL', status: activityStatus,
      difficulty: 'STANDARD', classification: null,
      content: { subtype: 'MCQ', concept: 'Cardiac output', question: 'Which factor increases cardiac output?',
        options: [{ id: 'option-a', text: 'Reduced heart rate' }, { id: 'option-b', text: 'Increased stroke volume' }], difficulty: 'STANDARD', limitations: [] },
      sources: [],
    },
    availableTimeMinutes: 20, startedAt: '2026-10-02T01:00:00Z', completedAt: null, stoppedAt: null, updatedAt: '2026-10-02T01:01:00Z',
  }
}

function connectionMissionPresentation(activityStatus: string) {
  return {
    id: missionId, status: 'ACTIVE', stage: 'CONNECTION',
    initialActivity: null, continuation: null,
    currentActivity: {
      id: '8b8b3302-b431-45e1-90e3-298c9d80918f', type: 'CONNECTION', status: activityStatus,
      difficulty: 'STANDARD', classification: null,
      content: {
        fromConcept: 'Preload', toConcept: 'Stroke volume', relationshipType: 'DIRECTLY_INFLUENCES',
        relationship: 'Greater filling can increase ejection.', whyItMatters: 'This connects venous return to cardiac output.',
        question: 'Explain how preload influences stroke volume.', limitations: [],
      },
      sources: [],
    },
    availableTimeMinutes: 20, startedAt: '2026-10-02T01:00:00Z', completedAt: null, stoppedAt: null, updatedAt: '2026-10-03T01:01:00Z',
  }
}

function lifecyclePresentation(status: string) {
  return {
    id: missionId, status, currentActivityId: '9a7b3302-b431-45e1-90e3-298c9d80918f', sourceScopes: [],
    startedAt: '2026-10-02T01:00:00Z', completedAt: null, stoppedAt: null, updatedAt: '2026-10-02T01:02:00Z',
  }
}


test('P7-15 reflection alternative continues without submitting synthetic evidence', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }),
  }))
  let completed = false
  let submissions = 0
  let continuations = 0
  let completedReads = 0
  await page.route(`**/api/study-missions/${missionId}?continuationContract=1&initialActivityContract=1`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    if (completed) completedReads += 1
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      id: missionId, initialActivity: null, continuation: null, status: completed ? 'COMPLETED' : 'ACTIVE', stage: completed ? 'EVIDENCE_UPDATE' : 'REFLECTION',
      currentActivity: { id: 'a1715015-1111-4111-8111-111111111111', type: 'REFLECTION', status: completed ? 'COMPLETED' : 'PENDING',
        difficulty: null, classification: null, content: null, sources: [] },
      availableTimeMinutes: 20, startedAt: '2026-10-18T00:00:00Z',
      completedAt: completed ? '2026-10-18T00:01:00Z' : null, stoppedAt: null, updatedAt: '2026-10-18T00:01:00Z',
    }) })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/responses`, async (route) => {
    submissions += 1
    await route.fulfill({ status: 409, body: '{}' })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/continue?continuationContract=1`, async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-csrf-token']).toBe('test-token')
    continuations += 1
    completed = true
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      outcome: 'NO_ACTIVITY', actionType: 'COMPLETE', rationaleCode: 'OBJECTIVE_COMPLETE',
    }) })
  })
  await page.goto(`/missions/${missionId}`)
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeEnabled()
  await expect(page.getByRole('textbox', { name: 'Response' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()
  expect(continuations).toBe(1)
  expect(completedReads).toBeGreaterThan(0)
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: 'Response' })).toHaveCount(0)
  expect(submissions).toBe(0)
})
