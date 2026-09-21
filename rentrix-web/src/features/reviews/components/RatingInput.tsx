import { Star } from "lucide-react"
import { cn } from "@/lib/utils"

interface Props {
  value: number | undefined
  onChange: (value: number) => void
  disabled?: boolean
}

export function RatingInput({ value, onChange, disabled }: Props) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1">
        {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => {
          const active = value != null && n <= value
          return (
            <button
              key={n}
              type="button"
              disabled={disabled}
              onClick={() => onChange(n)}
              className={cn(
                "flex h-9 w-9 items-center justify-center rounded-md border text-sm font-medium transition-colors",
                active
                  ? "border-yellow-400 bg-yellow-50 text-yellow-700 dark:bg-yellow-950/40 dark:text-yellow-400"
                  : "border-input bg-background text-muted-foreground hover:bg-accent",
                disabled && "cursor-not-allowed opacity-50",
              )}
              aria-label={`Rate ${n} out of 10`}
            >
              {n}
            </button>
          )
        })}
      </div>
      {value != null && (
        <p className="flex items-center gap-1 text-sm text-muted-foreground">
          <Star className="h-3.5 w-3.5 fill-yellow-400 text-yellow-400" />
          {value} / 10
        </p>
      )}
    </div>
  )
}
