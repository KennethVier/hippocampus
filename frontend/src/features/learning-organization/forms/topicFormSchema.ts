import { z } from 'zod'

export const topicFormSchema = z.object({
  name: z.string().refine((value) => value.trim().length > 0, 'Enter a topic name.'),
  description: z.string(),
})

export type TopicFormValues = z.infer<typeof topicFormSchema>
