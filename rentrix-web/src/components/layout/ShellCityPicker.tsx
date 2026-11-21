import { useLocation, useNavigate, useSearchParams } from "react-router-dom"
import { CityPicker } from "@/features/city/CityPicker"

/**
 * The C2 `CityPicker` as the shell's city control: it shows the `?city=` of the current page and
 * choosing a city opens Search for it (keeping Search's other filters). The list is whatever
 * `GET /cities` returns; the shell adds no city of its own (D-20, D-85).
 */
export function ShellCityPicker({
  className,
  onNavigate,
}: {
  className?: string
  onNavigate?: () => void
}) {
  const [params] = useSearchParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()

  function onChange(code: string) {
    const next = pathname === "/search" ? new URLSearchParams(params) : new URLSearchParams()
    next.set("city", code)
    navigate({ pathname: "/search", search: next.toString() })
    onNavigate?.()
  }

  return (
    <CityPicker
      value={params.get("city")}
      onChange={onChange}
      placeholder="Choose a city"
      className={className}
    />
  )
}
