import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"
import { ShieldCheckIcon } from "./icons.gen"

/**
 * Pill badges per DESIGN.md §4.4/§4.5: 22 px tall, semibold 12 px, `600` text on its `100` tint
 * (contrast ≥ 4.5:1). Colour is never the only signal: every badge carries a word.
 */
const badgeVariants = cva(
  "inline-flex h-[22px] w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full px-2 text-xs font-semibold whitespace-nowrap [&>svg]:pointer-events-none [&>svg]:size-3.5",
  {
    variants: {
      variant: {
        neutral: "bg-sunk text-ink-700",
        verified: "bg-teal-50 text-teal-700",
        demo: "bg-warn-100 text-warn-600",
        dev: "bg-dev-100 text-dev-600",
        tenant: "bg-tenant-100 text-tenant-600",
        landlord: "bg-landlord-100 text-landlord-600",
        success: "bg-success-100 text-success-600",
        warn: "bg-warn-100 text-warn-600",
        danger: "bg-danger-100 text-danger-600",
        info: "bg-info-100 text-info-600",
        admin: "bg-ink-900 text-white",
      },
    },
    defaultVariants: {
      variant: "neutral",
    },
  },
)

function Badge({
  className,
  variant = "neutral",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

/**
 * "Demo data" on every seeded row and, for a demo session, in the top bar (D-68). The API marks
 * seed rows with `isDemo`; real rows never show it.
 */
function DemoBadge({ className, ...props }: Omit<React.ComponentProps<"span">, "children">) {
  return (
    <Badge variant="demo" className={className} {...props}>
      Demo data
    </Badge>
  )
}

/**
 * "ID verified": the public face of an `APPROVED` verification (DESIGN.md §4.5, D-91, D-95).
 * Shield-check in teal, the most repeated trust mark in the product.
 */
function VerifiedBadge({
  className,
  children = "ID verified",
  ...props
}: React.ComponentProps<"span">) {
  return (
    <Badge variant="verified" className={className} {...props}>
      <ShieldCheckIcon />
      {children}
    </Badge>
  )
}

export { Badge, badgeVariants, DemoBadge, VerifiedBadge }
