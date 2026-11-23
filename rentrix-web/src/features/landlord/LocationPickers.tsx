import { useState, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { fetchSocieties, fetchTowers, locationKeys, searchAreas } from "@/api/locations"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { MapPinIcon, PlusIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useSignedIn } from "@/features/property/useProperties"
import type { LocationStatus, SocietyKind, TowerKind } from "@/types/api"
import { setLevel, type LocationDraft } from "./locationDraft"

const selectClass =
  "h-10 w-full rounded-md border border-line-strong bg-surface px-3 text-sm disabled:opacity-50"
const NEW = "__new"

/** Only the fields of a picked row: a tower's own `kind` must not replace the level's. */
const picked = (o: { id: string; name: string; status: LocationStatus }) =>
  ({ kind: "pick", id: o.id, name: o.name, status: o.status }) as const

function PendingBadge({ status }: { status?: LocationStatus | "NEW" }) {
  if (status !== "PENDING" && status !== "NEW") return null
  return <Badge variant="warn">Pending review</Badge>
}

interface Props {
  city: string
  value: LocationDraft
  onChange: (next: LocationDraft) => void
  /** Locked after submit (D-92): shown read-only. */
  disabled?: boolean
  /** A field error from the API, e.g. on `location.areaId`. */
  error?: string
  /** Errors on the society, tower and wing levels, from the form's checks or the API. */
  levelErrors?: Partial<Record<"society" | "tower" | "wing", string>>
  /** The line under the area; the landlord form's default says its listing is checked first. */
  hint?: string
}

function LevelError({ id, error }: { id: string; error?: string }) {
  if (!error) return null
  return (
    <p className="text-xs text-danger-600" data-testid={`${id}-error`} role="alert">
      {error}
    </p>
  )
}

function AreaPicker({ city, value, onChange, disabled }: Props) {
  const { viewer } = useSignedIn()
  const [text, setText] = useState("")
  const areas = useQuery({
    queryKey: locationKeys.areas(city, text.trim(), viewer),
    queryFn: ({ signal }) => searchAreas(city, text, signal),
    enabled: Boolean(city) && value.area.kind === "none" && !disabled,
    staleTime: 30_000,
  })
  const { area } = value

  if (area.kind !== "none") {
    return (
      <div className="flex flex-wrap items-center gap-2 rounded-md border border-line-strong bg-surface px-3 py-2">
        <MapPinIcon size={16} className="text-ink-500" />
        <span className="font-semibold text-ink-900">{area.name}</span>
        <PendingBadge status={area.kind === "new" ? "NEW" : area.status} />
        {!disabled && (
          <Button
            type="button"
            size="xs"
            variant="ghost"
            className="ml-auto"
            onClick={() => onChange(setLevel(value, "area", { kind: "none" }))}
          >
            Change area
          </Button>
        )}
      </div>
    )
  }

  const typed = text.trim()
  const exact = areas.data?.some((a) => a.name.toLowerCase() === typed.toLowerCase())
  return (
    <div className="space-y-2">
      <Input
        id="pf-area"
        value={text}
        disabled={disabled || !city}
        placeholder={city ? "Type an area name or a 6-digit PIN code" : "Pick a city first"}
        onChange={(e) => setText(e.target.value)}
        className="h-10"
        aria-describedby="pf-area-hint"
      />
      {city && (
        <ul aria-label="Areas" className="flex flex-wrap gap-2">
          {areas.data?.map((a) => (
            <li key={a.id}>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() =>
                  onChange(
                    setLevel(value, "area", {
                      kind: "pick",
                      id: a.id,
                      name: a.name,
                      status: a.status,
                      pincodes: a.pincodes,
                    }),
                  )
                }
              >
                {a.name}
                <span className="font-normal text-ink-500">
                  {a.pincodes.slice(0, 2).join(", ")}
                </span>
                {a.status === "PENDING" && <span className="text-warn-600">· Pending</span>}
              </Button>
            </li>
          ))}
          {typed.length > 0 && areas.isSuccess && areas.data.length === 0 && (
            <li className="text-sm text-ink-500">No area matches “{typed}”.</li>
          )}
          {/* Only once the search answered: a tap before that would propose an area that exists. */}
          {typed.length >= 2 &&
            !/^\d+$/.test(typed) &&
            areas.isSuccess &&
            !areas.isFetching &&
            !exact && (
              <li>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() =>
                    onChange(setLevel(value, "area", { kind: "new", name: typed, extra: {} }))
                  }
                >
                  <PlusIcon />
                  Suggest new area “{typed}”
                </Button>
              </li>
            )}
        </ul>
      )}
    </div>
  )
}

