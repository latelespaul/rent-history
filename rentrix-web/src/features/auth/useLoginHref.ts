import { useLocation } from "react-router-dom"
import { loginPath } from "./paths"

/** `/login?next=<this page>` (D-119); on the sign-in page itself, the page as it is. */
export function useLoginHref(): string {
  const location = useLocation()
  const here = `${location.pathname}${location.search}`
  return location.pathname === "/login" ? here : loginPath(here)
}
