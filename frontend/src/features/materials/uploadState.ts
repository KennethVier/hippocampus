import type { MaterialUpload } from './api/materialContracts'

export interface SafeUploadError { readonly message: string }
type Progress = { readonly type: 'determinate'; readonly percentage: number } | { readonly type: 'indeterminate' }
export type UploadState =
  | { readonly type: 'idle' }
  | { readonly type: 'selected'; readonly file: File }
  | { readonly type: 'uploading'; readonly file: File; readonly attempt: number; readonly progress: Progress }
  | { readonly type: 'awaiting-acceptance'; readonly file: File; readonly attempt: number }
  | { readonly type: 'accepted'; readonly file: File; readonly material: MaterialUpload }
  | { readonly type: 'failed'; readonly file: File; readonly error: SafeUploadError }
  | { readonly type: 'canceled' }

export type UploadAction =
  | { readonly type: 'select'; readonly file: File }
  | { readonly type: 'start'; readonly attempt: number }
  | { readonly type: 'progress'; readonly attempt: number; readonly progress: Progress }
  | { readonly type: 'accept'; readonly attempt: number; readonly material: MaterialUpload }
  | { readonly type: 'fail'; readonly attempt: number; readonly error: SafeUploadError }
  | { readonly type: 'cancel'; readonly attempt?: number }
  | { readonly type: 'reset' }

function isActive(state: UploadState, attempt: number): state is Extract<UploadState, { type: 'uploading' | 'awaiting-acceptance' }> {
  return (state.type === 'uploading' || state.type === 'awaiting-acceptance') && state.attempt === attempt
}
export function uploadReducer(state: UploadState, action: UploadAction): UploadState {
  switch (action.type) {
    case 'select': return state.type === 'uploading' || state.type === 'awaiting-acceptance' ? state : { type: 'selected', file: action.file }
    case 'start': return state.type === 'selected' || state.type === 'failed' ? { type: 'uploading', file: state.file, attempt: action.attempt, progress: { type: 'indeterminate' } } : state
    case 'progress':
      if (!isActive(state, action.attempt)) return state
      if (action.progress.type === 'determinate' && action.progress.percentage >= 100) return { type: 'awaiting-acceptance', file: state.file, attempt: action.attempt }
      return { type: 'uploading', file: state.file, attempt: action.attempt, progress: action.progress.type === 'determinate' ? { type: 'determinate', percentage: Math.min(99, Math.max(0, action.progress.percentage)) } : action.progress }
    case 'accept': return isActive(state, action.attempt) ? { type: 'accepted', file: state.file, material: action.material } : state
    case 'fail': return isActive(state, action.attempt) ? { type: 'failed', file: state.file, error: action.error } : state
    case 'cancel': return action.attempt === undefined || isActive(state, action.attempt) ? { type: 'canceled' } : state
    case 'reset': return { type: 'idle' }
  }
}
