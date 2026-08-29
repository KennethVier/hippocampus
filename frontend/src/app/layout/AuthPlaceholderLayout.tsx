import { Link, NavLink, Outlet } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { Button } from '../../components/ui'
import { logout } from '../../features/auth/authApi'
import { currentSessionQueryKey } from '../../features/auth/currentSessionQuery'

import './AuthPlaceholderLayout.css'

const primaryNavigation = [
  { label: 'Home', to: '/home' },
  { label: 'Subjects', to: '/subjects' },
  { label: 'Materials', to: '/materials' },
  { label: 'Review', to: '/review' },
  { label: 'Progress', to: '/progress' },
] as const

export function AuthPlaceholderLayout() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey: currentSessionQueryKey })
      queryClient.removeQueries({ queryKey: currentSessionQueryKey })
      navigate('/login', { replace: true })
    },
  })

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="app-header">
        <Link className="product-link" to="/home">
          Hippocampus
        </Link>
        <div className="header-actions">
          <Link to="/settings">Settings</Link>
          <Button disabled={logoutMutation.isPending} onClick={() => logoutMutation.mutate()} variant="tertiary">
            {logoutMutation.isPending ? 'Signing out…' : 'Sign out'}
          </Button>
        </div>
      </header>

      <div className="app-body">
        <nav aria-label="Primary navigation" className="primary-navigation">
          <ul>
            {primaryNavigation.map(({ label, to }) => (
              <li key={to}>
                <NavLink
                  className={({ isActive }) =>
                    isActive ? 'navigation-link navigation-link-active' : 'navigation-link'
                  }
                  to={to}
                >
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <main className="main-content" id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      {logoutMutation.isError ? <p className="logout-error" role="alert">Sign out could not be completed. Try again.</p> : null}
    </div>
  )
}
