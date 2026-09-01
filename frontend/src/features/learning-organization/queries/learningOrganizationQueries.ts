export const organizationKeys = {
  subjectLists: ['learning-organization', 'subjects'] as const,
  subjects: (page: number, size: number) => ['learning-organization', 'subjects', { page, size }] as const,
  subject: (subjectId: string) => ['learning-organization', 'subject', subjectId] as const,
  topicLists: (subjectId: string) => ['learning-organization', 'topics', subjectId] as const,
  topics: (subjectId: string, page: number, size: number) => ['learning-organization', 'topics', subjectId, { page, size }] as const,
}
