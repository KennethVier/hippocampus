import { expect, test, type Page } from '@playwright/test'

const userA = {
  email: process.env.HIPPOCAMPUS_E2E_USER_A_EMAIL,
  password: process.env.HIPPOCAMPUS_E2E_USER_A_PASSWORD,
  userId: process.env.HIPPOCAMPUS_E2E_USER_A_ID,
}
const userB = {
  email: process.env.HIPPOCAMPUS_E2E_USER_B_EMAIL,
  password: process.env.HIPPOCAMPUS_E2E_USER_B_PASSWORD,
  userId: process.env.HIPPOCAMPUS_E2E_USER_B_ID,
}

test.skip(
  [...Object.values(userA), ...Object.values(userB)].some((value) => !value),
  'Requires two isolated, runtime-provisioned E2E accounts.',
)

test('shared browser isolates two authoritative server sessions', async ({ page }) => {
  await page.goto('/subjects/example-subject?view=recent#notes')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0)

  await page.getByLabel('Email').fill(userA.email ?? '')
  await page.getByLabel('Password').fill('intentionally incorrect')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')

  await page.getByLabel('Password').fill(userA.password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/subjects\/example-subject\?view=recent#notes$/)
  await expect(page.getByRole('heading', { name: 'Subject' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)

  const userAMe = await currentUser(page)
  expect(userAMe.userId).toBe(userA.userId)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()

  const loggedOutMe = await page.request.get('http://127.0.0.1:8080/api/auth/me')
  expect(loggedOutMe.status()).toBe(401)

  await page.getByLabel('Email').fill(userB.email ?? '')
  await page.getByLabel('Password').fill(userB.password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible()

  const userBMe = await currentUser(page)
  expect(userBMe.userId).toBe(userB.userId)
  expect(userBMe.userId).not.toBe(userAMe.userId)
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)

  await invalidateSession(page)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0)
  await page.goto('/home')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
})

async function currentUser(page: Page): Promise<{ userId: string }> {
  const response = await page.request.get('http://127.0.0.1:8080/api/auth/me')
  expect(response.status()).toBe(200)
  return response.json() as Promise<{ userId: string }>
}

async function invalidateSession(page: Page) {
  await page.evaluate(async () => {
    const csrf = await fetch('http://127.0.0.1:8080/api/auth/csrf', { credentials: 'include' })
      .then((response) => response.json()) as { token: string }
    const response = await fetch('http://127.0.0.1:8080/api/auth/logout', {
      method: 'POST', credentials: 'include', headers: { 'X-CSRF-TOKEN': csrf.token },
    })
    if (!response.ok) throw new Error('Session invalidation failed')
  })
}
