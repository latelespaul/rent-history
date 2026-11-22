import { Link, useLocation } from "react-router-dom"
import { ClockIcon } from "@/components/ui/icons.gen"
import { Container } from "./Container"

/**
 * "Verify your phone to post" (D-120, D-132): on every page while the signed-in user has no
 * verified phone, except inside the stepper and the welcome screens. The link brings the user back
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
          <b>Verify your phone to post.</b> What you post shows at once as &ldquo;Not verified
          yet&rdquo; until an admin reviews it.{" "}
          <Link to={`/verify/phone?next=${next}`} className="font-semibold text-inherit underline">
            Verify your phone
          </Link>
        </p>
      </Container>
    </div>
  )
}
