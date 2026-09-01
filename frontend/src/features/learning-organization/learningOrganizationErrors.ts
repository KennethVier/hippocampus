import { ApiError } from '../../api/apiClient'

export function subjectMutationError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'SUBJECT_NAME_CONFLICT') return 'You already have a Subject with this name.'
  if (error instanceof ApiError && error.code === 'SUBJECT_NOT_FOUND') return 'This Subject is no longer available.'
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') return 'Review the form and try again.'
  return 'We could not save this change. Try again.'
}

export function topicMutationError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'SUBJECT_NOT_FOUND') return 'This Subject is no longer available.'
  if (error instanceof ApiError && error.code === 'TOPIC_NOT_FOUND') return 'This Topic is no longer available.'
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') return 'Review the form and try again.'
  return 'We could not save this change. Try again.'
}
