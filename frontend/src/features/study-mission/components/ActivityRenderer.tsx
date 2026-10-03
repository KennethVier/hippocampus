import type {
  ApplicationActivity,
  ConnectionActivity,
  ExplanationActivity,
  RetrievalActivity,
  StudyMissionActivity,
} from '../api/studyMissionContracts'
import type { ReactNode } from 'react'

export interface ActivityRendererProps {
  activity: StudyMissionActivity
}

export function ActivityRenderer({ activity }: ActivityRendererProps) {
  switch (activity.type) {
    case 'EXPLANATION':
      return <ActivityShell activity={activity}><ExplanationPresentation activity={activity} /></ActivityShell>
    case 'RETRIEVAL':
      return <ActivityShell activity={activity}><RetrievalPresentation activity={activity} /></ActivityShell>
    case 'CONNECTION':
      return <ActivityShell activity={activity}><ConnectionPresentation activity={activity} /></ActivityShell>
    case 'APPLICATION':
      return <ActivityShell activity={activity}><ApplicationPresentation activity={activity} /></ActivityShell>
    case 'VISUAL':
      return <ActivityShell activity={activity}><NeutralActivityState>Visual learning content is not available for this activity yet.</NeutralActivityState></ActivityShell>
    case 'FEEDBACK':
      return <ActivityShell activity={activity}><NeutralActivityState>Feedback is not available for this activity yet.</NeutralActivityState></ActivityShell>
    case 'REFLECTION':
      return <ActivityShell activity={activity}><NeutralActivityState>Reflection content is not available for this activity yet.</NeutralActivityState></ActivityShell>
  }

  return assertNever(activity)
}

function ActivityShell({ activity, children }: { activity: StudyMissionActivity; children: ReactNode }) {
  return (
    <article aria-labelledby={`activity-${activity.id}`} className="mission-activity">
      <header className="mission-activity-heading">
        <div>
          <p className="mission-eyebrow">Current learning activity</p>
          <h2 id={`activity-${activity.id}`}>{activityTitle(activity.type)}</h2>
        </div>
        {activity.classification === 'SUPPLEMENTAL_GENERATED' ? (
          <span className="mission-context-label">Additional medical context</span>
        ) : null}
      </header>
      {children}
    </article>
  )
}

function ExplanationPresentation({ activity }: { activity: ExplanationActivity }) {
  if (activity.content === null) return <NeutralActivityState>Content is not available for this activity yet.</NeutralActivityState>
  const { concept, explanation, keyPoints, limitations } = activity.content
  return <div className="mission-reading"><p className="mission-concept">{concept}</p><p className="mission-explanation">{explanation}</p><ContentList heading="Key points" items={keyPoints} />{limitations.length > 0 ? <ContentList heading="Keep in mind" items={limitations} /> : null}</div>
}

function RetrievalPresentation({ activity }: { activity: RetrievalActivity }) {
  if (activity.content === null) return <NeutralActivityState>Content is not available for this activity yet.</NeutralActivityState>
  const { concept, question, options, limitations } = activity.content
  return <div><p className="mission-concept">{concept}</p><p className="mission-question">{question}</p><ol className="mission-options" aria-label="Answer options">{options.map((option) => <li key={option.id}>{option.text}</li>)}</ol>{limitations.length > 0 ? <ContentList heading="Keep in mind" items={limitations} /> : null}</div>
}

function ConnectionPresentation({ activity }: { activity: ConnectionActivity }) {
  if (activity.content === null) return <NeutralActivityState>Content is not available for this activity yet.</NeutralActivityState>
  const { fromConcept, toConcept, relationshipType, relationship, whyItMatters, question, limitations } = activity.content
  return <div><div className="mission-connection"><p><span>{fromConcept}</span><span aria-hidden="true">→</span><span>{toConcept}</span></p><p className="mission-relationship-type">{relationshipType}</p></div><p className="mission-explanation">{relationship}</p><section><h3>Why it matters</h3><p>{whyItMatters}</p></section><p className="mission-question">{question}</p>{limitations.length > 0 ? <ContentList heading="Keep in mind" items={limitations} /> : null}</div>
}

function ApplicationPresentation({ activity }: { activity: ApplicationActivity }) {
  if (activity.content === null) return <NeutralActivityState>Content is not available for this activity yet.</NeutralActivityState>
  const { scenario, question, targetConcept, limitations } = activity.content
  return <div><p className="mission-concept">Apply: {targetConcept}</p><section className="mission-scenario" aria-labelledby={`scenario-${activity.id}`}><h3 id={`scenario-${activity.id}`}>Scenario</h3><p>{scenario}</p></section><p className="mission-question">{question}</p>{limitations.length > 0 ? <ContentList heading="Keep in mind" items={limitations} /> : null}</div>
}

function ContentList({ heading, items }: { heading: string; items: string[] }) {
  return <section className="mission-content-section"><h3>{heading}</h3><ul>{items.map((item, index) => <li key={`${heading}-${index}`}>{item}</li>)}</ul></section>
}

function NeutralActivityState({ children }: { children: ReactNode }) {
  return <p className="mission-neutral-state">{children}</p>
}

function activityTitle(type: StudyMissionActivity['type']): string {
  switch (type) {
    case 'EXPLANATION': return 'Explanation'
    case 'RETRIEVAL': return 'Retrieval practice'
    case 'CONNECTION': return 'Learning connection'
    case 'APPLICATION': return 'Application'
    case 'VISUAL': return 'Visual activity'
    case 'FEEDBACK': return 'Feedback'
    case 'REFLECTION': return 'Reflection'
  }
  return assertNever(type)
}

function assertNever(value: never): never {
  throw new Error(`Unhandled activity type: ${String(value)}`)
}
