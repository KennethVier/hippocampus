export const materialKeys = {
  all: ['materials'] as const,
  lists: () => ['materials', 'list'] as const,
  list: (page: number, size: number) => ['materials', 'list', { page, size }] as const,
  details: () => ['materials', 'detail'] as const,
  detail: (id: string) => ['materials', 'detail', id] as const,
  processing: (id: string) => ['materials', 'processing', id] as const,
  structure: (id: string) => ['materials', 'structure', id] as const,
}
