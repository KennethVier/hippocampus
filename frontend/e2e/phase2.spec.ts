import { expect, test, type Page } from '@playwright/test'

const backendBaseUrl = 'http://127.0.0.1:8080'
const userA = {
  email: process.env.HIPPOCAMPUS_E2E_USER_A_EMAIL,
  password: process.env.HIPPOCAMPUS_E2E_USER_A_PASSWORD,
}
const userB = {
  email: process.env.HIPPOCAMPUS_E2E_USER_B_EMAIL,
  password: process.env.HIPPOCAMPUS_E2E_USER_B_PASSWORD,
}

test.skip(
  [...Object.values(userA), ...Object.values(userB)].some((value) => !value),
  'Requires two isolated, runtime-provisioned E2E accounts and the real backend.',
)

test('Phase 2 keeps organization and uploaded materials private through deletion', async ({ page }) => {
  const unique = `${Date.now()}-${test.info().project.name}`
  const subjectName = `Phase 2 Anatomy ${unique}`
  const topicName = `Upper Limb ${unique}`
  const materialName = `phase2-${unique}.txt`

  await page.goto('/subjects')
  await signIn(page, userA)
  await expect(page.getByRole('heading', { level: 1, name: 'Subjects', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Create Subject' }).first().click()
  await page.getByLabel('Name').fill(subjectName)
  await page.getByLabel('Description').fill('Phase 2 private organization gate')
  await page.getByRole('button', { name: 'Save Subject' }).click()
  const subjectHeading = page.getByRole('heading', { name: subjectName, exact: true })
  await expect(subjectHeading).toBeVisible()
  const subjectCard = subjectHeading.locator('..').locator('..')
  await subjectCard.getByRole('link', { name: 'Open Subject' }).click()
  await expect(page.getByRole('heading', { level: 1, name: subjectName, exact: true })).toBeVisible()
  const subjectId = idFromPath(page.url(), 'subjects')

  await page.getByRole('button', { name: 'Create Topic' }).first().click()
  await page.getByLabel('Name').fill(topicName)
  await page.getByLabel('Description').fill('Phase 2 topic gate')
  await page.getByRole('button', { name: 'Save Topic' }).click()
  await expect(page.getByRole('heading', { name: topicName, exact: true })).toBeVisible()

  await page.goto('/materials')
  await page.getByLabel('Choose file').setInputFiles({
    name: materialName,
    mimeType: 'text/plain',
    buffer: Buffer.from(`Phase 2 private material ${unique}\n`),
  })
  await page.getByRole('button', { name: 'Upload file' }).click()
  await expect(page.getByRole('heading', { name: 'Upload accepted' })).toBeVisible()
  const materialHeading = page.getByRole('heading', { name: materialName, exact: true }).last()
  await expect(materialHeading).toBeVisible()
  const materialCard = materialHeading.locator('..').locator('..')
  await materialCard.getByRole('link', { name: 'Open material' }).click()
  await expect(page.getByRole('heading', { level: 1, name: materialName, exact: true })).toBeVisible()
  const materialId = idFromPath(page.url(), 'materials')

  expect((await page.request.get(`${backendBaseUrl}/api/subjects/${subjectId}`)).status()).toBe(200)
  expect((await page.request.get(`${backendBaseUrl}/api/materials/${materialId}`)).status()).toBe(200)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await signIn(page, userB)
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible()

  const foreignSubject = await page.request.get(`${backendBaseUrl}/api/subjects/${subjectId}`)
  expect(foreignSubject.status()).toBe(404)
  const foreignSubjectBody = await foreignSubject.text()
  expect(foreignSubjectBody).toContain('SUBJECT_NOT_FOUND')
  expect(foreignSubjectBody).not.toContain(subjectName)

  const foreignMaterial = await page.request.get(`${backendBaseUrl}/api/materials/${materialId}`)
  expect(foreignMaterial.status()).toBe(404)
  const foreignMaterialBody = await foreignMaterial.text()
  expect(foreignMaterialBody).toContain('MATERIAL_NOT_FOUND')
  expect(foreignMaterialBody).not.toContain(materialName)

  const userBSubjects = await page.request.get(`${backendBaseUrl}/api/subjects?size=100`)
  expect(userBSubjects.status()).toBe(200)
  expect(await userBSubjects.text()).not.toContain(subjectName)
  const userBMaterials = await page.request.get(`${backendBaseUrl}/api/materials?size=100`)
  expect(userBMaterials.status()).toBe(200)
  expect(await userBMaterials.text()).not.toContain(materialName)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await signIn(page, userA)
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible()

  await page.goto(`/materials/${materialId}`)
  await expect(page.getByRole('heading', { level: 1, name: materialName, exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Delete material' }).click()
  const deleteDialog = page.getByRole('dialog', { name: 'Delete material' })
  await deleteDialog.getByRole('button', { name: 'Delete material' }).click()
  await expect(page).toHaveURL(/\/materials(?:\?page=1)?$/)
  await expect(page.getByRole('heading', { name: materialName, exact: true })).toHaveCount(0)

  await page.goto(`/materials/${materialId}`)
  await expect(page.getByRole('heading', { name: 'Material unavailable' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
})

async function signIn(page: Page, user: { email?: string; password?: string }) {
  await page.getByLabel('Email').fill(user.email ?? '')
  await page.getByLabel('Password').fill(user.password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
}

function idFromPath(url: string, resource: 'subjects' | 'materials'): string {
  const match = new URL(url).pathname.match(new RegExp(`/${resource}/([0-9a-f-]{36})$`))
  expect(match).not.toBeNull()
  return match?.[1] ?? ''
}
