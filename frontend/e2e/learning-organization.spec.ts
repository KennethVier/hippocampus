import { expect, test } from '@playwright/test'

const email = process.env.HIPPOCAMPUS_E2E_USER_A_EMAIL
const password = process.env.HIPPOCAMPUS_E2E_USER_A_PASSWORD

test.skip(!email || !password, 'Requires a runtime-provisioned E2E account and real backend.')

test('student completes Subject and Topic organization CRUD', async ({ page }) => {
  const unique = `${Date.now()}-${test.info().project.name}`
  const subjectName = `Clinical Anatomy ${unique}`
  const editedSubject = `Applied Anatomy ${unique}`
  const topicName = `Thorax ${unique}`
  const editedTopic = `Clinical Thorax ${unique}`

  await page.goto('/subjects')
  await page.getByLabel('Email').fill(email ?? '')
  await page.getByLabel('Password').fill(password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Subjects', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Create Subject' }).first().click()
  await page.getByLabel('Name').fill(subjectName)
  await page.getByLabel('Description').fill('A private study area')
  await page.getByRole('button', { name: 'Save Subject' }).click()
  await expect(page.getByRole('heading', { name: subjectName, exact: true })).toBeVisible()

  const subjectCard = page.getByRole('heading', { name: subjectName, exact: true }).locator('..').locator('..')
  await subjectCard.getByRole('link', { name: 'Open Subject' }).click()
  await expect(page.getByRole('heading', { level: 1, name: subjectName, exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Create Topic' }).first().click()
  await page.getByLabel('Name').fill(topicName)
  await page.getByLabel('Description').fill('Chest anatomy')
  await page.getByRole('button', { name: 'Save Topic' }).click()
  await expect(page.getByRole('heading', { name: topicName, exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Edit Subject' }).click()
  await page.getByLabel('Name').fill(editedSubject)
  await page.getByRole('button', { name: 'Save Subject' }).click()
  await expect(page.getByRole('heading', { level: 1, name: editedSubject, exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Edit Topic' }).click()
  await page.getByLabel('Name').fill(editedTopic)
  await page.getByRole('button', { name: 'Save Topic' }).click()
  await expect(page.getByRole('heading', { name: editedTopic, exact: true })).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { level: 1, name: editedSubject, exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: editedTopic, exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)

  await page.getByRole('button', { name: 'Archive Topic' }).click()
  const topicDialog = page.getByRole('dialog', { name: 'Archive Topic' })
  await topicDialog.getByRole('button', { name: 'Archive Topic' }).click()
  await expect(page.getByRole('heading', { name: editedTopic, exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: 'Archive Subject' }).click()
  const subjectDialog = page.getByRole('dialog', { name: 'Archive Subject' })
  await subjectDialog.getByRole('button', { name: 'Archive Subject' }).click()
  await expect(page).toHaveURL(/\/subjects(?:\?page=1)?$/)
  await expect(page.getByRole('heading', { name: editedSubject, exact: true })).toHaveCount(0)
})
