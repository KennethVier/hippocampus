import { Link, NavLink, Outlet } from 'react-router'

import './AuthPlaceholderLayout.css'

const primaryNavigation = [
  { label: 'Home', to: '/home' },
  { label: 'Subjects', to: '/subjects' },
  { label: 'Materials', to: '/materials' },
  { label: 'Review', to: '/review' },
  { label: 'Progress', to: '/progress' },
] as const

export function AuthPlaceholderLayout() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="app-header">
        <Link className="product-link" to="/home">
          Hippocampus
        </Link>
        <Link to="/settings">Settings</Link>
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
    </div>
  )
}
