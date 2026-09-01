import { ApiError, apiClient } from '../../../api/apiClient'
import {
  subjectPageSchema,
  subjectSchema,
  topicPageSchema,
  topicSchema,
  type Subject,
  type SubjectPage,
  type Topic,
  type TopicPage,
} from './learningOrganizationContracts'
import { organizationRequest, subjectCreateBody, type SubjectInput, type TopicInput } from './learningOrganizationRequests'

function parse<T>(schema: { safeParse(value: unknown): { success: true; data: T } | { success: false } }, value: unknown): T {
  const result = schema.safeParse(value)
  if (result.success) return result.data
  throw new ApiError({ kind: 'invalid-response', status: 200, code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' })
}

function required(value: unknown): unknown {
  if (value !== undefined) return value
  throw new ApiError({ kind: 'invalid-response', status: 204, code: 'INVALID_RESPONSE', message: 'The server returned an invalid response.' })
}

export async function listSubjects(page: number, size: number, signal?: AbortSignal): Promise<SubjectPage> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.subjects(page, size), { signal })
  return parse(subjectPageSchema, required(value))
}

export async function getSubject(subjectId: string, signal?: AbortSignal): Promise<Subject> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.subject(subjectId), { signal })
  return parse(subjectSchema, required(value))
}

export async function createSubject(input: Omit<SubjectInput, 'sortOrder'>): Promise<Subject> {
  const value = await apiClient.requestJson<unknown>('/api/subjects', { method: 'POST', body: subjectCreateBody(input.name, input.description) })
  return parse(subjectSchema, required(value))
}

export async function updateSubject(subjectId: string, input: SubjectInput): Promise<Subject> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.subject(subjectId), { method: 'PUT', body: input })
  return parse(subjectSchema, required(value))
}

export async function archiveSubject(subjectId: string): Promise<Subject> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.subjectArchive(subjectId), { method: 'POST' })
  return parse(subjectSchema, required(value))
}

export async function listTopics(subjectId: string, page: number, size: number, signal?: AbortSignal): Promise<TopicPage> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.topics(subjectId, page, size), { signal })
  return parse(topicPageSchema, required(value))
}

export async function createTopic(subjectId: string, input: TopicInput): Promise<Topic> {
  const value = await apiClient.requestJson<unknown>(`/api/subjects/${encodeURIComponent(subjectId)}/topics`, { method: 'POST', body: input })
  return parse(topicSchema, required(value))
}

export async function updateTopic(topicId: string, input: TopicInput): Promise<Topic> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.topic(topicId), { method: 'PUT', body: input })
  return parse(topicSchema, required(value))
}

export async function archiveTopic(topicId: string): Promise<Topic> {
  const value = await apiClient.requestJson<unknown>(organizationRequest.topicArchive(topicId), { method: 'POST' })
  return parse(topicSchema, required(value))
}
