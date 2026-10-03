import { z } from 'zod'

const instant = z.iso.datetime({ offset: true })
const nonEmpty = z.string().trim().min(1)

export const missionSourceSchema = z.strictObject({
  sourceReferenceId: z.uuid(),
  materialTitle: nonEmpty,
  pageNumber: z.number().int().nullable(),
  displayLabel: z.string().nullable(),
})

const activityBaseSchema = z.strictObject({
  id: z.uuid(),
  status: nonEmpty,
  difficulty: nonEmpty.nullable(),
  classification: nonEmpty.nullable(),
  sources: z.array(missionSourceSchema),
})

const limitationsSchema = z.array(nonEmpty)

export const explanationActivitySchema = activityBaseSchema.extend({
  type: z.literal('EXPLANATION'),
  content: z.strictObject({
    concept: nonEmpty,
    explanation: nonEmpty,
    keyPoints: z.array(nonEmpty),
    limitations: limitationsSchema,
  }).nullable(),
})

export const retrievalActivitySchema = activityBaseSchema.extend({
  type: z.literal('RETRIEVAL'),
  content: z.strictObject({
    subtype: nonEmpty,
    concept: nonEmpty,
    question: nonEmpty,
    options: z.array(z.strictObject({ id: nonEmpty, text: nonEmpty })),
    difficulty: nonEmpty,
    limitations: limitationsSchema,
  }).nullable(),
})

export const connectionActivitySchema = activityBaseSchema.extend({
  type: z.literal('CONNECTION'),
  content: z.strictObject({
    fromConcept: nonEmpty,
    toConcept: nonEmpty,
    relationshipType: nonEmpty,
    relationship: nonEmpty,
    whyItMatters: nonEmpty,
    limitations: limitationsSchema,
  }).nullable(),
})

export const applicationActivitySchema = activityBaseSchema.extend({
  type: z.literal('APPLICATION'),
  content: z.strictObject({
    scenario: nonEmpty,
    question: nonEmpty,
    targetConcept: nonEmpty,
    difficulty: nonEmpty,
    limitations: limitationsSchema,
  }).nullable(),
})

export const visualActivitySchema = activityBaseSchema.extend({
  type: z.literal('VISUAL'),
  content: z.null(),
})

export const feedbackActivitySchema = activityBaseSchema.extend({
  type: z.literal('FEEDBACK'),
  content: z.null(),
})

export const reflectionActivitySchema = activityBaseSchema.extend({
  type: z.literal('REFLECTION'),
  content: z.null(),
})

export const studyMissionActivitySchema = z.discriminatedUnion('type', [
  explanationActivitySchema,
  retrievalActivitySchema,
  connectionActivitySchema,
  applicationActivitySchema,
  visualActivitySchema,
  feedbackActivitySchema,
  reflectionActivitySchema,
])

export const studyMissionSchema = z.strictObject({
  id: z.uuid(),
  status: nonEmpty,
  stage: nonEmpty.nullable(),
  currentActivity: studyMissionActivitySchema.nullable(),
  availableTimeMinutes: z.number().int().nonnegative().nullable(),
  startedAt: instant.nullable(),
  completedAt: instant.nullable(),
  stoppedAt: instant.nullable(),
  updatedAt: instant,
})

export const activitySubmissionSchema = z.strictObject({
  missionId: z.uuid(),
  activityId: z.uuid(),
  outcome: nonEmpty,
  correctConcepts: z.array(nonEmpty),
  missingConcepts: z.array(nonEmpty),
  misconceptions: z.array(nonEmpty),
  feedback: nonEmpty,
  missionStatus: nonEmpty,
  stage: nonEmpty,
  updatedAt: instant,
  continuationAvailable: z.boolean(),
})

const lifecycleSourceScopeSchema = z.strictObject({
  materialId: z.uuid(),
  materialVersionId: z.uuid(),
  documentNodeId: z.uuid().nullable(),
})

export const studyMissionLifecycleSchema = z.strictObject({
  id: z.uuid(),
  status: nonEmpty,
  currentActivityId: z.uuid().nullable(),
  sourceScopes: z.array(lifecycleSourceScopeSchema),
  startedAt: instant.nullable(),
  completedAt: instant.nullable(),
  stoppedAt: instant.nullable(),
  updatedAt: instant,
})

export type MissionSource = z.infer<typeof missionSourceSchema>
export type ExplanationActivity = z.infer<typeof explanationActivitySchema>
export type RetrievalActivity = z.infer<typeof retrievalActivitySchema>
export type ConnectionActivity = z.infer<typeof connectionActivitySchema>
export type ApplicationActivity = z.infer<typeof applicationActivitySchema>
export type VisualActivity = z.infer<typeof visualActivitySchema>
export type FeedbackActivity = z.infer<typeof feedbackActivitySchema>
export type ReflectionActivity = z.infer<typeof reflectionActivitySchema>
export type StudyMissionActivity = z.infer<typeof studyMissionActivitySchema>
export type StudyMission = z.infer<typeof studyMissionSchema>
export type ActivitySubmission = z.infer<typeof activitySubmissionSchema>
export type StudyMissionLifecycle = z.infer<typeof studyMissionLifecycleSchema>
