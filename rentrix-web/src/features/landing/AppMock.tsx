import { Badge } from "@/components/ui/badge"
import { CheckIcon, MenuIcon, ShieldCheckIcon, TenancyIcon } from "@/components/ui/icons.gen"
import { Stars } from "@/features/property/Rating"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"

/** Flat line-art buildings for a card's picture area; no photo until homes have photos (B9). */
export function Buildings({ tone, className }: { tone: string; className?: string }) {
  return (
    <svg
      viewBox="0 0 280 90"
      aria-hidden="true"
      className={cn("absolute inset-x-0 bottom-0 h-auto w-full", className)}
    >
      <rect x="20" y="22" width="70" height="68" rx="4" fill="#fff" opacity=".85" />
      <rect x="100" y="6" width="80" height="84" rx="4" fill="#fff" />
      <rect x="190" y="30" width="70" height="60" rx="4" fill="#fff" opacity=".85" />
      <g fill={tone}>
        {[112, 134, 156].flatMap((x) =>
          [18, 40].map((y) => <rect key={`${x}-${y}`} x={x} y={y} width="14" height="12" rx="2" />),
        )}
        <rect x="132" y="64" width="18" height="26" rx="2" />
        {[32, 56].flatMap((x) =>
          [34, 56].map((y) => (
            <rect key={`l${x}-${y}`} x={x} y={y} width="12" height="10" rx="2" />
          )),
        )}
        {[202, 226].flatMap((x) =>
          [42, 62].map((y) => (
            <rect key={`r${x}-${y}`} x={x} y={y} width="12" height="10" rx="2" />
          )),
        )}
      </g>
    </svg>
  )
}

function Bar({ label, value }: { label: string; value: number }) {
  return (
    <div className="grid grid-cols-[52px_1fr_22px] items-center gap-1.5">
      <span>{label}</span>
      <span className="h-[5px] overflow-hidden rounded-full bg-sunk">
        <span
          className="block h-full rounded-full bg-marigold-500"
          style={{ width: `${value * 20}%` }}
        />
      </span>
      <span className="num">{value.toFixed(1)}</span>
    </div>
  )
}

/**
 * The hero's picture: a phone showing a home's page as the app lays it out. Every value on it
 * is made up for the picture, so the screen says "Sample screen" and the caption says it is not
 * a real home (D-03). The rent line notes that rent shows only when both parties share it.
 */
export function AppMock() {
  return (
    <figure className="relative mx-auto grid w-fit place-items-center pb-7">
      <div
        aria-hidden="true"
        className="w-[286px] rounded-[40px] bg-ink-900 p-2.5 shadow-[0_30px_60px_rgb(20_34_41/0.25),0_8px_20px_rgb(20_34_41/0.12)] lg:w-[300px]"
      >
        <div className="relative h-[560px] overflow-hidden rounded-[31px] bg-paper text-[11px] text-ink-700 lg:h-[590px]">
          <span className="absolute top-2 left-1/2 h-[22px] w-[84px] -translate-x-1/2 rounded-full bg-ink-900" />
          <div className="flex h-[52px] items-center justify-between px-3.5 pt-7 font-display text-[13px] font-bold text-ink-900">
            <span className="flex items-center gap-1.5">
              <span className="grid size-5 place-items-center rounded-[6px] bg-teal-600 text-white">
                <TenancyIcon size={13} />
              </span>
              {BRAND.name}
            </span>
            <MenuIcon size={14} />
          </div>
          <div className="relative mx-3 mt-2.5 h-[118px] overflow-hidden rounded-[14px] bg-linear-160 from-[#d6ece8] to-[#9ccbc2]">
            <Badge variant="warn" className="absolute top-2 left-2 z-[1]">
              Sample screen
            </Badge>
            <Buildings tone="#9ccbc2" />
          </div>
          <div className="flex flex-col gap-2 px-3.5 py-2.5">
            <div>
              <p className="text-[15px] leading-tight font-semibold text-ink-900">
                2 BHK apartment
              </p>
              <p className="text-ink-500">A locality in your city · Semi-furnished</p>
            </div>
            <div className="flex items-center gap-1.5 text-xs">
              <span className="text-base font-semibold text-ink-900">4.6</span>
              <Stars value={4.6} />
              <span className="text-ink-500">from 3 tenancies</span>
            </div>
            <div className="grid gap-1.5 text-[10px]">
              <Bar label="Upkeep" value={4.6} />
              <Bar label="Water" value={4.4} />
              <Bar label="Landlord" value={4.8} />
            </div>
            <div className="grid gap-1 rounded-xl border border-line bg-surface px-2.5 py-2">
              <span className="text-[10px] text-ink-500">Rent paid, shared by both parties</span>
              <span className="num text-[13px] font-semibold text-ink-900">₹28,000 → ₹32,000</span>
              <svg viewBox="0 0 240 34" className="h-[34px] w-full" aria-hidden="true">
                <polyline
                  points="4,28 60,24 120,18 180,12 236,6"
                  fill="none"
                  stroke="var(--rx-teal-600)"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
                {[
                  [4, 28],
                  [60, 24],
                  [120, 18],
                  [180, 12],
                  [236, 6],
                ].map(([cx, cy]) => (
                  <circle key={cx} cx={cx} cy={cy} r="3" fill="var(--rx-teal-600)" />
                ))}
              </svg>
            </div>
            <div className="grid gap-1 rounded-xl border border-line bg-surface px-2.5 py-2 text-[10.5px]">
              <div className="flex items-center gap-1.5">
                <Badge variant="tenant" className="h-[18px] px-1.5 text-[9.5px]">
                  Tenant
                </Badge>
                rates the landlord
                <Stars value={5} className="ml-auto [&_svg]:size-[11px]" />
              </div>
              “Fixed the geyser within a week. The full deposit came back on time.”
              <Badge variant="success" className="h-[18px] px-1.5 text-[9.5px]">
                <CheckIcon />
                Verified by {BRAND.name}
              </Badge>
            </div>
          </div>
        </div>
      </div>
      <div
        aria-hidden="true"
        className="absolute top-[72px] -right-7 hidden w-[200px] gap-1 rounded-[14px] border border-line bg-surface px-3 py-2.5 text-xs text-ink-700 shadow-md lg:grid"
      >
        <span className="flex items-center gap-1.5">
          <Badge variant="landlord">Landlord</Badge>
          rates the tenant
        </span>
        <Stars value={5} />
        <span>“Paid rent on the 1st every month and left the flat clean.”</span>
      </div>
      <div
        aria-hidden="true"
        className="absolute bottom-2 -left-16 hidden w-[200px] gap-1 rounded-[14px] border border-line bg-surface px-3 py-2.5 text-xs text-ink-700 shadow-md lg:grid"
      >
        <span className="flex items-center gap-1.5 font-semibold text-ink-900">
          <span className="grid size-8 place-items-center rounded-[10px] bg-teal-50 text-teal-700">
            <ShieldCheckIcon size={18} />
          </span>
          Verified gets the tick
        </span>
        <span>Verify with any one of phone, Aadhaar or LinkedIn to get the green tick.</span>
      </div>
      <figcaption className="absolute bottom-0 text-xs text-ink-500">
        Sample screen, not a real home
      </figcaption>
    </figure>
  )
}
