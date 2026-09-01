import { z } from 'zod'

const instant = z.iso.datetime({ offset: true })
const nonEmpty = z.string().trim().min(1)

export const materialSchema = z.strictObject({
  id: z.uuid(), title: nonEmpty, materialType: nonEmpty,
  originalFilename: z.string().nullable(), mimeType: z.string().nullable(),
  status: nonEmpty, createdAt: instant, updatedAt: instant,
})

export const materialPageSchema = z.strictObject({
  items: z.array(materialSchema), page: z.number().int().nonnegative(),
  size: z.number().int().min(1).max(100), totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
})

export const materialUploadSchema = z.strictObject({
  materialId: z.uuid(), versionId: z.uuid(), title: nonEmpty, materialType: nonEmpty,
  originalFilename: z.string().nullable(), mimeType: nonEmpty,
  fileSizeBytes: z.number().int().nonnegative().safe(), materialStatus: nonEmpty,
  processingStatus: nonEmpty, createdAt: instant,
})

export type Material = z.infer<typeof materialSchema>
export type MaterialPage = z.infer<typeof materialPageSchema>
export type MaterialUpload = z.infer<typeof materialUploadSchema>
