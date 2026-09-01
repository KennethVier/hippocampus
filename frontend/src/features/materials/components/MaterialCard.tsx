import { Link } from 'react-router'
import { Button, Card } from '../../../components/ui'
import type { Material } from '../api/materialContracts'
import { displayMaterialStatus } from '../materialPresentation'

export function MaterialCard({ material, onDelete }: { readonly material: Material; readonly onDelete: () => void }) {
  return <Card className="material-card">
    <div><p className="materials-eyebrow">{material.materialType}</p><h2>{material.title}</h2></div>
    <dl className="material-metadata">
      {material.originalFilename ? <><dt>Original file</dt><dd>{material.originalFilename}</dd></> : null}
      {material.mimeType ? <><dt>File type</dt><dd>{material.mimeType}</dd></> : null}
      <dt>Status</dt><dd>{displayMaterialStatus(material.status)}</dd>
      <dt>Added</dt><dd>{new Date(material.createdAt).toLocaleString()}</dd>
    </dl>
    <div className="materials-actions"><Link className="materials-link-action" to={`/materials/${material.id}`}>Open material</Link><Button onClick={onDelete} variant="tertiary">Delete material</Button></div>
  </Card>
}
