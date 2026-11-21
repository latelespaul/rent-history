import { launchCopy } from "./cityText"
import { useCities } from "./useCities"

/** The launch line built from `GET /cities` (D-85); renders nothing until the list loads. */
export function LaunchCopy({ className }: { className?: string }) {
  const cities = useCities()
  if (!cities.isSuccess) return null
  return <span className={className}>{launchCopy(cities.data)}</span>
}
