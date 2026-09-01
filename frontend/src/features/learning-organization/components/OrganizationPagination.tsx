import { Button } from '../../../components/ui'

interface OrganizationPaginationProps {
  readonly page: number
  readonly totalPages: number
  readonly onPage: (page: number) => void
}

export function OrganizationPagination({ page, totalPages, onPage }: OrganizationPaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="organization-pagination">
      <Button disabled={page <= 1} onClick={() => onPage(page - 1)} variant="tertiary">Previous</Button>
      <span aria-live="polite">Page {page} of {totalPages}</span>
      <Button disabled={page >= totalPages} onClick={() => onPage(page + 1)} variant="tertiary">Next</Button>
    </nav>
  )
}
