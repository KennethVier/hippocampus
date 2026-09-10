export function displayMaterialStatus(status: string): string {
  switch (status) {
    case 'UPLOADED': return 'Uploaded'
    case 'PROCESSING': return 'Processing'
    case 'READY': return 'Ready'
    case 'PARTIALLY_READY': return 'Partially ready'
    case 'FAILED': return 'Needs attention'
    case 'UNSUPPORTED': return 'Unsupported'
    default: return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (value) => value.toUpperCase())
  }
}

export function displayProcessingStatus(status: string): string {
  return displayMaterialStatus(status)
}
