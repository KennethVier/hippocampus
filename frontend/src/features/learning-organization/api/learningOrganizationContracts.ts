import { z } from 'zod'

const instantSchema = z.iso.datetime({ offset: true })
const lifecycleSchema = z.enum(['ACTIVE', 'ARCHIVED'])

export const subjectSchema = z.strictObject({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullable(),
  sortOrder: z.number().int().nullable(),
  status: lifecycleSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema,
})

export const topicSchema = z.strictObject({
  id: z.uuid(),
  subjectId: z.uuid(),
  name: z.string(),
  description: z.string().nullable(),
  status: lifecycleSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema,
})

const pageMetadata = {
  page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
}

export const subjectPageSchema = z.strictObject({ items: z.array(subjectSchema), ...pageMetadata })
export const topicPageSchema = z.strictObject({ items: z.array(topicSchema), ...pageMetadata })

export type Subject = z.infer<typeof subjectSchema>
export type Topic = z.infer<typeof topicSchema>
export type SubjectPage = z.infer<typeof subjectPageSchema>
export type TopicPage = z.infer<typeof topicPageSchema>
