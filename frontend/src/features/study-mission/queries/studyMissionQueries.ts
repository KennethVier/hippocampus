export const studyMissionKeys = {
  sources: (ownerId: string, topicId: string) => ['mission-start-sources', ownerId, topicId] as const,
  all: ['study-mission'] as const,
  detail: (missionId: string) => ['study-mission', missionId, 'continuationContract=1', 'initialActivityContract=1'] as const,
}
