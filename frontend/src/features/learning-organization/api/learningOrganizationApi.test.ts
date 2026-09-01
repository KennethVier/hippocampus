import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestJson = vi.hoisted(() => vi.fn())

vi.mock('../../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../api/apiClient')>()
  return { ...actual, apiClient: { ...actual.apiClient, requestJson } }
})

import { ApiError } from '../../../api/apiClient'
import {
  archiveSubject,
  archiveTopic,
  createSubject,
  createTopic,
  getSubject,
  listSubjects,
  listTopics,
  updateSubject,
  updateTopic,
} from './learningOrganizationApi'

const subject = { id: '3f2504e0-4f89-41d3-9a0c-0305e82c3301', name: 'Anatomy', description: null, sortOrder: 7, status: 'ACTIVE', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z' }
const topic = { id: '9a7b3302-b431-45e1-90e3-298c9d80918f', subjectId: subject.id, name: 'Thorax', description: null, status: 'ACTIVE', createdAt: subject.createdAt, updatedAt: subject.updatedAt }
const subjectPage = { items: [subject], page: 0, size: 12, totalElements: 1, totalPages: 1 }
const topicPage = { items: [topic], page: 0, size: 12, totalElements: 1, totalPages: 1 }

beforeEach(() => requestJson.mockReset())

describe('learning organization API boundary', () => {
  it('uses exact read paths and forwards abort signals', async () => {
    const signal = new AbortController().signal
    requestJson.mockResolvedValueOnce(subjectPage).mockResolvedValueOnce(subject).mockResolvedValueOnce(topicPage)

    await listSubjects(1, 12, signal)
    await getSubject(subject.id, signal)
    await listTopics(subject.id, 2, 12, signal)

    expect(requestJson).toHaveBeenNthCalledWith(1, '/api/subjects?page=1&size=12', { signal })
    expect(requestJson).toHaveBeenNthCalledWith(2, `/api/subjects/${subject.id}`, { signal })
    expect(requestJson).toHaveBeenNthCalledWith(3, `/api/subjects/${subject.id}/topics?page=2&size=12`, { signal })
  })

  it('serializes Subject mutations without owner or lifecycle authority', async () => {
    requestJson.mockResolvedValue(subject)

    await createSubject({ name: 'Anatomy', description: null })
    await updateSubject(subject.id, { name: 'Anatomy II', description: 'Core', sortOrder: 7 })
    await archiveSubject(subject.id)

    expect(requestJson).toHaveBeenNthCalledWith(1, '/api/subjects', {
      method: 'POST',
      body: { name: 'Anatomy', description: null, sortOrder: null },
    })
    expect(requestJson).toHaveBeenNthCalledWith(2, `/api/subjects/${subject.id}`, {
      method: 'PUT',
      body: { name: 'Anatomy II', description: 'Core', sortOrder: 7 },
    })
    expect(requestJson).toHaveBeenNthCalledWith(3, `/api/subjects/${subject.id}/archive`, { method: 'POST' })
  })

  it('serializes Topic mutations with parent and resource IDs only in approved paths', async () => {
    requestJson.mockResolvedValue(topic)

    await createTopic(subject.id, { name: 'Thorax', description: null })
    await updateTopic(topic.id, { name: 'Clinical Thorax', description: 'Chest' })
    await archiveTopic(topic.id)

    expect(requestJson).toHaveBeenNthCalledWith(1, `/api/subjects/${subject.id}/topics`, {
      method: 'POST',
      body: { name: 'Thorax', description: null },
    })
    expect(requestJson).toHaveBeenNthCalledWith(2, `/api/topics/${topic.id}`, {
      method: 'PUT',
      body: { name: 'Clinical Thorax', description: 'Chest' },
    })
    expect(requestJson).toHaveBeenNthCalledWith(3, `/api/topics/${topic.id}/archive`, { method: 'POST' })
  })

  it('rejects malformed successful payloads and unexpected empty responses safely', async () => {
    requestJson.mockResolvedValueOnce({ ...subject, id: 'not-a-uuid' }).mockResolvedValueOnce(undefined)

    await expect(getSubject(subject.id)).rejects.toMatchObject({ kind: 'invalid-response', status: 200, code: 'INVALID_RESPONSE' })
    await expect(getSubject(subject.id)).rejects.toMatchObject({ kind: 'invalid-response', status: 204, code: 'INVALID_RESPONSE' })
  })

  it('preserves normalized ApiError failures from the centralized client', async () => {
    const error = new ApiError({ kind: 'http', status: 404, code: 'SUBJECT_NOT_FOUND', message: 'hidden' })
    requestJson.mockRejectedValue(error)

    await expect(getSubject(subject.id)).rejects.toBe(error)
  })
})
