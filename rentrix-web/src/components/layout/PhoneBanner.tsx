import { Link, useLocation } from "react-router-dom"
import { ShieldCheckIcon } from "@/components/ui/icons.gen"
import { Container } from "./Container"

/**
 * "Verify to get a green tick" (D-120, D-144, D-163): an optional nudge on every page while the
 * signed-in user has no verified method (phone, masked Aadhaar or LinkedIn), except inside the
 * stepper and the welcome screens. Posting never waits for it (D-163). The link brings the user
 * back here afterwards (`?next=`, D-119).
 */
export function PhoneBanner() {
  const location = useLocation()
  if (location.pathname.startsWith("/verify") || location.pathname.startsWith("/welcome")) {
    return null
  }
  const next = encodeURIComponent(`${location.pathname}${location.search}`)
  return (
    <div
      role="region"
      aria-label="Verification"
      className="border-b border-line bg-teal-50 text-teal-800"
    >
      <Container className="flex items-start gap-2 py-2.5 text-sm">
        <ShieldCheckIcon size={18} className="mt-0.5 shrink-0" />
        <p>
          You can post now. What you post shows as &ldquo;Not verified yet&rdquo;. <b>Optional:</b>{" "}
          verify with any one of phone, Aadhaar or LinkedIn to get a green tick.{" "}
          <Link to={`/verify/phone?next=${next}`} className="font-semibold text-inherit underline">
            Verify to get a green tick
          </Link>
        </p>
      </Container>
    </div>
  )
}
