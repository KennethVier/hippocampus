export function displayMaterialStatus(status: string): string {
  switch (status) {
    case 'UPLOADED': return 'Uploaded'
    case 'PROCESSING': return 'Processing'
    case 'READY': return 'Ready'
    case 'PARTIALLY_READY': return 'Partially ready'
    case 'FAILED': return 'Needs attention'
    default: return 'Status unavailable'
  }
}

export function displayProcessingStatus(status: string): string {
  switch (status) {
    case 'UPLOADED': return 'Uploaded and waiting to process'
    case 'PROCESSING': return 'Processing'
    case 'READY': return 'Ready to study'
    case 'PARTIALLY_READY': return 'Ready with limitations'
    case 'FAILED': return 'Needs attention before study'
    default: return 'Processing status is being updated'
  }
}

export function displayProcessingStage(stage: string | null): string | null {
  switch (stage) {
    case 'VALIDATING': return 'Validating material'
    case 'EXTRACTING': return 'Extracting text'
    case 'STRUCTURE_DETECTION': return 'Detecting structure'
    case 'VISUAL_PROCESSING': return 'Processing visuals'
    case 'TEXT_NORMALIZATION': return 'Preparing text'
    case 'CHUNKING': return 'Preparing study sections'
    default: return null
  }
}
