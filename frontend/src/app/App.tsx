import { RouterProvider } from 'react-router'

import { browserRouter } from './router/browserRouter'

export function App() {
  return <RouterProvider router={browserRouter} />
}

