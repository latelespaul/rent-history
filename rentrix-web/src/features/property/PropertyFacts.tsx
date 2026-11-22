import { Icon, type IconName } from "@/components/ui/icon"
import { cn } from "@/lib/utils"
import type { PropertyCard } from "@/types/api"
import { bedroomsLabel, furnishingLabel, sqftLabel, typeLabel } from "./labels"

type Facts = Pick<
  PropertyCard,
  "propertyType" | "bedrooms" | "bathrooms" | "areaSqft" | "furnishing" | "parking"
>

/** The fact row of a card or page: only facts the API sent, each with its icon (DESIGN.md §5). */
export function PropertyFacts({
  property,
  detailed = false,
  className,
}: {
  property: Facts
  /** The property page spells out bathrooms and parking too. */
  detailed?: boolean
  className?: string
}) {
  const baths = property.bathrooms
  const facts: Array<[IconName, string | null]> = [
    ["building", typeLabel(property.propertyType)],
    ["bed", bedroomsLabel(property.bedrooms)],
    ["bath", detailed && baths ? `${baths} ${baths === 1 ? "bathroom" : "bathrooms"}` : null],
    ["area", sqftLabel(property.areaSqft)],
    ["sofa", furnishingLabel(property.furnishing)],
    ["car", property.parking ? "Parking" : null],
  ]
  return (
    <ul className={cn("flex flex-wrap gap-x-4 gap-y-1.5 text-sm text-ink-700", className)}>
      {facts
        .filter((fact): fact is [IconName, string] => fact[1] !== null)
        .map(([icon, text]) => (
          <li key={icon} className="inline-flex items-center gap-1.5">
            <Icon name={icon} size={16} className="text-ink-500" />
            {text}
          </li>
        ))}
    </ul>
  )
}
