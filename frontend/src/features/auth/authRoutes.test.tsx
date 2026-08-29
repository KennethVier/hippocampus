import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useSyncExternalStore, type ReactNode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { createAppQueryClient } from '../../app/providers/queryClient'
import { appRoutes } from '../../app/router/routes'
import { currentSessionQueryKey } from './currentSessionQuery'

const userAId = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const userBId = '9a7b3302-b431-45e1-90e3-298c9d80918f'
const privateSentinelQueryKey = ['test-private-state', 'visible'] as const

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
    const client = createAppQueryClient()
    client.setQueryData(['subjects', 'USER_A'], ['USER_A_PRIVATE_SUBJECT'])
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline')))
    const router = renderRoute('/home', client)
    expect(await screen.findByText("We couldn't check your session.")).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/home')
    expect(client.getQueryData(['subjects', 'USER_A'])).toEqual(['USER_A_PRIVATE_SUBJECT'])
  })

  it('shows and focuses a generic invalid-credentials message', async () => {
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
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Email or password is incorrect.')
    expect(alert).toHaveFocus()
    expect(screen.queryByText('Authentication failed.')).not.toBeInTheDocument()
  })

  it('clears User A state only after backend logout succeeds and isolates User B in the same runtime', async () => {
    const client = createAppQueryClient()
    let completeLogout: ((response: Response) => void) | undefined
    let completeUserAQuery: ((value: string) => void) | undefined
    let userAQuerySignal: AbortSignal | undefined
    let loggedOut = false
    const logoutResponse = new Promise<Response>((resolve) => {
      completeLogout = resolve
    })
    const fetchMock = vi.fn<typeof fetch>(async (_input, init) => {
      if (init?.method === 'POST') {
        const response = await logoutResponse
        loggedOut = true
        return response
      }
      if (String(_input).endsWith('/api/auth/csrf')) return jsonResponse({ token: 'csrf' })
      return loggedOut ? authenticationRequired() : jsonResponse({ userId: userAId })
    })
    vi.stubGlobal('fetch', fetchMock)

    client.setQueryData(currentSessionQueryKey, { userId: userAId })
    client.setQueryData(privateSentinelQueryKey, 'USER_A_PRIVATE_SENTINEL')
    client.setQueryData(['materials', 'USER_A'], ['USER_A_PRIVATE_MATERIAL'])
    const userAMutation = client.getMutationCache().build(client, {
      mutationKey: ['save', 'USER_A'],
      mutationFn: async () => 'USER_A_MUTATION_RESULT',
    })
    await userAMutation.execute(undefined)
    const userAUnderlyingResult = new Promise<string>((resolve) => {
      completeUserAQuery = resolve
    })
    const activeUserAQuery = client.fetchQuery({
      queryKey: ['missions', 'USER_A_PENDING'],
      queryFn: ({ signal }) => {
        userAQuerySignal = signal
        return userAUnderlyingResult
      },
    })
    const settledUserAQuery = activeUserAQuery.catch((error: unknown) => error)

    const router = renderRoute('/home', client, <PrivateStateConsumer client={client} />)
    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()
    expect(screen.getByTestId('private-state')).toHaveTextContent('USER_A_PRIVATE_SENTINEL')

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('button', { name: 'Signing out…' })).toBeDisabled()
    expect(router.state.location.pathname).toBe('/home')
    expect(client.getQueryData(privateSentinelQueryKey)).toBe('USER_A_PRIVATE_SENTINEL')

    completeLogout?.(new Response(null, { status: 204 }))

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument()
    expect(client.getQueryData(privateSentinelQueryKey)).toBeUndefined()
    expect(client.getQueryData(['materials', 'USER_A'])).toBeUndefined()
    expect(client.getMutationCache().getAll()).toHaveLength(0)
    expect(userAQuerySignal?.aborted).toBe(true)
    expect(screen.getByTestId('private-state')).toHaveTextContent('NO_PRIVATE_STATE')
    expect(screen.queryByText(/USER_A_PRIVATE/)).not.toBeInTheDocument()

    client.setQueryData(currentSessionQueryKey, { userId: userBId })
    client.setQueryData(privateSentinelQueryKey, 'USER_B_PRIVATE_SENTINEL')

    await waitFor(() => expect(screen.getByTestId('private-state')).toHaveTextContent('USER_B_PRIVATE_SENTINEL'))
    expect(client.getQueryData(privateSentinelQueryKey)).toBe('USER_B_PRIVATE_SENTINEL')
    expect(client.getQueryData(['materials', 'USER_A'])).toBeUndefined()
    expect(screen.queryByText(/USER_A_PRIVATE/)).not.toBeInTheDocument()

    completeUserAQuery?.('USER_A_LATE_PRIVATE_SENTINEL')
    await userAUnderlyingResult
    await settledUserAQuery
    await Promise.resolve()

    expect(client.getQueryData(['missions', 'USER_A_PENDING'])).toBeUndefined()
    expect(client.getQueryData(privateSentinelQueryKey)).toBe('USER_B_PRIVATE_SENTINEL')
    expect(screen.queryByText(/USER_A_(PRIVATE|LATE)/)).not.toBeInTheDocument()
  })

  it.each([
    ['network failure', () => Promise.reject(new TypeError('USER_A_RAW_NETWORK_DETAIL'))],
    ['server failure', () => Promise.resolve(problemResponse(500, 'SERVER_FAILURE', 'USER_A_RAW_500_DETAIL'))],
    ['unauthorized', () => Promise.resolve(problemResponse(401, 'AUTHENTICATION_REQUIRED', 'USER_A_RAW_401_DETAIL'))],
    ['CSRF rejection', () => Promise.resolve(problemResponse(403, 'ACCESS_DENIED', 'USER_A_RAW_403_DETAIL'))],
  ])('preserves private state when logout has a %s', async (_case, logoutResult) => {
    const client = createAppQueryClient()
    const privateKey = ['subjects', 'USER_A'] as const
    client.setQueryData(currentSessionQueryKey, { userId: userAId })
    client.setQueryData(privateKey, ['USER_A_PRIVATE_SUBJECT'])
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf')) return jsonResponse({ token: 'csrf' })
      if (init?.method === 'POST') return logoutResult()
      return jsonResponse({ userId: userAId })
    }))
    const router = renderRoute('/home', client)
    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Sign out could not be completed. Try again.')
    expect(router.state.location.pathname).toBe('/home')
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeInTheDocument()
    expect(client.getQueryData(privateKey)).toEqual(['USER_A_PRIVATE_SUBJECT'])
    expect(screen.queryByText(/USER_A_RAW_/)).not.toBeInTheDocument()
  })

  it('clears private state once on authoritative expiry and preserves the safe return path', async () => {
    const client = createAppQueryClient()
    const privateKey = ['materials', 'USER_A'] as const
    client.setQueryData(privateKey, ['USER_A_PRIVATE_MATERIAL'])
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(authenticationRequired())
    const cancelQueries = vi.spyOn(client, 'cancelQueries')
    const clear = vi.spyOn(client, 'clear')
    vi.stubGlobal('fetch', fetchMock)

    const router = renderRoute('/subjects/example?view=recent#notes', client)

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location.state).toEqual({
      returnTo: { pathname: '/subjects/example', search: '?view=recent', hash: '#notes' },
    })
    expect(client.getQueryData(privateKey)).toBeUndefined()
    expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(cancelQueries).toHaveBeenCalledTimes(1)
    expect(clear).toHaveBeenCalledTimes(1)
  })
})

function renderRoute(initialEntry: string, client = createAppQueryClient(), sibling?: ReactNode) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [initialEntry] })
  render(<QueryClientProvider client={client}><RouterProvider router={router} />{sibling}</QueryClientProvider>)
  return router
}

function PrivateStateConsumer({ client }: { readonly client: QueryClient }) {
  const privateState = useSyncExternalStore(
    (onStoreChange) => client.getQueryCache().subscribe(onStoreChange),
    () => String(client.getQueryData(privateSentinelQueryKey) ?? 'NO_PRIVATE_STATE'),
  )
  return <output data-testid="private-state">{privateState}</output>
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
