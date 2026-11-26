import * as React from "react"
import { Tabs as TabsPrimitive } from "radix-ui"
import { cn } from "@/lib/utils"

/** shadcn/ui Tabs on Radix: arrow keys move between tabs, each tab names its panel. */
function Tabs({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return (
    <TabsPrimitive.Root data-slot="tabs" className={cn("flex flex-col", className)} {...props} />
  )
}

/** A segmented control: a sunk pill track holding 44 px tall tabs (DESIGN.md §6 tap targets). */
function TabsList({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn("inline-flex items-center gap-1 rounded-full bg-sunk p-1", className)}
      {...props}
    />
  )
}

function TabsTrigger({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(
        "inline-flex min-h-11 flex-1 items-center justify-center gap-2 rounded-full px-4 text-[15px] font-semibold whitespace-nowrap text-ink-700 transition-colors duration-150 ease-rx focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-600 data-[state=active]:bg-surface data-[state=active]:text-ink-900 data-[state=active]:shadow-sm [&_svg]:shrink-0",
        className,
      )}
      {...props}
    />
  )
}

function TabsContent({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      data-slot="tabs-content"
      className={cn("outline-none", className)}
      {...props}
    />
  )
}

export { Tabs, TabsContent, TabsList, TabsTrigger }
