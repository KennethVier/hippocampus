import { z } from 'zod'

export const subjectFormSchema = z.object({
  name: z.string().refine((value) => value.trim().length > 0, 'Enter a subject name.'),
  description: z.string(),
})

export type SubjectFormValues = z.infer<typeof subjectFormSchema>
