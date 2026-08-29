import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router'

import { Button } from '../../components/ui'
import { clearPrivateClientState } from './clearPrivateClientState'
import { currentSessionQueryOptions, isAuthenticationRequired } from './currentSessionQuery'
import { returnStateFromLocation, type ReturnLocation } from './safeReturnPath'

interface ExpiredReturnState {
  readonly returnTo: ReturnLocation
}

export function RequireSession() {
  const location = useLocation()
  const session = useQuery(currentSessionQueryOptions)
  const [expiredReturnState, setExpiredReturnState] = useState<ExpiredReturnState | null>(null)

  if (expiredReturnState) {
    return <ExpiredSessionTransition returnState={expiredReturnState} />
  }

  if (session.isError && isAuthenticationRequired(session.error)) {
    setExpiredReturnState(returnStateFromLocation(location))
    return <SessionStatus message="Your session has expired. Returning to sign in…" />
  }

  if (session.isPending) return <SessionStatus message="Checking your session…" />
  if (session.isError) {
    return (
      <SessionStatus message="We couldn't check your session.">
        <Button onClick={() => void session.refetch()}>Try again</Button>
      </SessionStatus>
    )
  }
  return <Outlet />
}

function ExpiredSessionTransition({ returnState }: { readonly returnState: ExpiredReturnState }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true

    void clearPrivateClientState(queryClient).then(() => {
      navigate('/login', { replace: true, state: returnState })
    })
  }, [navigate, queryClient, returnState])

  return <SessionStatus message="Your session has expired. Returning to sign in…" />
}

function SessionStatus({ children, message }: { readonly children?: ReactNode; readonly message: string }) {
  return (
    <main className="auth-status" aria-live="polite">
      <p>{message}</p>
      {children}
    </main>
  )
}
