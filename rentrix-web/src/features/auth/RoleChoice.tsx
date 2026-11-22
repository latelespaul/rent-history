import { Badge } from "@/components/ui/badge"
import { Icon } from "@/components/ui/icon"
import { CheckIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"
import { ROLE_COPY, type PickableRole } from "./roles"

interface Props {
  role: PickableRole
  checked: boolean
  onChange: (checked: boolean) => void
}

/** One selectable role card of the role pick (onboarding mockup `.role`), a real checkbox inside. */
export function RoleChoice({ role, checked, onChange }: Props) {
  const copy = ROLE_COPY[role]
  const tenant = role === "TENANT"
  return (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3.5 rounded-lg p-[18px] has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-teal-600",
        checked ? "border-2 border-teal-600 bg-teal-50" : "border border-line-strong bg-surface",
      )}
    >
      <input
        type="checkbox"
        className="sr-only"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span
        aria-hidden
        className={cn(
          "mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-[5px] border-[1.5px]",
          checked ? "border-teal-600 bg-teal-600 text-white" : "border-line-strong bg-surface",
        )}
      >
        {checked && <CheckIcon size={14} />}
      </span>
      <span
        aria-hidden
        className={cn(
          "grid size-12 shrink-0 place-items-center rounded-[12px]",
          tenant ? "bg-tenant-100 text-tenant-600" : "bg-landlord-100 text-landlord-600",
        )}
      >
        <Icon name={tenant ? "tenant" : "landlord"} size={26} />
      </span>
      <span className="min-w-0">
        <span className="flex flex-wrap items-center gap-2">
          <b className="text-ink-900">{copy.title}</b>
          <Badge variant={tenant ? "tenant" : "landlord"}>{copy.badge}</Badge>
        </span>
        <span className="mt-1 block text-sm text-ink-500">{copy.text}</span>
      </span>
    </label>
  )
}
