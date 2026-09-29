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
import { useFiltersStore } from "../store"

export function FlatFilters() {
  const filters = useFiltersStore((s) => s.filters)
  const setFilter = useFiltersStore((s) => s.setFilter)
  const resetFilters = useFiltersStore((s) => s.resetFilters)

  const hasAnyFilter =
    filters.city ||
    filters.minRent != null ||
    filters.maxRent != null ||
    filters.numberOfRooms != null ||
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
          value={filters.numberOfRooms != null ? String(filters.numberOfRooms) : "any"}
          onValueChange={(v) => setFilter("numberOfRooms", v === "any" ? undefined : Number(v))}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Any</SelectItem>
            <SelectItem value="1">1 BHK</SelectItem>
            <SelectItem value="2">2 BHK</SelectItem>
            <SelectItem value="3">3 BHK</SelectItem>
            <SelectItem value="4">4+ BHK</SelectItem>
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
