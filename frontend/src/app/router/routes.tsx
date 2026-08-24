import { Navigate, type RouteObject } from 'react-router'

import { AuthPlaceholderLayout } from '../layout/AuthPlaceholderLayout'
import { NotFoundPage } from './NotFoundPage'
import { RouteErrorBoundary } from './RouteErrorBoundary'
import { RoutePlaceholder } from './RoutePlaceholder'

export const appRoutes = [
  {
    path: '/',
    element: <AuthPlaceholderLayout />,
    children: [
      {
        errorElement: <RouteErrorBoundary />,
        children: [
          { index: true, element: <Navigate replace to="/home" /> },
          { path: 'home', element: <RoutePlaceholder title="Home" /> },
          { path: 'subjects', element: <RoutePlaceholder title="Subjects" /> },
          {
            path: 'subjects/:subjectId',
            element: <RoutePlaceholder title="Subject" />,
          },
          { path: 'topics/:topicId', element: <RoutePlaceholder title="Topic" /> },
          {
            path: 'missions/:missionId',
            element: <RoutePlaceholder title="Study Mission" />,
          },
          { path: 'materials', element: <RoutePlaceholder title="Materials" /> },
          {
            path: 'materials/:materialId',
            element: <RoutePlaceholder title="Material" />,
          },
          { path: 'review', element: <RoutePlaceholder title="Review" /> },
          { path: 'progress', element: <RoutePlaceholder title="Progress" /> },
          { path: 'settings', element: <RoutePlaceholder title="Settings" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
] satisfies RouteObject[]
