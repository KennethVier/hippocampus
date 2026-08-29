import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'

import { queryClient } from './providers/queryClient'
import { browserRouter } from './router/browserRouter'

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={browserRouter} />
    </QueryClientProvider>
  )
}
