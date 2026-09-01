import { ApiError } from '../../api/apiClient'

export function uploadErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'The upload could not be completed. Try again.'
  switch (error.code) {
    case 'UPLOAD_TOO_LARGE': return 'This file exceeds the current upload limit.'
    case 'UPLOAD_FILE_REQUIRED': case 'UPLOAD_SINGLE_FILE_REQUIRED': return 'Choose one file to upload.'
    case 'UPLOAD_EMPTY': return 'Choose a file that is not empty.'
    case 'UPLOAD_TYPE_UNSUPPORTED': return 'Choose a PDF, JPEG, PNG, or plain-text file.'
    case 'NETWORK_ERROR': return 'The server could not be reached. Check your connection and try again.'
    case 'INVALID_RESPONSE': return 'The server returned an unexpected response. Try again.'
    default: return 'The upload could not be completed. Try again.'
  }
}
export function deleteErrorMessage(error: unknown): string {
  return error instanceof ApiError && error.code === 'MATERIAL_NOT_FOUND'
    ? 'This material is no longer available.' : 'The material could not be deleted. Try again.'
}
