import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { X } from "lucide-react"
import type { PropertyType } from "@/types"
import { useFiltersStore } from "../store"

const PROPERTY_TYPES: { value: PropertyType; label: string }[] = [
  { value: "APARTMENT", label: "Apartment" },
  { value: "INDEPENDENT_HOUSE", label: "Independent House" },
  { value: "PG", label: "PG" },
  { value: "ROOM", label: "Room" },
  { value: "STUDIO", label: "Studio" },
  { value: "VILLA", label: "Villa" },
]

export function FlatFilters() {
  const filters = useFiltersStore((s) => s.filters)
  const setFilter = useFiltersStore((s) => s.setFilter)
  const resetFilters = useFiltersStore((s) => s.resetFilters)

  const hasAnyFilter =
    filters.city ||
    filters.minRent != null ||
    filters.maxRent != null ||
    filters.minRooms != null ||
    filters.maxRooms != null ||
    filters.furnished != null ||
    filters.parking != null ||
    filters.propertyType != null ||
    filters.available != null

  return (
    <div className="space-y-4 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold">Filters</h2>
        {hasAnyFilter && (
          <Button variant="ghost" size="sm" onClick={resetFilters}>
            <X className="mr-1 h-3 w-3" /> Clear
          </Button>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="city">City</Label>
        <Input
          id="city"
          placeholder="e.g. Bangalore"
          value={filters.city ?? ""}
          onChange={(e) => setFilter("city", e.target.value || undefined)}
        />
      </div>

      <div className="grid grid-cols-2 gap-2">
        <div className="space-y-2">
          <Label htmlFor="minRent">Min rent</Label>
          <Input
            id="minRent"
            type="number"
            min={0}
            value={filters.minRent ?? ""}
            onChange={(e) =>
              setFilter("minRent", e.target.value ? Number(e.target.value) : undefined)
            }
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="maxRent">Max rent</Label>
          <Input
            id="maxRent"
            type="number"
            min={0}
            value={filters.maxRent ?? ""}
            onChange={(e) =>
              setFilter("maxRent", e.target.value ? Number(e.target.value) : undefined)
            }
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label>Rooms</Label>
        <Select
          value={filters.minRooms != null ? String(filters.minRooms) : "any"}
          onValueChange={(v) => {
            if (v === "any") {
              setFilter("minRooms", undefined)
              setFilter("maxRooms", undefined)
            } else {
              setFilter("minRooms", Number(v))
              setFilter("maxRooms", Number(v))
            }
          }}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            <SelectItem value="1">1 BHK</SelectItem>
            <SelectItem value="2">2 BHK</SelectItem>
            <SelectItem value="3">3 BHK</SelectItem>
            <SelectItem value="4">4 BHK</SelectItem>
            <SelectItem value="5">5+ BHK</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Property type</Label>
        <Select
          value={filters.propertyType ?? "any"}
          onValueChange={(v) =>
            setFilter("propertyType", v === "any" ? undefined : (v as PropertyType))
          }
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            {PROPERTY_TYPES.map((p) => (
              <SelectItem key={p.value} value={p.value}>
                {p.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Furnishing</Label>
        <Select
          value={filters.furnished == null ? "any" : filters.furnished ? "true" : "false"}
          onValueChange={(v) => setFilter("furnished", v === "any" ? undefined : v === "true")}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            <SelectItem value="true">Furnished</SelectItem>
            <SelectItem value="false">Unfurnished</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Parking</Label>
        <Select
          value={filters.parking == null ? "any" : filters.parking ? "true" : "false"}
          onValueChange={(v) => setFilter("parking", v === "any" ? undefined : v === "true")}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            <SelectItem value="true">Yes</SelectItem>
            <SelectItem value="false">No</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Availability</Label>
        <Select
          value={filters.available == null ? "any" : filters.available ? "true" : "false"}
          onValueChange={(v) => setFilter("available", v === "any" ? undefined : v === "true")}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            <SelectItem value="true">Available</SelectItem>
            <SelectItem value="false">Occupied</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  )
}
