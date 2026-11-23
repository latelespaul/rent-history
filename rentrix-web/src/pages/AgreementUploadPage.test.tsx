// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { RentalAgreement, Tenancy } from "@/types/api"
import { makeUser } from "@/test/users"

// "Upload your rental agreement" (captain 2026-11-23 14:53; D-123 – D-131).
const TENANCY = {
  id: "t9",
  property: {
    id: "p9",
    buildingName: "Lakeside Habitat",
    locality: "Baner",
    city: { code: "PNQ", name: "Pune" },
    unitLabel: "12",
    propertyType: "APARTMENT",
  },
  myRole: "TENANT",
  landlord: { shortName: "Not on Doorway Living yet" },
  tenant: { id: "u1", shortName: "Asha R." },
  startDate: "2026-03-01",
  endDate: null,
  initialRentInr: 25000,
  status: "ACTIVE",
  initiatedBy: "TENANT",
  verificationStatus: "UNVERIFIED",
  verificationReason: null,
  agreement: null,
  actions: { confirm: false, decline: false, cancel: false, end: true, addRentRecord: true },
} as unknown as Tenancy

const STORED: RentalAgreement = {
  id: "a1",
  tenancyId: "t9",
  status: "DRAFT",
  tier: "UNVERIFIED",
  uploaderSide: "TENANT",
  uploadedByMe: true,
  anchorKind: "MH_LEAVE_LICENCE",
  fields: {
    landlordName: "Ravi Patil",
    tenantName: "Asha XXXX XXXX 0123",
    addressText: "Baner, Pune",
    pincode: "411045",
    monthlyRentInr: 25000,
    depositInr: null,
    startDate: "2026-03-01",
    endDate: null,
    anchorNumber: "4567/2025",
  },
  aadhaarMasked: true,
  contentType: "application/pdf",
  sizeBytes: 2048,
  sha256: "ab".repeat(32),
  fileAvailable: true,
  uploadedAt: "2026-11-23T10:00:00Z",
  confirmedAt: null,
  decidedAt: null,
  fileDeletesAfter: "2027-02-21T10:00:00Z",
  fileDeletedAt: null,
  tenancyVerification: "UNVERIFIED",
  decisionReason: null,
}

const api = vi.hoisted(() => ({ uploadError: null as unknown as Error | null }))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: makeUser() } }),
}))
vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchTenancy: vi.fn(async () => TENANCY),
}))
vi.mock("@/api/agreements", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/agreements")>()),
  fetchTenancyAgreement: vi.fn(async () => {
    throw new ApiError({ status: 404, code: "not_found", title: "Not Found" })
  }),
  uploadAgreement: vi.fn(async () => {
    if (api.uploadError) throw api.uploadError
    return STORED
  }),
  confirmAgreement: vi.fn(async () => ({ ...STORED, status: "SUBMITTED" })),
  fetchReviewTasks: vi.fn(async () => [{ tenancyId: "t9", direction: "TENANT_TO_PROPERTY" }]),
}))

const agreements = await import("@/api/agreements")
const { default: AgreementUploadPage } = await import("./AgreementUploadPage")

