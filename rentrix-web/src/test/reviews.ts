import type { PropertyReview } from "@/types/api"

/**
 * Two reviews exactly as `GET /properties/{id}/reviews` returned them for the property's landlord
 * (dev seed, Demo Residency A): one already answered, one the landlord may still answer.
 */
export const RECORDED_REVIEWS: PropertyReview[] = [
  {
    id: "01a20329-e1a9-7741-a7f4-f4755cca22e6",
    direction: "TENANT_TO_PROPERTY",
    status: "PUBLISHED",
    ratingOverall: 5,
    ratingA: 5,
    ratingB: 4,
    ratingC: 5,
    body: "Well maintained building with a working lift and good security. Shops and buses are a short walk away.",
    author: {
      label: "Tenant, 2024",
      badges: {
        google: false,
        id: true,
      },
    },
    property: {
      id: "01a20329-dcd1-7a5a-8ab0-1c35736d3aaa",
      buildingName: "Demo Residency A",
      locality: "Koramangala",
      city: {
        code: "BLR",
        name: "Bangalore",
      },
    },
    period: {
      from: "2024-03",
    },
    publishedAt: "2024-06-09T10:00:00Z",
    response: {
      body: "Thank you for the feedback. The water pump was replaced after you moved out and the lift is now serviced every quarter.",
      authorLabel: "Owner",
      createdAt: "2024-06-11T10:00:00Z",
      editUntil: "2024-06-18T10:00:00Z",
    },
    actions: {
      edit: false,
      withdraw: false,
      respond: false,
      editResponse: false,
    },
  },
  {
    id: "01a20329-e1a9-7c07-8522-df79d571f05b",
    direction: "TENANT_TO_PROPERTY",
    status: "PUBLISHED",
    ratingOverall: 5,
    ratingA: 5,
    ratingB: 5,
    ratingC: 5,
    body: "Well maintained building with a working lift and good security. Shops and buses are a short walk away.",
    author: {
      label: "Tenant, 2024",
      badges: {
        google: false,
        id: true,
      },
    },
    property: {
      id: "01a20329-dcd1-7a5a-8ab0-1c35736d3aaa",
      buildingName: "Demo Residency A",
      locality: "Koramangala",
      city: {
        code: "BLR",
        name: "Bangalore",
      },
    },
    period: {
      from: "2022-06",
      to: "2024-01",
    },
    publishedAt: "2024-02-05T10:00:00Z",
    actions: {
      edit: false,
      withdraw: false,
      respond: true,
      editResponse: false,
    },
  },
]
