import { Link } from "react-router-dom"
import { MapPin, Star, BedDouble, Ruler } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import type { Flat } from "@/types"

function formatCurrency(n: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(n)
}

export function FlatCard({ flat }: { flat: Flat }) {
  return (
    <Card className="flex h-full flex-col overflow-hidden transition-shadow hover:shadow-md">
      <CardHeader className="space-y-1">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-base leading-tight">{flat.address}</CardTitle>
          {flat.isAvailable ? (
            <Badge variant="default" className="shrink-0">
              Available
            </Badge>
          ) : (
            <Badge variant="secondary" className="shrink-0">
              Occupied
            </Badge>
          )}
        </div>
        <CardDescription className="flex items-center gap-1 text-xs">
          <MapPin className="h-3 w-3" />
          {flat.city}, {flat.state}
        </CardDescription>
      </CardHeader>

      <CardContent className="flex-1 space-y-3">
        <p className="line-clamp-2 text-sm text-muted-foreground">{flat.description}</p>

        <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <BedDouble className="h-3 w-3" />
            {flat.numberOfRooms} BHK
          </span>
          <span className="flex items-center gap-1">
            <Ruler className="h-3 w-3" />
            {flat.area} m²
          </span>
          {flat.averageRating != null && (
            <span className="flex items-center gap-1">
              <Star className="h-3 w-3 fill-yellow-400 text-yellow-400" />
              {flat.averageRating.toFixed(1)}
              {flat.reviewCount != null && ` (${flat.reviewCount})`}
            </span>
          )}
        </div>
      </CardContent>

      <CardFooter className="flex items-center justify-between border-t pt-4">
        <span className="text-lg font-semibold">{formatCurrency(flat.rent)}</span>
        <Button asChild size="sm">
          <Link to={`/flats/${flat.id}`}>View details</Link>
        </Button>
      </CardFooter>
    </Card>
  )
}
