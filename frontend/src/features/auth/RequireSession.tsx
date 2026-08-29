import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'

import { Button } from '../../components/ui'
import { currentSessionQueryOptions, isAuthenticationRequired } from './currentSessionQuery'
import { returnStateFromLocation } from './safeReturnPath'

export function RequireSession() {
  const location = useLocation()
  const session = useQuery(currentSessionQueryOptions)

  if (session.isPending) return <SessionStatus message="Checking your session…" />
  if (session.isError) {
    if (isAuthenticationRequired(session.error)) {
      return <Navigate replace state={returnStateFromLocation(location)} to="/login" />
    }
    return (
      <SessionStatus message="We couldn't check your session.">
        <Button onClick={() => void session.refetch()}>Try again</Button>
      </SessionStatus>
    )
  }
  return <Outlet />
}

function SessionStatus({ children, message }: { readonly children?: ReactNode; readonly message: string }) {
  return (
    <main className="auth-status" aria-live="polite">
      <p>{message}</p>
      {children}
    </main>
  )
}