/** A select of existing rows plus "Add …", with the proposal's name field under it. */
function LevelSelect({
  id,
  label,
  noneLabel,
  addLabel,
  options,
  current,
  onPick,
  onNone,
  onNew,
  disabled,
  children,
}: {
  id: string
  label: string
  noneLabel: string
  addLabel: string
  options: Array<{ id: string; name: string; status: LocationStatus }>
  current: { kind: "none" } | { kind: "pick"; id: string } | { kind: "new" }
  onPick: (option: { id: string; name: string; status: LocationStatus }) => void
  onNone: () => void
  onNew: () => void
  disabled?: boolean
  children?: ReactNode
}) {
  const selected = current.kind === "pick" ? current.id : current.kind === "new" ? NEW : ""
  const picked = options.find((o) => o.id === selected)
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="flex items-center gap-2">
        {label}
        <PendingBadge status={current.kind === "new" ? "NEW" : picked?.status} />
      </Label>
      <select
        id={id}
        className={selectClass}
        value={selected}
        disabled={disabled}
        onChange={(e) => {
          const v = e.target.value
          if (v === "") onNone()
          else if (v === NEW) onNew()
          else {
            const option = options.find((o) => o.id === v)
            if (option) onPick(option)
          }
        }}
      >
        <option value="">{noneLabel}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
            {o.status === "PENDING" ? " (pending review)" : ""}
          </option>
        ))}
        <option value={NEW}>{addLabel}</option>
      </select>
      {children}
    </div>
  )
}

/**
 * Where the home is (D-107–D-110): area by name or PIN code, then optionally society → tower →
 * wing. Each level is an existing row or a proposal that admins review ("Pending review"). A
 * change at one level clears the levels under it.
 */
