import { describe, expect, it } from 'vitest'

import { createAppQueryClient } from '../../app/providers/queryClient'
import { clearPrivateClientState } from './clearPrivateClientState'
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
})
