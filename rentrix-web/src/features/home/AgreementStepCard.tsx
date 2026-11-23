import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ShieldCheckIcon } from "@/components/ui/icons.gen"
import type { Tenancy } from "@/types/api"
import { agreementDone } from "./steps"

/**
 * "Upload your rental agreement", one of the optional next steps after adding a home (captain
 * 2026-11-23 14:53). A placeholder until agreement upload exists (B9 storage, B18 intake): the
 * agreement-upload crew replaces this one component with the real action, so keep it the only
 * place that knows how an agreement is sent.
 */
export function AgreementStepCard({ tenancy }: { tenancy: Tenancy }) {
  if (agreementDone(tenancy)) return null
  return (
    <div
      data-testid="next-step-agreement"
      className="flex items-start gap-3 rounded-lg border border-line bg-surface p-4 text-left"
    >
      <ShieldCheckIcon size={20} className="mt-0.5 shrink-0 text-teal-700" />
      <div className="min-w-0 flex-1 space-y-1">
        <p className="flex flex-wrap items-center gap-2 font-semibold text-ink-900">
          Upload your rental agreement
          <Badge>Coming soon</Badge>
        </p>
        <p className="text-sm text-ink-700">
          It gets your tenancy the green tick once the team checks it. Optional.
        </p>
        <Button size="sm" variant="outline" disabled className="mt-1">
          Upload agreement
        </Button>
      </div>
    </div>
  )
}
