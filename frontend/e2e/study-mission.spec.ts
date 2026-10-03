import { expect, test, type Page } from '@playwright/test'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'

test('student opens a Study Mission and inspects learner-safe sources', async ({ page }, testInfo) => {
  await mockSession(page)
  await page.route(`**/api/study-missions/${missionId}`, async (route) => {
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
  await page.route(`**/api/study-missions/${missionId}`, async (route) => {
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
  await context.route(`**/api/study-missions/${missionId}`, async (route) => {
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
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }) }))
  await page.route(`**/api/study-missions/${missionId}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(activity === 'RETRIEVAL'
        ? missionPresentation('COMPLETED')
        : connectionMissionPresentation('PRESENTED')),
    })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/continue`, async (route) => {
    activity = 'CONNECTION'
    await route.fulfill({ status: 204 })
  })
  await page.route(`**/api/study-missions/${missionId}/activities/*/responses`, async (route) => {
    connectionWrites += 1
    expect(route.request().postDataJSON()).toEqual({ responseText: 'Preload stretches the ventricle and increases stroke volume.' })
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      missionId, activityId: '8b8b3302-b431-45e1-90e3-298c9d80918f', outcome: 'CORRECT',
      correctConcepts: ['Preload', 'Stroke volume'], missingConcepts: [], misconceptions: [],
      feedback: 'You explained the relationship.', missionStatus: 'ACTIVE', stage: 'CONNECTION',
      updatedAt: '2026-10-03T01:02:00Z', continuationAvailable: true,
    }) })
  })

  await page.goto(`/missions/${missionId}`)
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByText('Explain how preload influences stroke volume.')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Response' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Continue' })).toHaveCount(0)

  await page.getByRole('textbox', { name: 'Response' }).fill('Preload stretches the ventricle and increases stroke volume.')
  await page.getByRole('button', { name: 'Submit response' }).click()
  await expect(page.getByText('You explained the relationship.')).toBeVisible()
  expect(connectionWrites).toBe(1)
})

test('pause survives refresh and resume restores authoritative ACTIVE state', async ({ page }) => {
  let status = 'ACTIVE'
  await mockSession(page)
  await page.route('**/api/auth/csrf', async (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test-token' }) }))
  await page.route(`**/api/study-missions/${missionId}`, async (route) => {
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

async function mockSession(page: Page) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: '22222222-2222-4222-8222-222222222222' }),
    })
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
