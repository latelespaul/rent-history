import { Badge, VerifiedBadge } from "@/components/ui/badge"

/**
 * A person's green tick for one role (D-134), next to their name on a property page: "Verified
 * tenant" or "Verified landlord" once our team approved them, else "<Role> not verified yet". It
 * names the role so it is not mistaken for the review's own tick beside it.
 */
export function PersonTick({
  verified,
  role,
}: {
  verified: boolean | undefined
  role: "tenant" | "landlord"
}) {
  return verified ? (
    <VerifiedBadge data-testid="person-verified">Verified {role}</VerifiedBadge>
  ) : (
    <Badge data-testid="person-unverified">
      {role === "tenant" ? "Tenant" : "Landlord"} not verified yet
    </Badge>
  )
}
