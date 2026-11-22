import { Button } from "@/components/ui/button"
import { ChevronRightIcon } from "@/components/ui/icons.gen"

/** Previous / next over a D-59 page; `page` here is 1-based, as the URL shows it. */
export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pages" className="flex items-center justify-between gap-4">
      <Button variant="outline" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        <ChevronRightIcon className="rotate-180" />
        Previous
      </Button>
      <span className="text-sm text-ink-700">
        Page {page} of {totalPages}
      </span>
      <Button variant="outline" disabled={page >= totalPages} onClick={() => onPage(page + 1)}>
        Next
        <ChevronRightIcon />
      </Button>
    </nav>
  )
}
