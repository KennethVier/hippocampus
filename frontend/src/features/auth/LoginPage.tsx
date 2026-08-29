import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'

import { ApiError } from '../../api/apiClient'
import { Button, Card, Input } from '../../components/ui'
import { login } from './authApi'
import { currentSessionQueryKey, currentSessionQueryOptions, isAuthenticationRequired } from './currentSessionQuery'
import { safeReturnPath } from './safeReturnPath'
import './auth.css'

export function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const session = useQuery(currentSessionQueryOptions)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const errorSummaryRef = useRef<HTMLDivElement>(null)
  const destination = safeReturnPath(location.state)
  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: async () => {
      const confirmed = await queryClient.fetchQuery(currentSessionQueryOptions)
      queryClient.setQueryData(currentSessionQueryKey, confirmed)
      navigate(destination, { replace: true })
    },
  })
  const mutationError = loginMutation.error
  const errorMessage = validationError ?? loginErrorMessage(mutationError)
  const checkingFailed = session.isError && !isAuthenticationRequired(session.error)

  useEffect(() => {
    if (errorMessage) errorSummaryRef.current?.focus()
  }, [errorMessage])

  if (session.isSuccess) return <Navigate replace to={destination} />

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!email.trim() || !password) {
      setValidationError('Enter your email and password.')
      return
    }
    setValidationError(null)
    loginMutation.mutate({ email: email.trim(), password })
  }

  return (
    <main className="login-page">
      <Card className="login-card">
        <p className="login-brand">Hippocampus</p>
        <h1>Welcome back</h1>
        <p className="login-introduction">Sign in to continue your medical studies.</p>
        {checkingFailed ? (
          <div className="login-alert" role="alert">
            We couldn't check your existing session. You can still try to sign in.
          </div>
        ) : null}
        {errorMessage ? <div ref={errorSummaryRef} className="login-alert" role="alert" tabIndex={-1}>{errorMessage}</div> : null}
        <form className="login-form" onSubmit={submit} noValidate>
          <Input autoComplete="email" label="Email" name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input autoComplete="current-password" label="Password" name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          <Button disabled={loginMutation.isPending} type="submit">
            {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </Card>
    </main>
  )
}

function loginErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return error ? 'Sign in could not be completed. Try again.' : null
  if (error.code === 'AUTHENTICATION_FAILED') return 'Email or password is incorrect.'
  if (error.code === 'AUTHENTICATION_RATE_LIMITED') return 'Too many sign-in attempts. Try again later.'
  return 'Sign in could not be completed. Check your connection and try again.'
}
