import { expect, test } from '@playwright/test'
import path from 'node:path'

const email = process.env.HIPPOCAMPUS_E2E_USER_A_EMAIL
const password = process.env.HIPPOCAMPUS_E2E_USER_A_PASSWORD

test('student uploads, opens, and deletes a Material', async ({ page }) => {
  test.skip(!email || !password, 'Requires a runtime-provisioned E2E account and real backend.')

  await page.goto('/materials')
  await page.getByLabel('Email').fill(email ?? '')
  await page.getByLabel('Password').fill(password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Materials' })).toBeVisible()

  await page.getByLabel('Choose file').setInputFiles(path.join(import.meta.dirname, 'fixtures/p2-10-notes.txt'))
  await page.getByRole('button', { name: 'Upload file' }).click()
  await expect(page.getByRole('heading', { name: 'Upload accepted' })).toBeVisible()
  const cardHeading = page.getByRole('heading', { name: 'p2-10-notes.txt', exact: true }).last()
  await expect(cardHeading).toBeVisible()
  const card = cardHeading.locator('..').locator('..')
  await card.getByRole('link', { name: 'Open material' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'p2-10-notes.txt' })).toBeVisible()
  await expect(page.getByText('text/plain')).toBeVisible()
  const detailUrl = page.url()

  await page.getByRole('button', { name: 'Delete material' }).click()
  let dialog = page.getByRole('dialog', { name: 'Delete material' })
  await dialog.getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'p2-10-notes.txt' })).toBeVisible()
  await page.getByRole('button', { name: 'Delete material' }).click()
  dialog = page.getByRole('dialog', { name: 'Delete material' })
  await dialog.getByRole('button', { name: 'Delete material' }).click()
  await expect(page).toHaveURL(/\/materials(?:\?page=1)?$/)
  await expect(page.getByRole('heading', { name: 'p2-10-notes.txt', exact: true })).toHaveCount(0)
  await page.goto(detailUrl)
  await expect(page.getByRole('heading', { name: 'Material unavailable' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
})

test.describe('material processing status presentation', () => {
  test('shows partial readiness with student-facing limitation text', async ({ page }) => {
    await mockMaterialDetail(page, 'PARTIALLY_READY', 'Some parts of this material could not be fully processed.')

    await page.goto(`/materials/${materialId}`)

    await expect(page.getByRole('heading', { level: 1, name: 'processing-fixture.pdf' })).toBeVisible()
    await expect(page.getByText('Ready with limitations')).toBeVisible()
    await expect(page.getByText('Some parts of this material could not be fully processed.')).toBeVisible()
    await expect(page.getByText('PARTIALLY_READY')).toHaveCount(0)
  })

  test('shows failure readiness without exposing raw processing values', async ({ page }) => {
    await mockMaterialDetail(page, 'FAILED', null)

    await page.goto(`/materials/${materialId}`)

    await expect(page.getByText('Needs attention before study')).toBeVisible()
    await expect(page.getByText('FAILED')).toHaveCount(0)
    await expect(page.getByText('OCR')).toHaveCount(0)
  })
})

const materialId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const versionId = '9a7b3302-b431-45e1-90e3-298c9d80918f'

async function mockMaterialDetail(page: import('@playwright/test').Page, readiness: string, limitation: string | null) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: '11111111-1111-4111-8111-111111111111' }),
    })
  })
  await page.route(`**/api/materials/${materialId}/processing`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        materialId,
        versionId,
        readiness,
        stage: readiness === 'FAILED' ? 'OCR' : null,
        progress: null,
        limitation,
        structureAvailable: false,
      }),
    })
  })
  await page.route(`**/api/materials/${materialId}/structure`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ available: false, root: null }),
    })
  })
  await page.route(`**/api/materials/${materialId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: materialId,
        title: 'processing-fixture.pdf',
        materialType: 'PDF',
        originalFilename: 'processing-fixture.pdf',
        mimeType: 'application/pdf',
        status: readiness,
        createdAt: '2026-09-01T10:00:00Z',
        updatedAt: '2026-09-01T10:01:00Z',
      }),
    })
  })
}
