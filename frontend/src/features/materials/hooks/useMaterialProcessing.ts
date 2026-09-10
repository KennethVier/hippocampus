import { useQuery } from '@tanstack/react-query'

import { getMaterialProcessing, getMaterialStructure } from '../api/materialsApi'
import type { MaterialProcessing } from '../api/materialContracts'

export function useMaterialProcessing(materialId: string | null) {
  return useQuery({
    queryKey: ['materials', 'processing', materialId],
    queryFn: ({ signal }) => {
      if (!materialId || materialId === 'invalid') throw new Error('Missing material id')
      return getMaterialProcessing(materialId, signal)
    },
    enabled: materialId !== null && materialId !== 'invalid',
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PROCESSING' ? 4000 : false
    },
  })
}

export function useMaterialStructure(materialId: string | null) {
  return useQuery({
    queryKey: ['materials', 'structure', materialId],
    queryFn: ({ signal }) => {
      if (!materialId || materialId === 'invalid') throw new Error('Missing material id')
      return getMaterialStructure(materialId, signal)
    },
    enabled: materialId !== null && materialId !== 'invalid',
  })
}

export function materialProcessingSummary(processing: MaterialProcessing | undefined): string {
  if (!processing) return 'Processing status is being updated.'
  if (processing.status === 'PARTIALLY_READY') return 'Most of this material is ready. Some pages or images could not be processed.'
  if (processing.status === 'FAILED') return 'This material needs attention before it can be used.'
  if (processing.status === 'PROCESSING') return `Processing ${Math.round(processing.progress)}%`
  if (processing.status === 'READY') return 'Ready to study.'
  return processing.limitation ?? 'Processing status is being updated.'
}