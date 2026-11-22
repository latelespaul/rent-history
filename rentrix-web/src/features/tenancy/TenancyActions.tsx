import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { cancelTenancy, confirmTenancy, declineTenancy, endTenancy } from "@/api/tenancies"
import { Button } from "@/components/ui/button"
import { CheckIcon, XIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { Tenancy } from "@/types/api"
import { ActionError } from "./ApprovalPrompt"
import { today, wholeNumber } from "./labels"
import { useRefreshTenancyData } from "./useTenancies"

type Panel = null | "decline" | "end"

/**
 * The buttons a party may press now (`actions`, §6). Confirm and end are approval-gated on the
 * API; decline and cancel never are (D-35), so a pending account can always say no.
 */
export function TenancyActions({
  tenancy,
  compact = false,
}: {
  tenancy: Tenancy
  compact?: boolean
}) {
  const { actions } = tenancy
  const refresh = useRefreshTenancyData()
  const [panel, setPanel] = useState<Panel>(null)
  const [reason, setReason] = useState("")
  const [endDate, setEndDate] = useState(today())
  const [refund, setRefund] = useState("")

  const run = useMutation({
    mutationFn: (action: "confirm" | "decline" | "cancel" | "end") => {
      switch (action) {
        case "confirm":
          return confirmTenancy(tenancy.id)
        case "decline":
          return declineTenancy(tenancy.id, reason.trim() || undefined)
        case "cancel":
          return cancelTenancy(tenancy.id)
        case "end": {
          const depositRefundedInr = wholeNumber(refund)
          return endTenancy(tenancy.id, {
            endDate,
            ...(depositRefundedInr !== undefined ? { depositRefundedInr } : {}),
          })
        }
      }
    },
    onSuccess: () => {
      setPanel(null)
      void refresh()
    },
  })

  if (!actions.confirm && !actions.decline && !actions.cancel && !actions.end) return null
  const size = compact ? "sm" : "default"

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {actions.confirm && (
          <Button size={size} disabled={run.isPending} onClick={() => run.mutate("confirm")}>
            <CheckIcon />
            Confirm tenancy
          </Button>
        )}
        {actions.decline && (
          <Button
            size={size}
            variant="outline"
            disabled={run.isPending}
            aria-expanded={panel === "decline"}
            onClick={() => setPanel(panel === "decline" ? null : "decline")}
          >
            <XIcon />
            Decline
          </Button>
        )}
        {actions.cancel && (
          <Button
            size={size}
            variant="outline"
            disabled={run.isPending}
            onClick={() => run.mutate("cancel")}
          >
            Cancel request
          </Button>
        )}
        {actions.end && (
          <Button
            size={size}
            variant="ghost"
            disabled={run.isPending}
            aria-expanded={panel === "end"}
            onClick={() => setPanel(panel === "end" ? null : "end")}
          >
            End tenancy
          </Button>
        )}
      </div>

      {panel === "decline" && (
        <div className="space-y-2 rounded-lg border border-line p-4">
          <Label htmlFor={`decline-${tenancy.id}`}>
            Reason{" "}
            <span className="font-normal text-ink-500">(optional, shown to the other side)</span>
          </Label>
          <Textarea
            id={`decline-${tenancy.id}`}
            value={reason}
            maxLength={300}
            onChange={(e) => setReason(e.target.value)}
            placeholder="For example: I never rented this home."
          />
          <Button
            variant="destructive"
            disabled={run.isPending}
            onClick={() => run.mutate("decline")}
          >
            Decline request
          </Button>
        </div>
      )}

      {panel === "end" && (
        <div className="space-y-3 rounded-lg border border-line p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor={`end-date-${tenancy.id}`}>Last day</Label>
              <Input
                id={`end-date-${tenancy.id}`}
                type="date"
                value={endDate}
                max={today()}
                min={tenancy.startDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="h-10"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor={`refund-${tenancy.id}`}>
                Deposit refunded (₹) <span className="font-normal text-ink-500">(optional)</span>
              </Label>
              <Input
                id={`refund-${tenancy.id}`}
                inputMode="numeric"
                value={refund}
                onChange={(e) => setRefund(e.target.value)}
                className="h-10"
              />
            </div>
          </div>
          <p className="text-xs text-ink-500">Ending opens the review window for both sides.</p>
          <Button disabled={run.isPending} onClick={() => run.mutate("end")}>
            End tenancy
          </Button>
        </div>
      )}

      {run.isError && <ActionError error={run.error} />}
    </div>
  )
}
