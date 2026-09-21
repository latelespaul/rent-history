import { Button } from "@/components/ui/button"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { useFiltersStore } from "../store"

interface Props {
  totalPages: number
  currentPage: number
}

export function FlatPagination({ totalPages, currentPage }: Props) {
  const setFilter = useFiltersStore((s) => s.setFilter)

  if (totalPages <= 1) return null

  return (
    <div className="flex items-center justify-center gap-2 py-6">
      <Button
        variant="outline"
        size="sm"
        disabled={currentPage === 0}
        onClick={() => setFilter("page", currentPage - 1)}
      >
        <ChevronLeft className="h-4 w-4" /> Prev
      </Button>
      <span className="text-sm text-muted-foreground">
        Page {currentPage + 1} of {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={currentPage >= totalPages - 1}
        onClick={() => setFilter("page", currentPage + 1)}
      >
        Next <ChevronRight className="h-4 w-4" />
      </Button>
    </div>
  )
}
