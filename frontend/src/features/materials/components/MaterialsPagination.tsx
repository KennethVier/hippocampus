import { Button } from '../../../components/ui'
export function MaterialsPagination({ page, totalPages, onPage }: { readonly page: number; readonly totalPages: number; readonly onPage: (page: number) => void }) {
  if (totalPages <= 1) return null
  return <nav aria-label="Materials pagination" className="materials-pagination">
    <Button disabled={page <= 1} onClick={() => onPage(page - 1)} variant="tertiary">Previous</Button>
    <span aria-live="polite">Page {page} of {totalPages}</span>
    <Button disabled={page >= totalPages} onClick={() => onPage(page + 1)} variant="tertiary">Next</Button>
  </nav>
}
