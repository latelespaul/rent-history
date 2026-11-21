import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"
import { ICON_PATHS, type IconName } from "./icon-paths.gen"

export type { IconName }

export type IconProps = Omit<ComponentProps<"svg">, "children" | "name"> & {
  /** Pixel size; the set is drawn for 16, 20 (default) and 24 (DESIGN.md §5). */
  size?: number
  /** Accessible name. Without one the icon is decorative and hidden from screen readers. */
  label?: string
}

/**
 * One icon of the Rentrix set: stroke 1.75, round caps and joins, `currentColor` only.
 * Decorative by default (`aria-hidden`); pass `label` when the icon carries meaning on its own.
 */
export function Icon({
  name,
  size = 20,
  label,
  className,
  ...props
}: IconProps & { name: IconName }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      data-icon={name}
      className={cn("rx-icon shrink-0", className)}
      {...(label ? { role: "img", "aria-label": label } : { "aria-hidden": true })}
      {...props}
    >
      {ICON_PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}

/** Binds a name, so `icons.gen.ts` can export `SearchIcon`, `ShieldCheckIcon`, … */
export function createIcon(name: IconName) {
  function NamedIcon(props: IconProps) {
    return <Icon name={name} {...props} />
  }
  NamedIcon.displayName = `Icon(${name})`
  return NamedIcon
}