export function LocationPickers(props: Props) {
  const {
    city,
    value,
    onChange,
    disabled,
    error,
    levelErrors = {},
    hint = "Pick the area. If it is missing, suggest it: an admin checks new names with your listing before it goes public.",
  } = props
  const { viewer } = useSignedIn()
  const { area, society, tower, wing } = value

  const societies = useQuery({
    queryKey: locationKeys.societies(city, area.kind === "pick" ? area.id : "", viewer),
    queryFn: ({ signal }) => fetchSocieties(city, area.kind === "pick" ? area.id : "", signal),
    enabled: area.kind === "pick",
    staleTime: 30_000,
  })
  const towers = useQuery({
    queryKey: locationKeys.towers(society.kind === "pick" ? society.id : "", viewer),
    queryFn: ({ signal }) => fetchTowers(society.kind === "pick" ? society.id : "", signal),
    enabled: society.kind === "pick",
    staleTime: 30_000,
  })
  const wings =
    tower.kind === "pick" ? (towers.data?.find((t) => t.id === tower.id)?.wings ?? []) : []

  return (
    <fieldset className="space-y-4" disabled={disabled}>
      <legend className="sr-only">Location</legend>
      <div className="space-y-1.5">
        <Label htmlFor="pf-area">Area</Label>
        <AreaPicker {...props} />
        <p
          id="pf-area-hint"
          className={`text-xs ${error ? "text-danger-600" : "text-ink-500"}`}
          data-testid={error ? "pf-area-error" : undefined}
        >
          {error ?? hint}
        </p>
      </div>

      {area.kind !== "none" && (
        <LevelSelect
          id="pf-society"
          label="Society or apartment complex"
          noneLabel="Standalone home (not in a society)"
          addLabel="+ Add a society…"
          options={societies.data ?? []}
          current={society}
          disabled={disabled}
          onNone={() => onChange(setLevel(value, "society", { kind: "none" }))}
          onPick={(o) => onChange(setLevel(value, "society", picked(o)))}
          onNew={() =>
            onChange(
              setLevel(value, "society", {
                kind: "new",
                name: "",
                extra: { societyKind: "APARTMENT_COMPLEX" },
              }),
            )
          }
        >
          {society.kind === "new" && (
            <div className="grid gap-2 sm:grid-cols-[2fr_1fr]">
              <Input
                aria-label="New society name"
                placeholder="Society name"
                value={society.name}
                maxLength={160}
                onChange={(e) =>
                  onChange({ ...value, society: { ...society, name: e.target.value } })
                }
                className="h-10"
                aria-invalid={Boolean(levelErrors.society)}
              />
              <select
                aria-label="New society kind"
                className={selectClass}
                value={society.extra.societyKind}
                onChange={(e) =>
                  onChange({
                    ...value,
                    society: { ...society, extra: { societyKind: e.target.value as SocietyKind } },
                  })
                }
              >
                <option value="APARTMENT_COMPLEX">Apartment complex</option>
                <option value="GATED_COMMUNITY">Gated community</option>
              </select>
            </div>
          )}
          <LevelError id="pf-society" error={levelErrors.society} />
        </LevelSelect>
      )}

      {society.kind !== "none" && (
        <LevelSelect
          id="pf-tower"
          label="Tower or block"
          noneLabel="No tower"
          addLabel="+ Add a tower…"
          options={towers.data ?? []}
          current={tower}
          disabled={disabled}
          onNone={() => onChange(setLevel(value, "tower", { kind: "none" }))}
          onPick={(o) => onChange(setLevel(value, "tower", picked(o)))}
          onNew={() =>
            onChange(
              setLevel(value, "tower", { kind: "new", name: "", extra: { towerKind: "TOWER" } }),
            )
          }
        >
          {tower.kind === "new" && (
            <div className="grid gap-2 sm:grid-cols-[2fr_1fr]">
              <Input
                aria-label="New tower name"
                placeholder="4, Tower 2, Block C…"
                value={tower.name}
                maxLength={60}
                onChange={(e) => onChange({ ...value, tower: { ...tower, name: e.target.value } })}
                className="h-10"
                aria-invalid={Boolean(levelErrors.tower)}
              />
              <select
                aria-label="New tower kind"
                className={selectClass}
                value={tower.extra.towerKind}
                onChange={(e) =>
                  onChange({
                    ...value,
                    tower: { ...tower, extra: { towerKind: e.target.value as TowerKind } },
                  })
                }
              >
                <option value="TOWER">Tower</option>
                <option value="BLOCK">Block</option>
              </select>
            </div>
          )}
          <LevelError id="pf-tower" error={levelErrors.tower} />
        </LevelSelect>
      )}

      {tower.kind !== "none" && (
        <LevelSelect
          id="pf-wing"
          label="Wing"
          noneLabel="No wing"
          addLabel="+ Add a wing…"
          options={wings}
          current={wing}
          disabled={disabled}
          onNone={() => onChange(setLevel(value, "wing", { kind: "none" }))}
          onPick={(o) => onChange(setLevel(value, "wing", picked(o)))}
          onNew={() => onChange(setLevel(value, "wing", { kind: "new", name: "", extra: {} }))}
        >
          {wing.kind === "new" && (
            <Input
              aria-label="New wing name"
              placeholder="A, B, Wing A…"
              value={wing.name}
              maxLength={60}
              onChange={(e) => onChange({ ...value, wing: { ...wing, name: e.target.value } })}
              className="h-10"
              aria-invalid={Boolean(levelErrors.wing)}
            />
          )}
          <LevelError id="pf-wing" error={levelErrors.wing} />
        </LevelSelect>
      )}
    </fieldset>
  )
}
