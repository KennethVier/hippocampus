import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { createAppQueryClient } from '../../app/providers/queryClient'
import { appRoutes } from '../../app/router/routes'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('authentication routes', () => {
  it('does not render private content while session bootstrap is unresolved', () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockReturnValue(new Promise(() => undefined)))
    renderRoute('/home')
    expect(screen.getByText('Checking your session…')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Home' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('redirects an unauthenticated protected route to login', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(authenticationRequired()))
    const router = renderRoute('/subjects/example?view=recent#notes')
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('does not misclassify a network failure as logged out', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline')))
    renderRoute('/home')
    expect(await screen.findByText("We couldn't check your session.")).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
  })

  it('shows a generic invalid-credentials message', async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(authenticationRequired())
      .mockResolvedValueOnce(jsonResponse({ token: 'csrf' }))
      .mockResolvedValueOnce(problemResponse(401, 'AUTHENTICATION_FAILED', 'Authentication failed.'))
    vi.stubGlobal('fetch', fetchMock)
    renderRoute('/login')
    await screen.findByRole('heading', { name: 'Welcome back' })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'student@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'private password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(screen.queryByText('Authentication failed.')).not.toBeInTheDocument()
  })
})

function renderRoute(initialEntry: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [initialEntry] })
  render(<QueryClientProvider client={createAppQueryClient()}><RouterProvider router={router} /></QueryClientProvider>)
  return router
}

function authenticationRequired() {
  return problemResponse(401, 'AUTHENTICATION_REQUIRED', 'Authentication is required.')
}

function problemResponse(status: number, code: string, message: string) {
  return new Response(JSON.stringify({ status, code, message, details: {} }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}
