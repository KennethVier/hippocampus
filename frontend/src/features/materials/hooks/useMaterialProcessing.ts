import { useQuery } from '@tanstack/react-query'

import { getMaterialProcessing, getMaterialStructure } from '../api/materialsApi'
import type { MaterialProcessing } from '../api/materialContracts'
import { materialKeys } from '../queries/materialQueries'

function processingPollingInterval(query: { state: { data?: MaterialProcessing; dataUpdateCount?: number } }) {
  const readiness = query.state.data?.readiness
  if (readiness !== 'UPLOADED' && readiness !== 'PROCESSING') return false

  const updates = Math.min(query.state.dataUpdateCount ?? 0, 5)
  return Math.min(2000 * 2 ** updates, 15000)
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

export function useMaterialStructure(materialId: string | null) {
  return useQuery({
    queryKey: materialKeys.structure(materialId ?? 'invalid'),
    queryFn: ({ signal }) => {
      if (!materialId || materialId === 'invalid') throw new Error('Missing material id')
      return getMaterialStructure(materialId, signal)
    },
    enabled: materialId !== null && materialId !== 'invalid',
  })
}

export function materialProcessingSummary(processing: MaterialProcessing | undefined): string {
  if (!processing) return 'Processing status is being updated.'
  if (processing.readiness === 'PARTIALLY_READY') return 'Most of this material is ready. Some pages or images could not be processed.'
  if (processing.readiness === 'FAILED') return 'This material needs attention before it can be used.'
  if (processing.readiness === 'PROCESSING') return `Processing ${Math.round(processing.progress ?? 0)}%`
  if (processing.readiness === 'READY') return 'Ready to study.'
  return processing.limitation ?? 'Processing status is being updated.'
}