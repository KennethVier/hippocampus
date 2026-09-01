import type { QueryClient } from '@tanstack/react-query'

type PrivateClientCleanup = () => void
const privateClientCleanups = new Set<PrivateClientCleanup>()

export function registerPrivateClientCleanup(cleanup: PrivateClientCleanup): () => void {
  privateClientCleanups.add(cleanup)
  let registered = true
  return () => {
    if (!registered) return
    registered = false
    privateClientCleanups.delete(cleanup)
  }
}

export async function clearPrivateClientState(queryClient: QueryClient): Promise<void> {
  for (const cleanup of [...privateClientCleanups]) {
    try { cleanup() } catch { /* Private cleanup is best-effort and intentionally not logged. */ }
  }
  try {
    await queryClient.cancelQueries()
  } catch {
    // Session teardown must continue even if a cancellation implementation fails.
  } finally {
    queryClient.clear()
  }
}
