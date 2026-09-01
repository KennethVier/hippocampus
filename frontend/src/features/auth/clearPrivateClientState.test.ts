import { describe, expect, it } from 'vitest'

import { createAppQueryClient } from '../../app/providers/queryClient'
import { clearPrivateClientState, registerPrivateClientCleanup } from './clearPrivateClientState'
import { currentSessionQueryKey } from './currentSessionQuery'

describe('clearPrivateClientState', () => {
  it('removes every query and mutation belonging to the previous user', async () => {
    const queryClient = createAppQueryClient()
    const privateQueryKeys = [
      currentSessionQueryKey,
      ['subjects', 'user-a'] as const,
      ['material', 'user-a-material'] as const,
    ]

    queryClient.setQueryData(privateQueryKeys[0], { userId: 'user-a' })
    queryClient.setQueryData(privateQueryKeys[1], ['USER_A_SUBJECT'])
    queryClient.setQueryData(privateQueryKeys[2], { title: 'USER_A_MATERIAL' })

    const mutation = queryClient.getMutationCache().build(queryClient, {
      mutationKey: ['update-subject', 'user-a'],
      mutationFn: async () => 'USER_A_MUTATION_RESULT',
      meta: { owner: 'USER_A' },
    })
    await mutation.execute(undefined)

    await clearPrivateClientState(queryClient)

    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0)
    for (const queryKey of privateQueryKeys) {
      expect(queryClient.getQueryData(queryKey)).toBeUndefined()
    }
  })

  it('awaits active query cancellation and rejects a late User A result', async () => {
    const queryClient = createAppQueryClient()
    const queryKey = ['mission', 'user-a-active'] as const
    let resolveQuery: ((value: string) => void) | undefined
    let querySignal: AbortSignal | undefined
    const underlyingResult = new Promise<string>((resolve) => {
      resolveQuery = resolve
    })

    const activeQuery = queryClient.fetchQuery({
      queryKey,
      queryFn: ({ signal }) => {
        querySignal = signal
        return underlyingResult
      },
    })

    await Promise.resolve()
    expect(querySignal?.aborted).toBe(false)

    await clearPrivateClientState(queryClient)

    expect(querySignal?.aborted).toBe(true)
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    await expect(activeQuery).rejects.toBeDefined()

    resolveQuery?.('USER_A_LATE_RESULT')
    await underlyingResult
    await Promise.resolve()

    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    expect(queryClient.getQueryData(queryKey)).toBeUndefined()
  })

  it('runs registered cleanups before query cancellation and cache clearing', async () => {
    const queryClient = createAppQueryClient(); const order: string[] = []
    queryClient.setQueryData(['private'], 'USER_A')
    const unregister = registerPrivateClientCleanup(() => order.push('cleanup'))
    queryClient.cancelQueries = async () => { order.push('cancel') }
    const clear = queryClient.clear.bind(queryClient); queryClient.clear = () => { order.push('clear'); clear() }
    await clearPrivateClientState(queryClient); unregister()
    expect(order).toEqual(['cleanup', 'cancel', 'clear']); expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('isolates throwing callbacks and still cancels and clears', async () => {
    const queryClient = createAppQueryClient(); const order: string[] = []
    const first = registerPrivateClientCleanup(() => { order.push('throw'); throw new Error('PRIVATE_CALLBACK') })
    const second = registerPrivateClientCleanup(() => order.push('later'))
    queryClient.cancelQueries = async () => { order.push('cancel') }; queryClient.clear = () => order.push('clear')
    await clearPrivateClientState(queryClient); first(); second()
    expect(order).toEqual(['throw', 'later', 'cancel', 'clear'])
  })

  it('clears even when cancellation rejects', async () => {
    const queryClient = createAppQueryClient(); queryClient.setQueryData(['private'], 'USER_A')
    queryClient.cancelQueries = async () => { throw new Error('cancel failed') }
    await expect(clearPrivateClientState(queryClient)).resolves.toBeUndefined()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('unregisters idempotently', async () => {
    const queryClient = createAppQueryClient(); let calls = 0
    const unregister = registerPrivateClientCleanup(() => { calls += 1 }); unregister(); unregister()
    await clearPrivateClientState(queryClient); expect(calls).toBe(0)
  })
})
