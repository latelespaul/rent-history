import type { ReactNode } from "react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { HOME_SIZES } from "@/features/property/labels"
import type { HomeSize } from "@/types/api"

/**
 * The home size as chips (1 RK … 5+ BHK, Studio, Other with up to 40 characters of free text;
 * captain 2026-11-23 17:18). Tapping the picked chip again clears it. `idPrefix` names the inputs
 * (`<prefix>-home-size`, `<prefix>-size-other`), so a field error can focus them.
 */
export function HomeSizeChips({
  idPrefix,
  legend,
  value,
  other,
  onChange,
  onOtherChange,
  otherError,
  error,
}: {
  idPrefix: string
  legend: ReactNode
  value: HomeSize | ""
  other: string
  onChange: (size: HomeSize | "") => void
  onOtherChange: (text: string) => void
  otherError?: string
  error?: string
}) {
  const otherId = `${idPrefix}-size-other`
  return (
    <fieldset
      id={`${idPrefix}-home-size`}
      tabIndex={-1}
      className="space-y-2"
      data-testid={`${idPrefix}-home-size`}
    >
      <legend className="text-sm font-medium text-ink-900">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {HOME_SIZES.map((s) => (
          <button
            key={s.value}
            type="button"
            aria-pressed={value === s.value}
            onClick={() => onChange(value === s.value ? "" : s.value)}
            className={
              value === s.value
                ? "h-9 rounded-full border border-teal-600 bg-teal-50 px-3.5 text-sm font-semibold text-teal-800"
                : "h-9 rounded-full border border-line-strong bg-surface px-3.5 text-sm text-ink-700 hover:border-teal-600"
            }
          >
            {s.label}
          </button>
        ))}
      </div>
      {value === "OTHER" && (
        <div className="space-y-1.5">
          <Label htmlFor={otherId}>Size in your words</Label>
          <Input
            id={otherId}
            value={other}
            maxLength={40}
            placeholder="e.g. 2.5 BHK"
            onChange={(e) => onOtherChange(e.target.value)}
            className="h-11"
          />
          {otherError && (
            <p className="text-xs text-danger-600" data-testid={`${otherId}-error`}>
              {otherError}
            </p>
          )}
        </div>
      )}
      {error && (
        <p className="text-xs text-danger-600" role="alert">
          {error}
        </p>
      )}
    </fieldset>
  )
}
