export interface SubjectInput {
  readonly name: string
  readonly description: string | null
  readonly sortOrder: number | null
}

export interface TopicInput {
  readonly name: string
  readonly description: string | null
}

export const organizationRequest = {
  subjects: (page: number, size: number) => `/api/subjects?page=${page}&size=${size}`,
  subject: (subjectId: string) => `/api/subjects/${encodeURIComponent(subjectId)}`,
  subjectArchive: (subjectId: string) => `/api/subjects/${encodeURIComponent(subjectId)}/archive`,
  topics: (subjectId: string, page: number, size: number) => `/api/subjects/${encodeURIComponent(subjectId)}/topics?page=${page}&size=${size}`,
  topic: (topicId: string) => `/api/topics/${encodeURIComponent(topicId)}`,
  topicArchive: (topicId: string) => `/api/topics/${encodeURIComponent(topicId)}/archive`,
} as const