configure({ asyncUtilTimeout: 10_000 })
vi.setConfig({ testTimeout: 120_000 })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: "/tenancies/:id/agreement", element: <AgreementUploadPage /> },
      { path: "*", element: <p>elsewhere</p> },
    ],
    { initialEntries: ["/tenancies/t9/agreement?next=review"] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

const pdf = () => new File(["%PDF-1.4 test"], "agreement.pdf", { type: "application/pdf" })

async function fileStep(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole("heading", { name: /step 1 of 3 · the file/i })
  await user.upload(screen.getByLabelText("Agreement file"), pdf())
  await user.click(screen.getByTestId("agreement-consent"))
  await user.click(screen.getByTestId("agreement-next"))
  await screen.findByRole("heading", { name: /step 2 of 3 · its details/i })
}

async function typeDetails(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Owner's name"), "Ravi Patil")
  await user.type(screen.getByLabelText("Tenant's name"), "Asha 2345 6789 0123")
  await user.type(screen.getByLabelText("Monthly rent (₹)"), "25000")
  await user.type(screen.getByLabelText("Start date"), "2026-03-01")
  await user.type(screen.getByLabelText(/stamp or registration number/i), "4567/2025")
}

afterEach(() => {
  cleanup()
  api.uploadError = null
  vi.clearAllMocks()
})

describe("upload your rental agreement", () => {
  it("asks to cover Aadhaar numbers and for consent, then shows the masked fields to confirm", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("heading", { name: /step 1 of 3 · the file/i })
    expect(screen.getByTestId("cover-aadhaar").textContent).toMatch(/cover any aadhaar number/i)

    // Each missing thing is named next to its field, never as a generic banner.
    await user.click(screen.getByTestId("agreement-next"))
    expect(screen.getByTestId("ag-file-error").textContent).toMatch(/choose the agreement file/i)
    expect(screen.getByTestId("ag-consent-error").textContent).toMatch(/tick the box/i)
    expect(screen.queryByText(/invalid fields/i)).toBeNull()

    await user.upload(screen.getByLabelText("Agreement file"), pdf())
    await user.click(screen.getByTestId("agreement-consent"))
    await user.click(screen.getByTestId("agreement-next"))
    await screen.findByRole("heading", { name: /step 2 of 3 · its details/i })

    await user.click(screen.getByTestId("agreement-upload"))
    expect(screen.getByTestId("ag-rent-error").textContent).toMatch(/monthly rent/i)
    expect(screen.getByTestId("ag-landlord-error")).toBeTruthy()
    expect(agreements.uploadAgreement).not.toHaveBeenCalled()

    await typeDetails(user)
    await user.click(screen.getByTestId("agreement-upload"))
    expect(agreements.uploadAgreement).toHaveBeenCalledWith(
      expect.objectContaining({
        tenancyId: "t9",
        consent: true,
        fields: expect.objectContaining({
          landlordName: "Ravi Patil",
          monthlyRentInr: 25000,
          startDate: "2026-03-01",
          anchorNumber: "4567/2025",
        }),
      }),
    )

    const check = await screen.findByTestId("agreement-check")
    expect(within(check).getByTestId("check-tenant").textContent).toBe("Asha XXXX XXXX 0123")
    expect(within(check).getByTestId("aadhaar-masked")).toBeTruthy()
    await user.click(within(check).getByTestId("agreement-confirm"))

    const done = await screen.findByTestId("agreement-done")
    expect(within(done).getByTestId("agreement-status").textContent).toBe(
      "Agreement uploaded · awaiting team check",
    )
    expect((await within(done).findByTestId("agreement-next-review")).getAttribute("href")).toBe(
      "/tenancies/t9/review",
    )
    expect(agreements.confirmAgreement).toHaveBeenCalledWith("a1")
  })

  it("puts the API's field errors next to their fields, with no generic banner", async () => {
    api.uploadError = new ApiError({
      status: 422,
      code: "validation_failed",
      title: "Unprocessable Content",
      detail: "The request has invalid fields.",
      errors: [
        {
          field: "fields.tenantName",
          code: "not_blank",
          message: "Type the tenant's name as the agreement shows it.",
        },
      ],
    })
    renderPage()
    const user = userEvent.setup({ delay: null })
    await fileStep(user)
    await typeDetails(user)
    await user.click(screen.getByTestId("agreement-upload"))

    expect((await screen.findByTestId("ag-tenant-error")).textContent).toBe(
      "Type the tenant's name as the agreement shows it.",
    )
    expect(screen.queryByText(/invalid fields/i)).toBeNull()
  })

  it("names a refused file type plainly", async () => {
    api.uploadError = new ApiError({
      status: 422,
      code: "unsupported_file_type",
      title: "Unprocessable Content",
      detail: "Upload a PDF, or a JPEG, PNG or HEIC photo.",
      errors: [
        {
          field: "file",
          code: "unsupported_file_type",
          message: "Upload a PDF, or a JPEG, PNG or HEIC photo.",
        },
      ],
    })
    renderPage()
    const user = userEvent.setup({ delay: null })
    await fileStep(user)
    await typeDetails(user)
    await user.click(screen.getByTestId("agreement-upload"))
    expect((await screen.findByTestId("ag-file-error")).textContent).toMatch(
      /upload a pdf, or a jpeg, png or heic photo/i,
    )
  })
})
