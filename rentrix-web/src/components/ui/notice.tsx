import type { ComponentProps, ReactNode } from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"
import { Icon, type IconName } from "./icon"

const noticeVariants = cva(
  "flex items-start gap-2.5 rounded-md px-3.5 py-3 text-sm [&>svg]:mt-px",
  {
    variants: {
      tone: {
        info: "bg-info-100 text-[#1f4c7a]",
        teal: "bg-teal-50 text-teal-800",
        warn: "bg-warn-100 text-[#6f4608]",
        danger: "bg-danger-100 text-[#7a1f1f]",
        dev: "border border-dashed border-dev-600 bg-dev-100 text-[#4d2390]",
      },
    },
    defaultVariants: { tone: "info" },
  },
)

const TONE_ICON: Record<NonNullable<VariantProps<typeof noticeVariants>["tone"]>, IconName> = {
  info: "info",
  teal: "shield-check",
  warn: "alert",
  danger: "alert",
  dev: "code",
}

/**
 * A short plain-words explanation of access, rules or a problem (DESIGN.md §4.4 `.notice`).
 * Danger and warn notices are announced to screen readers.
 */
function Notice({
  tone = "info",
  icon,
  className,
  children,
  ...props
}: ComponentProps<"div"> &
  VariantProps<typeof noticeVariants> & { icon?: IconName; children: ReactNode }) {
  const t = tone ?? "info"
  return (
    <div
      role={t === "danger" || t === "warn" ? "alert" : undefined}
      data-tone={t}
      className={cn(noticeVariants({ tone: t }), className)}
      {...props}
    >
      <Icon name={icon ?? TONE_ICON[t]} size={18} />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}

export { Notice, noticeVariants }
