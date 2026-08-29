import type { QueryClient } from '@tanstack/react-query'

export async function clearPrivateClientState(queryClient: QueryClient): Promise<void> {
  await queryClient.cancelQueries()
  queryClient.clear()
}
