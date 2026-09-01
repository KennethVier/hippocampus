import { Link } from 'react-router'

import { Button, Card } from '../../../components/ui'

interface OrganizationCardProps {
  readonly kind: 'Subject' | 'Topic'
  readonly name: string
  readonly description: string | null
  readonly detailPath?: string
  readonly onEdit: () => void
  readonly onArchive: () => void
}

export function OrganizationCard({ kind, name, description, detailPath, onEdit, onArchive }: OrganizationCardProps) {
  return (
    <Card className="organization-card">
      <div className="organization-card-copy">
        <h2>{name}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      <div className="organization-actions">
        {detailPath ? <Link className="organization-link-action" to={detailPath}>Open Subject</Link> : null}
        <Button onClick={onEdit} variant="tertiary">Edit {kind}</Button>
        <Button onClick={onArchive} variant="tertiary">Archive {kind}</Button>
      </div>
    </Card>
  )
}
