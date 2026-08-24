import { Link } from 'react-router'

export function RouteErrorBoundary() {
  return (
    <section>
      <h1>Unable to display this page</h1>
      <p>The application shell is still available. Please return home and try again.</p>
      <Link to="/home">Return home</Link>
    </section>
  )
}
