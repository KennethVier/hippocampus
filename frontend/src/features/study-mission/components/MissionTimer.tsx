interface MissionTimerProps {
  availableTimeMinutes: number | null
  status: string
}

export function MissionTimer({ availableTimeMinutes, status }: MissionTimerProps) {
  if (availableTimeMinutes === null) return null

  return (
    <div aria-label="Mission time" className="mission-timer">
      <span>Time available</span>
      <strong>{availableTimeMinutes} min</strong>
      {status === 'PAUSED' ? <small>Paused</small> : null}
    </div>
  )
}
