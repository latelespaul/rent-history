import { cn } from "@/lib/utils"
import { MODES, type Mode } from "./mode"

/** Tenant things in indigo, owner things in clay (DESIGN.md §1 "Two sides, two colours"). */
const ACTIVE = {
  TENANT: "bg-surface text-tenant-600 shadow-sm",
  OWNER: "bg-surface text-landlord-600 shadow-sm",
} as const

/**
 * The compact Tenant | Owner segmented switch of the header (PLAN R10). Each side is a toggle
 * button (`aria-pressed`), like the other segmented choices in the app.
 */
export function ModeSwitch({
  mode,
  onChange,
  className,
}: {
  mode: Mode
  onChange: (mode: Mode) => void
  className?: string
}) {
  return (
    <div
      role="group"
      aria-label="Mode"
      data-testid="mode-switch"
      data-mode={mode}
      className={cn("inline-flex rounded-full border border-line bg-sunk p-0.5", className)}
    >
      {MODES.map((m) => {
        const active = m.value === mode
        return (
          <button
            key={m.value}
            type="button"
            aria-pressed={active}
            data-testid={`mode-${m.value.toLowerCase()}`}
            onClick={() => {
              if (!active) onChange(m.value)
            }}
            className={cn(
              "h-8 min-w-[4.5rem] rounded-full px-3 text-sm font-semibold outline-none focus-visible:ring-2 focus-visible:ring-teal-600",
              active ? ACTIVE[m.value] : "text-ink-500 hover:text-ink-900",
            )}
          >
            {m.label}
          </button>
        )
      })}
    </div>
  )
}
