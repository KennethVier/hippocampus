import { expect, test, type Page } from '@playwright/test'

const missionId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'

test('student opens a Study Mission and inspects learner-safe sources', async ({ page }) => {
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
  await expect(page.getByText('Cardiovascular Physiology')).toBeVisible()
  await page.getByRole('button', { name: 'Hide sources' }).click()
  await expect(page.getByText('Cardiovascular Physiology')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Show sources' })).toHaveAttribute('aria-expanded', 'false')
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

async function mockSession(page: Page) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: '22222222-2222-4222-8222-222222222222' }),
    })
  })
}
