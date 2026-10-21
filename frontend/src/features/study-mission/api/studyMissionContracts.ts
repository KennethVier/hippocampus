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
    question: nonEmpty.nullable(),
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
  latestSubmission: z.lazy(() => activitySubmissionSchema).nullable().optional(),
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
  attemptId: z.uuid().nullable().optional(),
  limitationCode: nonEmpty.nullable().optional(),
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
export const continuationSchema = z.strictObject({
  actionType: z.enum(['UNDERSTAND', 'RETRIEVE', 'CONNECT', 'APPLY', 'FEEDBACK', 'REFLECT', 'HINT',
    'PREREQUISITE_SUPPORT', 'UNDERSTANDING_CHECK', 'RETRY', 'REDUCE_DIFFICULTY', 'REUSE_VALIDATED_CONTENT',
    'COMMUNICATE_LIMITATION', 'SOURCE_ONLY', 'RETRY_DEPENDENCY', 'PAUSE', 'COMPLETE']),
  rationaleCode: nonEmpty,
  canMaterialize: z.boolean(),
}).refine((view) => view.canMaterialize === ![
  'COMMUNICATE_LIMITATION', 'SOURCE_ONLY', 'RETRY_DEPENDENCY', 'PAUSE', 'COMPLETE',
].includes(view.actionType), { message: 'Inconsistent continuation action' })
export const studyMissionWithContinuationSchema = studyMissionSchema.extend({
  continuation: continuationSchema.nullable(),
})
export const continuationResultSchema = z.discriminatedUnion('outcome', [
  z.strictObject({ outcome: z.literal('ACTIVITY_CREATED'), activityId: z.uuid() }),
  z.strictObject({ outcome: z.literal('NO_ACTIVITY'),
    actionType: z.enum(['COMMUNICATE_LIMITATION', 'SOURCE_ONLY', 'RETRY_DEPENDENCY', 'PAUSE', 'COMPLETE']),
    rationaleCode: nonEmpty }),
])
export type MissionContinuation = z.infer<typeof continuationSchema>
export type StudyMission = z.infer<typeof studyMissionWithContinuationSchema> & { initialActivity?: InitialActivity | null }
export type ActivitySubmission = z.infer<typeof activitySubmissionSchema>
export type StudyMissionLifecycle = z.infer<typeof studyMissionLifecycleSchema>

const startUuid = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
  .refine((id) => id !== '00000000-0000-0000-0000-000000000000')
const startReason = z.enum(['DEPENDENCY_UNAVAILABLE', 'PREPARATION_TIMEOUT', 'INVALID_GENERATED_CONTENT',
  'SOURCE_UNAVAILABLE', 'SOURCE_BUSY', 'STATE_CHANGED', 'MISSION_PAUSED', 'MISSION_TERMINAL',
  'RETRY_LIMIT_REACHED', 'POLICY_DECISION_REQUIRED'])
const admissionReason = z.union([startReason, z.literal('INITIAL_COMPLETION_NOT_ADMISSIBLE')])
const initialControl = z.enum(['SOURCE_ONLY', 'COMMUNICATE_LIMITATION', 'RETRY_DEPENDENCY', 'PAUSE',
  'COMPLETE', 'STOP', 'START', 'RESUME'])
const retrySeconds = z.number().int().min(1).max(180).nullable()
export const initialActivitySchema = z.strictObject({
  startRequestId: startUuid,
  state: z.enum(['READY', 'CLAIMED', 'RETRYABLE', 'NO_ACTIVITY', 'ACTIVITY_CREATED', 'EXHAUSTED']),
  attemptsUsed: z.number().int().min(0).max(3), maximumAttempts: z.literal(3),
  canRetry: z.boolean(), retryAfterSeconds: retrySeconds, reasonCode: admissionReason.nullable(),
  decision: z.strictObject({ actionType: initialControl, rationaleCode: nonEmpty }).nullable(),
})
export const studyMissionWithInitialSchema = studyMissionWithContinuationSchema.extend({
  initialActivity: initialActivitySchema.nullable(),
})
export const startMissionRequestSchema = z.strictObject({
  startRequestId: startUuid, topicId: startUuid, learningObjectiveText: z.string().min(1).max(2000),
  availableTimeMinutes: z.number().int().min(1).max(240), groundingMode: z.literal('STRICT_SOURCE'),
  sourceScopes: z.array(z.strictObject({ materialId: startUuid })).min(1).max(8)
    .refine((scopes) => new Set(scopes.map((s) => s.materialId)).size === scopes.length),
})
const startResultBase = { missionId: startUuid, startRequestId: startUuid,
  missionStatus: z.enum(['ACTIVE', 'PAUSED', 'COMPLETED', 'STOPPED', 'FAILED', 'PLANNED']), replayed: z.boolean() }
export const startMissionResultSchema = z.discriminatedUnion('outcome', [
  z.strictObject({ ...startResultBase, outcome: z.literal('ACTIVITY_CREATED'), activityId: startUuid }),
  z.strictObject({ ...startResultBase, outcome: z.literal('PREPARING'), retryAfterSeconds: z.number().int().min(1).max(180) }),
  z.strictObject({ ...startResultBase, outcome: z.literal('RECOVERY_REQUIRED'), reasonCode: startReason,
    canRetry: z.boolean(), retryAfterSeconds: retrySeconds }),
  z.strictObject({ ...startResultBase, outcome: z.literal('NO_ACTIVITY'), actionType: initialControl,
    rationaleCode: nonEmpty, reasonCode: admissionReason.nullable(), canRetry: z.boolean(), retryAfterSeconds: retrySeconds }),
])
export const missionSourceSelectorSchema = z.strictObject({
  topic: z.strictObject({ id: startUuid, name: nonEmpty }),
  items: z.array(z.strictObject({ materialId: startUuid, title: nonEmpty, activeVersionId: startUuid,
    processingStatus: z.enum(['READY', 'PARTIALLY_READY']), readiness: z.enum(['READY', 'LIMITED']) })).max(50),
  nextAfterMaterialId: startUuid.nullable(),
})
export type InitialActivity = z.infer<typeof initialActivitySchema>
export type StartMissionRequest = z.infer<typeof startMissionRequestSchema>
export type StartMissionResult = z.infer<typeof startMissionResultSchema>
