import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

/** The 1120 px page column: 24 px side padding, 16 px under 860 px (DESIGN.md §4.3). */
export function Container({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("mx-auto w-full max-w-page px-4 nav:px-6", className)} {...props} />
}
