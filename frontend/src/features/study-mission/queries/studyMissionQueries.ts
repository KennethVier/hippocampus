export const studyMissionKeys = {
  all: ['study-mission'] as const,
  detail: (missionId: string) => ['study-mission', missionId] as const,
}
