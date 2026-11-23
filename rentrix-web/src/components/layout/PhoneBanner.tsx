import { Link, useLocation } from "react-router-dom"
import { ClockIcon } from "@/components/ui/icons.gen"
import { Container } from "./Container"

/**
 * "Verify to post" (D-120, D-132, D-139): on every page while the signed-in user has no verified
 * method (phone, masked Aadhaar or LinkedIn), except inside the stepper and the welcome screens. The link brings the user back
 * here afterwards (`?next=`, D-119).
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
      className="border-b border-line bg-warn-100 text-[#6f4608]"
    >
      <Container className="flex items-start gap-2 py-2.5 text-sm">
        <ClockIcon size={18} className="mt-0.5 shrink-0" />
        <p>
          <b>Verify to post: any one of phone, Aadhaar or LinkedIn.</b> What you post shows at once
          as &ldquo;Not verified yet&rdquo; until our team checks it.{" "}
          <Link to={`/verify/phone?next=${next}`} className="font-semibold text-inherit underline">
            Verify now
          </Link>
        </p>
      </Container>
    </div>
  )
}
