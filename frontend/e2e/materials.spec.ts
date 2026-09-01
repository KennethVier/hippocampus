import { expect, test } from '@playwright/test'
import path from 'node:path'

const email = process.env.HIPPOCAMPUS_E2E_USER_A_EMAIL
const password = process.env.HIPPOCAMPUS_E2E_USER_A_PASSWORD
test.skip(!email || !password, 'Requires a runtime-provisioned E2E account and real backend.')

test('student uploads, opens, and deletes a Material', async ({ page }) => {
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
