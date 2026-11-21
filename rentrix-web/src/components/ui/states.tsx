import type { ReactNode } from "react"
import { isApiError } from "@/api/errors"
import { cn } from "@/lib/utils"
import { Button } from "./button"
import { Icon, type IconName } from "./icon"

interface StateProps {
  icon?: IconName
  title: string
  description?: ReactNode
  /** Buttons or links under the text. */
  action?: ReactNode
  className?: string
}

const iconTile = "grid size-14 place-items-center rounded-2xl"

/**
 * Nothing to show yet: a list with no rows, a search with no match, a page that does not exist.
 * Calm, never an error colour; it says what is missing and, when there is one, what to do next.
 */
export function EmptyState({ icon = "search", title, description, action, className }: StateProps) {
  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-center gap-4 rounded-xl border border-dashed border-line-strong bg-surface px-6 py-12 text-center",
        className,
      )}
    >
      <span className={cn(iconTile, "bg-sunk text-ink-500")}>
        <Icon name={icon} size={24} />
      </span>
      <div className="max-w-md space-y-1">
        <p className="text-lg font-semibold text-ink-900">{title}</p>
        {description && <p className="text-sm text-ink-500">{description}</p>}
      </div>
      {action && <div className="flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  )
}

/** Plain words for a failure; the API's own detail when it sent one (D-60). */
export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.code === "network_error")
      return "Rentrix could not reach the server. Check your connection."
    if (error.status >= 500) return "The server had a problem. Please try again in a moment."
    return error.message
  }
  if (error instanceof Error && error.message) return error.message
  return "An unexpected error occurred."
}

interface ErrorStateProps extends Omit<StateProps, "title" | "description"> {
  title?: string
  /** The failure; its message is shown in plain words, with the trace id when the API sent one. */
  error?: unknown
  description?: ReactNode
  /** Shows "Try again" that calls this (e.g. a query's `refetch`). */
  onRetry?: () => void
}

/**
 * Something failed: a request, a page, a render. Red only for the icon (DESIGN.md §4.1), the text
 * stays readable, and "Try again" is the obvious next step when retrying can help.
 */
export function ErrorState({
  icon = "alert",
  title = "Something went wrong",
  error,
  description,
  onRetry,
  action,
  className,
}: ErrorStateProps) {
  const traceId = isApiError(error) ? error.traceId : undefined
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center gap-4 rounded-xl border border-line bg-surface px-6 py-12 text-center shadow-sm",
        className,
      )}
    >
      <span className={cn(iconTile, "bg-danger-100 text-danger-600")}>
        <Icon name={icon} size={24} />
      </span>
      <div className="max-w-md space-y-1">
        <p className="text-lg font-semibold text-ink-900">{title}</p>
        <p className="text-sm text-ink-500">{description ?? errorMessage(error)}</p>
        {traceId && <p className="font-mono text-xs text-ink-500">Reference: {traceId}</p>}
      </div>
      {(onRetry || action) && (
        <div className="flex flex-wrap justify-center gap-2">
          {onRetry && (
            <Button variant="outline" onClick={onRetry}>
              Try again
            </Button>
          )}
          {action}
        </div>
      )}
    </div>
  )
}
