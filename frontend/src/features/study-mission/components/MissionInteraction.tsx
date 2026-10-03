import { useState, type FormEvent } from 'react'
import { Button, Textarea } from '../../../components/ui'
import type { ActivitySubmission, StudyMissionActivity } from '../api/studyMissionContracts'
import type { ActivityResponseInput } from '../api/studyMissionApi'

interface MissionInteractionProps {
  activity: StudyMissionActivity
  missionStatus: string
  submission: ActivitySubmission | null
  confirmedSubmission: boolean
  confirmedContinue: boolean
  isSubmitting: boolean
  isContinuing: boolean
  error: string | null
  onSubmit: (input: ActivityResponseInput) => void
  onContinue: () => void
  onRetryState: () => void
}

export function MissionInteraction(props: MissionInteractionProps) {
  const { activity } = props
  const [responseText, setResponseText] = useState('')
  const [selectedOption, setSelectedOption] = useState('')

  const completed = activity.status === 'COMPLETED'
  const canContinue = completed
    || (activity.type === 'EXPLANATION' && activity.content !== null)
    || (props.confirmedSubmission && props.submission?.continuationAvailable === true)
  const responseKind = !completed && !props.confirmedSubmission ? responseKindFor(activity) : null
  const interactionDisabled = props.missionStatus !== 'ACTIVE'

  function submit(event: FormEvent) {
    event.preventDefault()
    if (responseKind === 'choice' && selectedOption) props.onSubmit({ selectedOption })
    if (responseKind === 'text' && responseText.trim()) props.onSubmit({ responseText: responseText.trim() })
  }

  return (
    <section aria-labelledby={`interaction-${activity.id}`} className="mission-interaction">
      <h2 id={`interaction-${activity.id}`}>Your response</h2>
      {props.submission?.activityId === activity.id ? <SubmissionFeedback submission={props.submission} /> : null}
      {props.error ? <p className="mission-action-error" role="alert">{props.error}</p> : null}

      {responseKind ? (
        <form onSubmit={submit}>
          {responseKind === 'choice' && activity.type === 'RETRIEVAL' && activity.content ? (
            <fieldset disabled={props.isSubmitting || interactionDisabled}>
              <legend>Select one answer</legend>
              <div className="mission-choice-list">
                {activity.content.options.map((option) => (
                  <label key={option.id}>
                    <input
                      checked={selectedOption === option.id}
                      maxLength={256}
                      name={`answer-${activity.id}`}
                      onChange={() => setSelectedOption(option.id)}
                      type="radio"
                      value={option.id}
                    />
                    <span>{option.text}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          ) : (
            <Textarea
              disabled={props.isSubmitting || interactionDisabled}
              label="Response"
              maxLength={8000}
              onChange={(event) => setResponseText(event.target.value)}
              rows={6}
              value={responseText}
            />
          )}
          <Button
            disabled={props.isSubmitting || interactionDisabled || (responseKind === 'choice' ? !selectedOption : !responseText.trim())}
            type="submit"
          >
            {props.isSubmitting ? 'Submitting…' : 'Submit response'}
          </Button>
        </form>
      ) : null}

      {canContinue ? (
        props.confirmedContinue ? (
          <div className="mission-reload-state">
            <p>The interaction was saved. Reload the latest mission state to continue.</p>
            <Button onClick={props.onRetryState} variant="secondary">Reload mission state</Button>
          </div>
        ) : (
          <Button disabled={props.isContinuing || interactionDisabled} onClick={props.onContinue}>
            {props.isContinuing ? 'Continuing…' : 'Continue'}
          </Button>
        )
      ) : null}
    </section>
  )
}

function responseKindFor(activity: StudyMissionActivity): 'choice' | 'text' | null {
  if (activity.type === 'APPLICATION' && activity.content !== null) return 'text'
  if (activity.type !== 'RETRIEVAL' || activity.content === null) return null
  return activity.content.subtype === 'MCQ' && activity.content.options.length > 0 ? 'choice' : 'text'
}

function SubmissionFeedback({ submission }: { submission: ActivitySubmission }) {
  return (
    <div aria-live="polite" className="mission-feedback">
      <p className="mission-feedback-outcome">{displayLabel(submission.outcome)}</p>
      <p>{submission.feedback}</p>
      <FeedbackList heading="Correct concepts" items={submission.correctConcepts} />
      <FeedbackList heading="Concepts to revisit" items={submission.missingConcepts} />
      <FeedbackList heading="Misconceptions" items={submission.misconceptions} />
    </div>
  )
}

function FeedbackList({ heading, items }: { heading: string; items: string[] }) {
  if (items.length === 0) return null
  return <div><h3>{heading}</h3><ul>{items.map((item) => <li key={item}>{item}</li>)}</ul></div>
}

function displayLabel(value: string) {
  return value.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')
}
