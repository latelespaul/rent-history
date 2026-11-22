import type { IdentityView, StepStatus, VerificationEntry } from "@/types/api"

type Steps = VerificationEntry["steps"]

/** Test data only: one `GET /me/verification` entry. */
export function entry(
  overrides: Partial<Omit<VerificationEntry, "steps">> & { steps?: Partial<Steps> } = {},
): VerificationEntry {
  const steps: Steps = {
    profile: "DONE",
    phone: "MISSING",
    identity: "SKIPPED",
    linkedin: "SKIPPED",
    rentalAgreement: "SKIPPED",
    ...overrides.steps,
  }
  return { role: "TENANT", status: "DRAFT", revision: 1, ...overrides, steps }
}

/** Test data only: `GET /me/identity` with the phone in the given state. */
export function identity(
  phone: StepStatus | "VERIFIED" | null = null,
  available = true,
  truecaller = false,
): IdentityView {
  return {
    idVerified: false,
    linkedinVerified: false,
    methods: [
      { method: "AADHAAR", status: null, available: false },
      { method: "PAN", status: null, available: false },
      {
        method: "PHONE_OTP",
        status: phone === "VERIFIED" ? "VERIFIED" : null,
        masked: phone === "VERIFIED" ? "+91 XXXXXX3210" : null,
        available,
      },
      { method: "LINKEDIN", status: null, available: true },
      { method: "TRUECALLER", status: null, available: truecaller },
    ],
  }
}
