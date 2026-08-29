import { expect, test, type Page } from '@playwright/test'

const email = process.env.HIPPOCAMPUS_E2E_EMAIL
const password = process.env.HIPPOCAMPUS_E2E_PASSWORD

test.skip(!email || !password, 'Requires an isolated, runtime-provisioned E2E account.')

test('login, session recovery, and logout use the real server session', async ({ page }) => {
  await page.goto('/subjects/example-subject?view=recent#notes')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0)

  await page.getByLabel('Email').fill(email ?? '')
  await page.getByLabel('Password').fill('intentionally incorrect')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')

  await page.getByLabel('Password').fill(password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/subjects\/example-subject\?view=recent#notes$/)
  await expect(page.getByRole('heading', { name: 'Subject' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)

  await invalidateSession(page)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0)

  await page.getByLabel('Email').fill(email ?? '')
  await page.getByLabel('Password').fill(password ?? '')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Subject' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()

  const me = await page.request.get('http://127.0.0.1:8080/api/auth/me')
  expect(me.status()).toBe(401)
  await page.goto('/home')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
})

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
