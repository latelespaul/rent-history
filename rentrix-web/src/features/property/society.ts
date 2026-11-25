import type { PropertyCard, TowerView } from "@/types/api"
import { rupees } from "./labels"

/** "₹25,000 – ₹40,000 a month", or one amount when every home pays the same. */
export function rentRangeLabel(range: { min: number; max: number }): string {
  const amount =
    range.min === range.max ? rupees(range.min) : `${rupees(range.min)} – ${rupees(range.max)}`
  return `${amount} a month`
}

/** "1 flat", "3 flats". */
export function flatCount(n: number): string {
  return `${n} ${n === 1 ? "flat" : "flats"}`
}

/** Flats without a tower are listed last, under this heading. */
const NO_TOWER = "Other homes"

/** The society's flats grouped by tower, in the society's tower order; flats outside any tower last. */
export function flatsByTower(
  towers: readonly Pick<TowerView, "id" | "name">[],
  flats: readonly PropertyCard[],
): Array<{ key: string; name: string; flats: PropertyCard[] }> {
  const groups = towers.map((t) => ({ key: t.id, name: t.name, flats: [] as PropertyCard[] }))
  const rest: PropertyCard[] = []
  for (const flat of flats) {
    const group = groups.find((g) => g.key === flat.location?.tower?.id)
    if (group) group.flats.push(flat)
    else rest.push(flat)
  }
  const shown = groups.filter((g) => g.flats.length > 0)
  return rest.length ? [...shown, { key: "none", name: NO_TOWER, flats: rest }] : shown
}
