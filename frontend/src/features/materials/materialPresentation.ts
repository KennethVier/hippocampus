export function displayMaterialStatus(status: string): string {
  return status === 'UPLOADED'
    ? 'Uploaded'
    : status.replaceAll('_', ' ').toLowerCase().replace(/^./, (value) => value.toUpperCase())
}
