type RoutePlaceholderProps = {
  title: string
}

export function RoutePlaceholder({ title }: RoutePlaceholderProps) {
  return (
    <section>
      <h1>{title}</h1>
      <p>This route is ready for its owning feature.</p>
    </section>
  )
}
