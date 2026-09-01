import { describe, expect, it } from 'vitest'

import { subjectPageSchema, subjectSchema, topicPageSchema, topicSchema } from './learningOrganizationContracts'
import { organizationRequest, subjectCreateBody, subjectUpdateBody, topicBody } from './learningOrganizationRequests'

const subject = { id: '3f2504e0-4f89-41d3-9a0c-0305e82c3301', name: 'Anatomy', description: null, sortOrder: 7, status: 'ACTIVE', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z' }
const topic = { id: '9a7b3302-b431-45e1-90e3-298c9d80918f', subjectId: subject.id, name: 'Thorax', description: null, status: 'ACTIVE', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z' }

describe('learning organization contracts', () => {
  it('parses valid Subject, Topic, and page responses', () => {
    expect(subjectSchema.parse(subject).sortOrder).toBe(7)
    expect(topicSchema.parse(topic).subjectId).toBe(subject.id)
    expect(subjectPageSchema.parse({ items: [subject], page: 0, size: 12, totalElements: 1, totalPages: 1 }).items).toHaveLength(1)
    expect(topicPageSchema.parse({ items: [topic], page: 0, size: 12, totalElements: 1, totalPages: 1 }).items).toHaveLength(1)
  })

  it.each([
    [{ ...subject, id: 'not-a-uuid' }],
    [{ ...subject, status: 'DELETED' }],
    [{ ...subject, createdAt: 'yesterday' }],
    [{ ...subject, description: undefined }],
  ])('rejects malformed Subject responses', (value) => expect(subjectSchema.safeParse(value).success).toBe(false))

  it('rejects malformed page metadata and Topic nullability', () => {
    expect(subjectPageSchema.safeParse({ items: [], page: -1, size: 0, totalElements: -1, totalPages: -1 }).success).toBe(false)
    expect(topicSchema.safeParse({ ...topic, name: null }).success).toBe(false)
  })
})

describe('learning organization requests', () => {
  it('builds exact Subject and Topic paths', () => {
    expect(organizationRequest.subjects(1, 12)).toBe('/api/subjects?page=1&size=12')
    expect(organizationRequest.subject(subject.id)).toBe(`/api/subjects/${subject.id}`)
    expect(organizationRequest.topics(subject.id, 0, 12)).toBe(`/api/subjects/${subject.id}/topics?page=0&size=12`)
    expect(organizationRequest.topic(topic.id)).toBe(`/api/topics/${topic.id}`)
  })

  it('creates with null sortOrder and preserves the authoritative update value', () => {
    expect(subjectCreateBody('Anatomy', null)).toEqual({ name: 'Anatomy', description: null, sortOrder: null })
    expect(subjectUpdateBody('Anatomy II', null, 7)).toEqual({ name: 'Anatomy II', description: null, sortOrder: 7 })
  })

  it('builds Topic bodies without owner, parent, or lifecycle fields', () => {
    expect(topicBody('Thorax', null)).toEqual({ name: 'Thorax', description: null })
  })
})
