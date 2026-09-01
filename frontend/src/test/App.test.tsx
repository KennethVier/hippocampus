import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../app/App'
import { AuthPlaceholderLayout } from '../app/layout/AuthPlaceholderLayout'
import { RouteErrorBoundary } from '../app/router/RouteErrorBoundary'
import { appRoutes } from '../app/router/routes'
import { createAppQueryClient } from '../app/providers/queryClient'
import { currentSessionQueryKey } from '../features/auth/currentSessionQuery'

function renderRoute(initialEntry: string, routes: RouteObject[] = appRoutes) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  const client = createAppQueryClient()
  client.setQueryData(currentSessionQueryKey, { userId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301' })

  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )

  return router
}

function BrokenRoute(): ReactNode {
  throw new Error('sensitive route failure details')
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
    new Response(JSON.stringify({ userId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  ))
})

describe('App', () => {
  it('renders through the browser router', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeInTheDocument()
  })

  it('redirects the root route to home', async () => {
    const router = renderRoute('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/home'))
    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })

  it.each([
    ['/home', 'Home'],
    ['/subjects', 'Subjects'],
    ['/subjects/example-subject', 'Subject unavailable'],
    ['/topics/example-topic', 'Topic'],
    ['/missions/example-mission', 'Study Mission'],
    ['/materials', 'Materials'],
    ['/materials/example-material', 'Material unavailable'],
    ['/review', 'Review'],
    ['/progress', 'Progress'],
    ['/settings', 'Settings'],
  ])('resolves documented route %s', (path, heading) => {
    renderRoute(path)

    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('navigates with the compact primary navigation without remounting the shell', async () => {
    const router = renderRoute('/home')
    const banner = screen.getByRole('banner')
    const primaryNavigation = screen.getByRole('navigation', {
      name: 'Primary navigation',
    })

    expect(
      within(primaryNavigation).getAllByRole('link').map((link) => link.textContent),
    ).toEqual(['Home', 'Subjects', 'Materials', 'Review', 'Progress'])
    expect(within(primaryNavigation).queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()

    fireEvent.click(within(primaryNavigation).getByRole('link', { name: 'Subjects' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/subjects'))
    expect(screen.getByRole('heading', { name: 'Subjects' })).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBe(banner)
  })

  it('marks the current primary route as active', () => {
    renderRoute('/materials/example-material')

    expect(screen.getByRole('link', { name: 'Materials' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('renders a safe not-found route inside the shell', () => {
    renderRoute('/unknown-route')

    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Return home' })).toBeInTheDocument()
  })

  it('renders a sanitized route failure inside the shell', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const failureRoutes = [
      {
        path: '/',
        element: <AuthPlaceholderLayout />,
        children: [
          {
            errorElement: <RouteErrorBoundary />,
            children: [{ path: 'failure', element: <BrokenRoute /> }],
          },
        ],
      },
    ] satisfies RouteObject[]

    renderRoute('/failure', failureRoutes)

    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Unable to display this page' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('sensitive route failure details')).not.toBeInTheDocument()
  })

  it.each([
    { width: 1440, height: 900, viewport: 'desktop' },
    { width: 390, height: 844, viewport: 'mobile' },
  ])('keeps route landmarks available at $viewport width', ({ width, height }) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: height })
    window.dispatchEvent(new Event('resize'))

    renderRoute('/home')

    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })
})
