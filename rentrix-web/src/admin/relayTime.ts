/** "Expires in 7 min", "Expires in 40 s" or "Expired", from the code's expiry and the time now. */
export function expiryLabel(expiresAt: string, now: number): string {
  const seconds = Math.round((Date.parse(expiresAt) - now) / 1000)
  if (!Number.isFinite(seconds) || seconds <= 0) return "Expired"
  if (seconds < 60) return `Expires in ${seconds} s`
  return `Expires in ${Math.ceil(seconds / 60)} min`
}
