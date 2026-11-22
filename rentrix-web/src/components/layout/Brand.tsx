import { Link } from "react-router-dom"
import { TenancyIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"

/** The brand mark: the tenancy icon in a teal tile, because Rentrix is about confirmed tenancies. */
export function Brand({ className }: { className?: string }) {
  return (
    <Link
      to="/"
      className={cn(
        "flex items-center gap-2 font-display text-xl font-bold text-ink-900 no-underline hover:no-underline",
        className,
      )}
    >
      <span className="grid size-[30px] place-items-center rounded-[9px] bg-teal-600 text-white">
        <TenancyIcon size={20} />
      </span>
      Rentrix
    </Link>
  )
}
