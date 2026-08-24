import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section>
      <h1>Page not found</h1>
      <p>The requested page does not exist.</p>
      <Link to="/home">Return home</Link>
    </section>
  )
}
