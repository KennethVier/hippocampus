import { useQuery } from '@tanstack/react-query'

import { getMaterialProcessing, getMaterialStructure } from '../api/materialsApi'
import type { MaterialProcessing } from '../api/materialContracts'
import { materialKeys } from '../queries/materialQueries'

const POLLING_INTERVALS_MS = [5_000, 10_000, 20_000, 30_000] as const

export function processingPollingInterval(query: {
  state: { data?: MaterialProcessing; dataUpdateCount?: number; fetchFailureCount?: number }
}) {
  const readiness = query.state.data?.readiness
  if (readiness !== 'UPLOADED' && readiness !== 'PROCESSING') return false

  const attemptsAfterFirstLoad = Math.max(
    (query.state.dataUpdateCount ?? 1) + (query.state.fetchFailureCount ?? 0) - 1,
    0,
  )
  return POLLING_INTERVALS_MS[Math.min(attemptsAfterFirstLoad, POLLING_INTERVALS_MS.length - 1)]
}

export function useMaterialProcessing(materialId: string | null) {
  return useQuery({
    queryKey: materialKeys.processing(materialId ?? 'invalid'),
    queryFn: ({ signal }) => {
      if (!materialId || materialId === 'invalid') throw new Error('Missing material id')
      return getMaterialProcessing(materialId, signal)
    },
    enabled: materialId !== null && materialId !== 'invalid',
    refetchInterval: processingPollingInterval,
  })
}

export function useMaterialStructure(materialId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: materialKeys.structure(materialId ?? 'invalid'),
    queryFn: ({ signal }) => {
      if (!materialId || materialId === 'invalid') throw new Error('Missing material id')
      return getMaterialStructure(materialId, signal)
    },
    enabled: materialId !== null && materialId !== 'invalid' && enabled,
  })
}

export function materialProcessingSummary(processing: MaterialProcessing | undefined): string {
  if (!processing) return 'Processing status is being updated.'
  if (processing.readiness === 'PARTIALLY_READY') return 'Most of this material is ready. Some parts could not be fully processed.'
  if (processing.readiness === 'FAILED') return 'This material needs attention before it can be used.'
  if (processing.readiness === 'PROCESSING') return processing.progress === null
    ? 'Processing is underway.'
    : `Current step: ${Math.round(processing.progress)}%`
  if (processing.readiness === 'UPLOADED') return 'Uploaded and waiting to process.'
  if (processing.readiness === 'READY') return 'Ready to study.'
  return 'Processing status is being updated.'
}
