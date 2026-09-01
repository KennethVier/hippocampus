import { describe, expect, it } from 'vitest'
import { uploadReducer, type UploadState } from './uploadState'

const file = new File(['notes'], 'notes.txt', { type: 'text/plain' })
const other = new File(['image'], 'image.png', { type: 'image/png' })
const accepted = { materialId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301', versionId: '9a7b3302-b431-45e1-90e3-298c9d80918f', title: 'notes.txt', materialType: 'TEXT', originalFilename: 'notes.txt', mimeType: 'text/plain', fileSizeBytes: 5, materialStatus: 'UPLOADED', processingStatus: 'UPLOADED', createdAt: '2026-09-01T10:00:00Z' }

function selected(): UploadState { return uploadReducer({ type: 'idle' }, { type: 'select', file }) }
function active(): UploadState { return uploadReducer(selected(), { type: 'start', attempt: 1 }) }

describe('uploadReducer', () => {
  it('selects and replaces only while inactive', () => {
    expect(selected()).toEqual({ type: 'selected', file }); expect(uploadReducer(selected(), { type: 'select', file: other })).toEqual({ type: 'selected', file: other })
    expect(uploadReducer(active(), { type: 'select', file: other })).toEqual(active())
  })
  it('starts determinate and indeterminate transfer states with clamping', () => {
    expect(active()).toMatchObject({ type: 'uploading', progress: { type: 'indeterminate' } })
    expect(uploadReducer(active(), { type: 'progress', attempt: 1, progress: { type: 'determinate', percentage: -4 } })).toMatchObject({ progress: { percentage: 0 } })
    expect(uploadReducer(active(), { type: 'progress', attempt: 1, progress: { type: 'determinate', percentage: 37 } })).toMatchObject({ progress: { percentage: 37 } })
    expect(uploadReducer(active(), { type: 'progress', attempt: 1, progress: { type: 'indeterminate' } })).toMatchObject({ progress: { type: 'indeterminate' } })
  })
  it('treats 100 as awaiting acceptance, never acceptance', () => { expect(uploadReducer(active(), { type: 'progress', attempt: 1, progress: { type: 'determinate', percentage: 100 } })).toEqual({ type: 'awaiting-acceptance', file, attempt: 1 }) })
  it('accepts from uploading or awaiting only for the current attempt', () => {
    expect(uploadReducer(active(), { type: 'accept', attempt: 1, material: accepted })).toEqual({ type: 'accepted', file, material: accepted })
    const awaiting = uploadReducer(active(), { type: 'progress', attempt: 1, progress: { type: 'determinate', percentage: 100 } })
    expect(uploadReducer(awaiting, { type: 'accept', attempt: 1, material: accepted }).type).toBe('accepted')
    expect(uploadReducer(active(), { type: 'accept', attempt: 2, material: accepted })).toEqual(active())
  })
  it('fails, cancels, retries with a fresh attempt, and ignores stale events', () => {
    const failed = uploadReducer(active(), { type: 'fail', attempt: 1, error: { message: 'safe' } }); expect(failed).toEqual({ type: 'failed', file, error: { message: 'safe' } })
    expect(uploadReducer(failed, { type: 'start', attempt: 2 })).toMatchObject({ type: 'uploading', attempt: 2 })
    expect(uploadReducer(active(), { type: 'cancel', attempt: 1 })).toEqual({ type: 'canceled' })
    expect(uploadReducer(active(), { type: 'fail', attempt: 9, error: { message: 'stale' } })).toEqual(active())
    expect(uploadReducer({ type: 'idle' }, { type: 'accept', attempt: 1, material: accepted })).toEqual({ type: 'idle' })
  })
})
