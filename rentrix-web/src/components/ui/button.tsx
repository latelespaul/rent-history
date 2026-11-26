import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"

/**
 * Buttons per DESIGN.md §4.4: 40 px tall, 10 px radius, semibold. One primary per area.
 * `default` is the primary (teal); `outline` is the secondary (strong border on surface).
 * `accent` (marigold, near-black text) is the one action we want pressed on a screen, and only
 * there: the landing's sign-up, Add home's "Write a review", the review submit and the invite's
 * WhatsApp (DESIGN.md §4.1).
 */
const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center gap-2 rounded-md border border-transparent text-sm font-semibold whitespace-nowrap no-underline transition-colors duration-(--rx-dur-fast) ease-rx outline-none select-none hover:no-underline disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:size-[18px] [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-teal-700",
        accent:
          "bg-cta text-cta-foreground hover:bg-cta-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink-900 focus-visible:outline-solid disabled:bg-cta-disabled disabled:text-cta-disabled-foreground disabled:opacity-100",
        outline: "border-line-strong bg-surface text-ink-900 hover:bg-sunk aria-expanded:bg-sunk",
        secondary: "border-line-strong bg-surface text-ink-900 hover:bg-sunk",
        ghost: "bg-transparent text-ink-700 hover:bg-sunk hover:text-ink-900 aria-expanded:bg-sunk",
        destructive: "bg-transparent text-danger-600 hover:bg-danger-100",
        link: "h-auto! px-0! text-primary underline-offset-4 hover:underline",
        dev: "border-dashed border-dev-600 bg-dev-100 text-dev-600 hover:bg-dev-100/70",
      },
      size: {
        default: "h-10 px-4",
        xs: "h-7 gap-1 rounded-sm px-2 text-xs [&_svg]:size-3.5",
        sm: "h-8 gap-1.5 px-3 text-xs [&_svg]:size-4",
        lg: "h-12 px-[22px] text-base [&_svg]:size-5",
        block: "h-10 w-full px-4",
        icon: "size-10",
        "icon-xs": "size-7 rounded-sm [&_svg]:size-3.5",
        "icon-sm": "size-8",
        "icon-lg": "size-12",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}
export { Button, buttonVariants }
