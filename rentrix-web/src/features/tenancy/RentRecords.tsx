import { useState, type FormEvent } from "react"
import { useMutation } from "@tanstack/react-query"
import {
  addRentRecord,
  confirmRentRecord,
  deleteRentRecord,
  disputeRentRecord,
  updateRentRecord,
} from "@/api/tenancies"
import { isApiError } from "@/api/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { PencilIcon, RupeeIcon, TrashIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { needsWarning, VERIFIED_EDIT_WARNING } from "@/features/edit/verified"
import { monthLabel, rupees } from "@/features/property/labels"
import type { RentRecord, RentRecordInput, Tenancy } from "@/types/api"
import { ActionError } from "./ApprovalPrompt"
import { RENT_RECORD_STATUS, rentChangeMonths, wholeNumber } from "./labels"
import { useRefreshTenancyData, useRentRecords } from "./useTenancies"

/** The month, rent and maintenance of a record its adder corrects (D-156). */
function EditRecord({
  record,
  tenancy,
  onDone,
  onCancel,
}: {
  record: RentRecord
  tenancy: Tenancy
  onDone: () => void
  onCancel: () => void
}) {
  const months = rentChangeMonths(tenancy)
  const [month, setMonth] = useState(record.effectiveFrom.slice(0, 7))
  const [rent, setRent] = useState(String(record.monthlyRentInr))
  const [maintenance, setMaintenance] = useState(
    record.maintenanceInr === null ? "" : String(record.maintenanceInr),
  )
  const [warn, setWarn] = useState(false)
  const verified = needsWarning(tenancy.verificationStatus)
  const save = useMutation({
    mutationFn: () => {
      const maintenanceInr = wholeNumber(maintenance)
      return updateRentRecord(record.id, {
        effectiveFrom: `${month}-01`,
        monthlyRentInr: wholeNumber(rent) ?? Number.NaN,
        ...(maintenanceInr !== undefined ? { maintenanceInr } : {}),
      })
    },
    onSuccess: onDone,
    onSettled: () => setWarn(false),
  })
  const fieldError = (field: string) =>
    isApiError(save.error) ? save.error.fieldError(field) : undefined
  const id = `rr-edit-${record.id}`
  return (
    <form
      noValidate
      aria-label="Edit rent record"
      className="space-y-3 rounded-lg border border-line bg-sunk/40 p-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (verified) setWarn(true)
        else save.mutate()
      }}
    >
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor={`${id}-month`}>From month</Label>
          <Input
            id={`${id}-month`}
            type="month"
            value={month}
            min={months.min}
            max={months.max}
            onChange={(e) => setMonth(e.target.value)}
            className="h-10"
          />
          {fieldError("effectiveFrom") && (
            <p className="text-xs text-danger-600">{fieldError("effectiveFrom")}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor={`${id}-rent`}>Monthly rent (₹)</Label>
          <Input
            id={`${id}-rent`}
            inputMode="numeric"
            value={rent}
            onChange={(e) => setRent(e.target.value)}
            className="h-10"
          />
          {fieldError("monthlyRentInr") && (
            <p className="text-xs text-danger-600">{fieldError("monthlyRentInr")}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor={`${id}-maintenance`}>
            Maintenance (₹) <span className="font-normal text-ink-500">(optional)</span>
          </Label>
          <Input
            id={`${id}-maintenance`}
            inputMode="numeric"
            value={maintenance}
            onChange={(e) => setMaintenance(e.target.value)}
            className="h-10"
          />
        </div>
      </div>
      {save.isError && !fieldError("effectiveFrom") && !fieldError("monthlyRentInr") && (
        <ActionError
          error={save.error}
          changeTarget={{ targetType: "TENANCY", targetId: tenancy.id }}
        />
      )}
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={save.isPending || !rent.trim()}>
          Save
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
      <ConfirmDialog
        open={warn}
        onOpenChange={setWarn}
        title="Save and remove the tick?"
        confirmLabel="Save"
        busy={save.isPending}
        onConfirm={() => save.mutate()}
      >
        <p>{VERIFIED_EDIT_WARNING}</p>
      </ConfirmDialog>
    </form>
  )
}

function RecordRow({
  record,
  tenancy,
  onDone,
}: {
  record: RentRecord
  tenancy: Tenancy
  onDone: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const respond = useMutation({
    mutationFn: (action: "confirm" | "dispute") =>
      action === "confirm" ? confirmRentRecord(record.id) : disputeRentRecord(record.id),
    onSuccess: onDone,
  })
  const remove = useMutation({
    mutationFn: () => deleteRentRecord(record.id),
    onSuccess: onDone,
    onSettled: () => setConfirmDelete(false),
  })
  const status = RENT_RECORD_STATUS[record.status]
  return (
    <li className="space-y-2 border-b border-line py-3 last:border-0">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <span className="min-w-28 font-semibold text-ink-900">
          From {monthLabel(record.effectiveFrom)}
        </span>
        <span className="num font-semibold">{rupees(record.monthlyRentInr)} / month</span>
        {record.maintenanceInr ? (
          <span className="num text-sm text-ink-500">
            + {rupees(record.maintenanceInr)} maintenance
          </span>
        ) : null}
        <Badge variant={status.variant}>{status.label}</Badge>
        <span className="text-xs text-ink-500">
          added by {record.addedByMe ? "you" : record.addedBy.shortName}
        </span>
        {record.canEdit && !editing && (
          <span className="ml-auto flex gap-1">
            <Button size="xs" variant="ghost" onClick={() => setEditing(true)}>
              <PencilIcon />
              Edit
            </Button>
            <Button
              size="xs"
              variant="destructive"
              disabled={remove.isPending}
              onClick={() => setConfirmDelete(true)}
            >
              <TrashIcon />
              Delete
            </Button>
          </span>
        )}
      </div>
      {editing && (
        <EditRecord
          record={record}
          tenancy={tenancy}
          onCancel={() => setEditing(false)}
          onDone={() => {
            setEditing(false)
            onDone()
          }}
        />
      )}
      {record.canRespond && (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" disabled={respond.isPending} onClick={() => respond.mutate("confirm")}>
            Confirm
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={respond.isPending}
            onClick={() => respond.mutate("dispute")}
          >
            Dispute
          </Button>
        </div>
      )}
      {respond.isError && <ActionError error={respond.error} />}
      {remove.isError && (
        <ActionError
          error={remove.error}
          changeTarget={{ targetType: "TENANCY", targetId: tenancy.id }}
        />
      )}
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete this rent change?"
        confirmLabel="Delete"
        danger
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        {needsWarning(tenancy.verificationStatus) && <p>{VERIFIED_EDIT_WARNING}</p>}
        <p>The rent from {monthLabel(record.effectiveFrom)} is removed. This cannot be undone.</p>
      </ConfirmDialog>
    </li>
  )
}

function AddRentRecord({ tenancy, onDone }: { tenancy: Tenancy; onDone: () => void }) {
  const tenancyId = tenancy.id
  const months = rentChangeMonths(tenancy)
  const [month, setMonth] = useState(months.initial)
  const [rent, setRent] = useState("")
  const [maintenance, setMaintenance] = useState("")
  const add = useMutation({
    mutationFn: (body: RentRecordInput) => addRentRecord(tenancyId, body),
    onSuccess: () => {
      setRent("")
      setMaintenance("")
      onDone()
    },
  })
  const fieldError = (field: string) =>
    isApiError(add.error) ? add.error.fieldError(field) : undefined
  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const monthlyRentInr = wholeNumber(rent) ?? Number.NaN
    const maintenanceInr = wholeNumber(maintenance)
    add.mutate({
      effectiveFrom: `${month}-01`,
      monthlyRentInr,
      ...(maintenanceInr !== undefined ? { maintenanceInr } : {}),
    })
  }

  return (
    <form
      noValidate
      onSubmit={onSubmit}
      aria-label="Add a rent change"
      className="space-y-3 rounded-lg border border-line bg-sunk/40 p-4"
    >
      <p className="font-semibold text-ink-900">Add a rent change</p>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="rr-month">From month</Label>
          <Input
            id="rr-month"
            type="month"
            value={month}
            min={months.min}
            max={months.max}
            onChange={(e) => setMonth(e.target.value)}
            className="h-10"
          />
          {fieldError("effectiveFrom") && (
            <p className="text-xs text-danger-600">{fieldError("effectiveFrom")}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="rr-rent">Monthly rent (₹)</Label>
          <Input
            id="rr-rent"
            inputMode="numeric"
            value={rent}
            onChange={(e) => setRent(e.target.value)}
            className="h-10"
          />
          {fieldError("monthlyRentInr") && (
            <p className="text-xs text-danger-600">{fieldError("monthlyRentInr")}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="rr-maintenance">
            Maintenance (₹) <span className="font-normal text-ink-500">(optional)</span>
          </Label>
          <Input
            id="rr-maintenance"
            inputMode="numeric"
            value={maintenance}
            onChange={(e) => setMaintenance(e.target.value)}
            className="h-10"
          />
        </div>
      </div>
      <p className="text-xs text-ink-500">
        The other side confirms it. Only confirmed rent enters the public rent history.
      </p>
      {add.isError && <ActionError error={add.error} />}
      <Button type="submit" disabled={add.isPending || !rent.trim()}>
        {add.isPending ? "Adding…" : "Add rent change"}
      </Button>
    </form>
  )
}

/** Rent records of one tenancy (D-25): each change is proposed by one side and confirmed by the other. */
export function RentRecords({ tenancy }: { tenancy: Tenancy }) {
  const records = useRentRecords(tenancy.id)
  const refresh = useRefreshTenancyData()
  const onDone = () => void refresh()

  let body
  if (records.isPending) body = <p className="text-sm text-ink-500">Loading rent records…</p>
  else if (records.isError)
    body = (
      <ErrorState
        title="Rent records could not be loaded"
        error={records.error}
        onRetry={() => void records.refetch()}
      />
    )
  else if (records.data.length === 0)
    body = (
      <EmptyState
        icon="rupee"
        title="No rent records yet"
        description="The starting rent is recorded when the tenancy is confirmed."
      />
    )
  else
    body = (
      <ul aria-label="Rent records">
        {records.data.map((record) => (
          <RecordRow key={record.id} record={record} tenancy={tenancy} onDone={onDone} />
        ))}
      </ul>
    )

  return (
    <section
      aria-labelledby="rent-records-heading"
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <h2
        id="rent-records-heading"
        className="inline-flex items-center gap-2 text-lg font-semibold"
      >
        <RupeeIcon size={20} />
        Rent records
      </h2>
      {body}
      {tenancy.actions.addRentRecord && <AddRentRecord tenancy={tenancy} onDone={onDone} />}
    </section>
  )
}
