import { useId, useState } from 'react'
import { Button } from '../../../components/ui'
import type { MissionSource } from '../api/studyMissionContracts'

export interface SourcePanelProps {
  sources: MissionSource[]
}

export function SourcePanel({ sources }: SourcePanelProps) {
  const [expanded, setExpanded] = useState(true)
  const sourceListId = useId()

  return (
    <aside aria-labelledby={`${sourceListId}-heading`} className="mission-sources">
      <div className="mission-sources-heading">
        <div>
          <p className="mission-eyebrow">Provenance</p>
          <h2 id={`${sourceListId}-heading`}>Sources</h2>
        </div>
        {sources.length > 0 ? (
          <Button
            aria-controls={sourceListId}
            aria-expanded={expanded}
            onClick={() => setExpanded((current) => !current)}
            variant="tertiary"
          >
            {expanded ? 'Hide sources' : 'Show sources'}
          </Button>
        ) : null}
      </div>

      {sources.length === 0 ? <p className="mission-muted">No sources are available for this activity.</p> : null}
      {expanded && sources.length > 0 ? (
        <ul className="mission-source-list" id={sourceListId}>
          {sources.map((source) => (
            <li className="mission-source" key={source.sourceReferenceId}>
              <p className="mission-source-title">{source.materialTitle}</p>
              {source.pageNumber !== null ? <p>Page {source.pageNumber}</p> : null}
              {source.displayLabel ? <p>{source.displayLabel}</p> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </aside>
  )
}
