import type { Page, PropertyCard, PropertyDetail } from "@/types/api"

/** Test data only: mocked `GET /properties` rows shaped like the dev seed (fictional homes). */
export function card(overrides: Partial<PropertyCard> = {}): PropertyCard {
  return {
    id: "00000000-0000-0000-0000-000000000001",
    city: { code: "BLR", name: "Bangalore" },
    locality: "Koramangala",
    buildingName: "Demo Residency A",
    street: "Demo Road",
    propertyType: "APARTMENT",
    bedrooms: 2,
    bathrooms: 2,
    areaSqft: 1150,
    furnishing: "SEMI",
    parking: true,
    demo: true,
    rating: { reviewCount: 0, average: null },
    landlordLabel: "Owner",
    ...overrides,
  }
}

export function page<T>(items: T[], number = 0, totalItems = items.length, size = 10): Page<T> {
  return {
    items,
    page: { number, size, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / size)) },
  }
}

export function detail(overrides: Partial<PropertyDetail> = {}): PropertyDetail {
  return {
    id: "00000000-0000-0000-0000-000000000001",
    city: { code: "BLR", name: "Bangalore" },
    locality: "Koramangala",
    buildingName: "Demo Residency A",
    street: "Demo Road",
    pincode: "560034",
    propertyType: "APARTMENT",
    bedrooms: 2,
    bathrooms: 2,
    areaSqft: 1150,
    furnishing: "SEMI",
    parking: true,
    description: "Demo listing for local development. Not a real home.",
    status: "PUBLISHED",
    demo: true,
    rating: { reviewCount: 4, average: 4.3 },
    landlord: { label: "Owner", rating: { reviewCount: 2, average: null } },
    createdAt: "2026-11-21T00:00:00Z",
    updatedAt: "2026-11-21T00:00:00Z",
    ...overrides,
  }
}
