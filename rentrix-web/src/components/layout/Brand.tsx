import { Link } from "react-router-dom"
import { TenancyIcon } from "@/components/ui/icons.gen"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"

/**
 * The brand mark: the tenancy icon in a teal tile, because the product is about confirmed tenancies.
 * The wordmark never wraps or shrinks: the top bar gives way around it, so it stays on one line in
 * both modes, at 390 px and on desktop. Under 390 px the wordmark steps down one size so the whole
 * row still fits on smaller phones.
 */
export function Brand({ className }: { className?: string }) {
  return (
    <Link
      to="/"
      className={cn(
        "flex shrink-0 items-center gap-2 font-display text-xl font-bold whitespace-nowrap text-ink-900 no-underline hover:no-underline max-[389px]:text-lg",
        className,
      )}
    >
      <span className="grid size-[30px] shrink-0 place-items-center rounded-[9px] bg-teal-600 text-white">
        <TenancyIcon size={20} />
      </span>
      <span>{BRAND.name}</span>
    </Link>
  )
}
