import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { fetchSocieties, fetchTowers, locationKeys, searchAreas } from "@/api/locations"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { MapPinIcon, PlusIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useSignedIn } from "@/features/property/useProperties"
import { cn } from "@/lib/utils"
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
  /**
   * The form's own PIN code field. While the area box is empty, a 6-digit PIN here lists that
   * PIN's areas instead of the city's first areas A–Z.
   */
  pincode?: string
}

function LevelError({ id, error }: { id: string; error?: string }) {
  if (!error) return null
  return (
    <p className="text-xs text-danger-600" data-testid={`${id}-error`} role="alert">
      {error}
    </p>
  )
}

/** Wait this long after the last key before asking for areas (as the search box does). */
const DEBOUNCE_MS = 200

/** One row of the area list: an existing area, or the typed name as a new one. */
interface AreaOption {
  key: string
  label: string
  detail?: string
  pick: () => void
}

/**
 * The area field (captain 2026-11-28): type an area name or a 6-digit PIN code and pick from the
 * list under the box, by tap or by arrow keys and Enter. A picked area keeps its id, so search
 * and grouping use the one canonical name, and it brings its PIN codes and city for the form to
 * fill in. With no city picked yet, the list holds the matching areas of every live city.
 */
function AreaPicker({ city, value, onChange, disabled, pincode }: Props) {
  const { viewer } = useSignedIn()
  const [text, setText] = useState("")
  const [typed, setTyped] = useState("")
  const [focused, setFocused] = useState(false)
  const [closed, setClosed] = useState(false)
  const [active, setActive] = useState(-1)
  const input = useRef<HTMLInputElement>(null)
  const listId = useId()
  const pin = pincode?.trim() ?? ""
  const pinOfForm = /^\d{6}$/.test(pin) ? pin : ""

  useEffect(() => {
    const timer = setTimeout(() => setTyped(text.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text])

  // What the box holds, else the form's PIN code when it is a whole one.
  const search = typed || pinOfForm
  const areas = useQuery({
    queryKey: locationKeys.areas(city, search, viewer),
    queryFn: ({ signal }) => searchAreas(city, search, signal),
    // with no city, only a typed name or PIN: the first areas A–Z of every city say nothing
    enabled: value.area.kind === "none" && !disabled && (Boolean(city) || search.length > 0),
    // the last list stays while the next one loads, so the list does not blink at each key
    placeholderData: (previous) => previous,
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

  const words = text.trim()
  // the list answers what the box holds now, not a few keys ago
  const settled =
    typed === words && areas.isSuccess && !areas.isFetching && !areas.isPlaceholderData
  const found = areas.data ?? []
  const exact = found.some((a) => a.name.toLowerCase() === words.toLowerCase())
  const options: AreaOption[] = found.map((a) => ({
    key: a.id,
    label: a.name,
    detail: [
      a.pincodes.length > 0 ? `PIN ${a.pincodes.slice(0, 3).join(", ")}` : "",
      city ? "" : a.city.name,
      a.status === "PENDING" ? "Pending review" : "",
    ]
      .filter(Boolean)
      .join(" · "),
    pick: () =>
      onChange(
        setLevel(value, "area", {
          kind: "pick",
          id: a.id,
          name: a.name,
          status: a.status,
          pincodes: a.pincodes,
          city: a.city.code,
        }),
      ),
  }))
  // Only once the search answered: a tap before that would propose an area that exists. A new
  // area needs its city.
  if (city && words.length >= 2 && !/^\d+$/.test(words) && settled && !exact)
    options.push({
      key: "new",
      label: `Suggest new area “${words}”`,
      pick: () => onChange(setLevel(value, "area", { kind: "new", name: words, extra: {} })),
    })

  // Open while the box has focus, or when the form's PIN code lists its areas.
  const open = !closed && (focused || (!words && pinOfForm !== ""))
  const expanded = open && options.length > 0
  const optionId = (i: number) => `${listId}-o${i}`
  let message = ""
  if (open && settled && found.length === 0) {
    // only a whole 6-digit PIN is "a PIN"; part of one is just text that matched nothing
    if (/^\d{6}$/.test(search)) message = `No area has PIN ${search} yet. Type the area name.`
    else if (words) message = `No area matches “${words}”.`
  }

  function pick(option: AreaOption) {
    setActive(-1)
    option.pick()
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault()
      setClosed(false)
      if (options.length === 0) return
      const step = event.key === "ArrowDown" ? 1 : -1
      setActive((i) => (i + step + options.length) % options.length)
    } else if (event.key === "Enter" && expanded) {
      // Enter picks; it never sends the form while the list is open
      event.preventDefault()
      if (active >= 0 && options[active]) pick(options[active])
    } else if (event.key === "Escape" && expanded) {
      event.preventDefault()
      setClosed(true)
      setActive(-1)
    }
  }

  return (
    <div className="relative space-y-2">
      <Input
        id="pf-area"
        ref={input}
        role="combobox"
        aria-expanded={expanded}
        aria-controls={expanded ? `${listId}-list` : undefined}
        aria-autocomplete="list"
        aria-activedescendant={expanded && active >= 0 ? optionId(active) : undefined}
        autoComplete="off"
        value={text}
        disabled={disabled}
        placeholder={
          city ? "Type an area name or a 6-digit PIN code" : "Type your area or 6-digit PIN code"
        }
        onChange={(e) => {
          setText(e.target.value)
          setClosed(false)
          setActive(-1)
        }}
        onFocus={() => {
          setFocused(true)
          setClosed(false)
        }}
        onBlur={() =>
          setTimeout(() => {
            if (document.activeElement !== input.current) setFocused(false)
          }, 150)
        }
        onKeyDown={onKeyDown}
        className="h-10"
        aria-describedby="pf-area-hint"
      />
      {expanded && (
        <div
          id={`${listId}-list`}
          role="listbox"
          aria-label="Areas"
          // on a phone the list sits in the form and pushes the next fields down; wider, it hangs
          // over them
          className="z-30 max-h-72 w-full overflow-y-auto rounded-lg border border-line bg-surface py-1 shadow-lg sm:absolute sm:top-10 sm:right-0 sm:left-0 sm:mt-1"
        >
          {options.map((option, i) => (
            <div
              key={option.key}
              id={optionId(i)}
              role="option"
              aria-selected={i === active}
              aria-label={option.detail ? `${option.label}, ${option.detail}` : option.label}
              data-testid="area-option"
              // mousedown, not click: the box's blur would close the list first
              onMouseDown={(event) => {
                event.preventDefault()
                pick(option)
              }}
              className={cn(
                "flex min-h-11 cursor-pointer items-center gap-2 px-3 py-1.5",
                i === active ? "bg-teal-50" : "hover:bg-paper",
              )}
            >
              {option.key === "new" && <PlusIcon size={16} className="text-ink-500" />}
              <span className="flex min-w-0 flex-col">
                <span className="text-sm font-semibold text-ink-900">{option.label}</span>
                {option.detail && <span className="text-xs text-ink-500">{option.detail}</span>}
              </span>
            </div>
          ))}
        </div>
      )}
      {message && (
        <p role="status" className="text-sm text-ink-500">
          {message}
        </p>
      )}
    </div>
  )
}

/**
 * Type the society or apartment name (captain 2026-11-23 17:18): matching societies of the area to
 * pick, and when none matches, the typed name as a new one, tagged "Pending review" for the team.
 */
function SocietyFinder({
  options,
  onPick,
  onNew,
}: {
  options: Array<{ id: string; name: string; status: LocationStatus }>
  onPick: (option: { id: string; name: string; status: LocationStatus }) => void
  onNew: (name: string) => void
}) {
  const [typed, setTyped] = useState("")
  const key = typed.trim().toLowerCase()
  const matches = key ? options.filter((o) => o.name.toLowerCase().includes(key)).slice(0, 6) : []
  const exact = options.some((o) => o.name.trim().toLowerCase() === key)
  return (
    <div className="space-y-1.5">
      <Input
        id="pf-society-find"
        aria-label="Type the society or apartment name"
        placeholder="Type the society or apartment name"
        value={typed}
        maxLength={160}
        onChange={(e) => setTyped(e.target.value)}
        className="h-10"
      />
      {key.length > 0 && (
        <ul className="space-y-1" aria-label="Societies" data-testid="pf-society-matches">
          {matches.map((o) => (
            <li key={o.id}>
              <Button type="button" size="sm" variant="ghost" onClick={() => onPick(o)}>
                {o.name}
                {o.status === "PENDING" ? " (pending review)" : ""}
              </Button>
            </li>
          ))}
          {matches.length === 0 && (
            <li className="text-sm text-ink-500">
              No society in this area matches “{typed.trim()}”.
            </li>
          )}
          {key.length >= 2 && !exact && (
            <li>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                data-testid="pf-society-add-typed"
                onClick={() => onNew(typed.trim())}
              >
                <PlusIcon />
                Add “{typed.trim()}” (pending review)
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
          {society.kind === "none" && !disabled && (
            <SocietyFinder
              options={societies.data ?? []}
              onPick={(o) => onChange(setLevel(value, "society", picked(o)))}
              onNew={(name) =>
                onChange(
                  setLevel(value, "society", {
                    kind: "new",
                    name,
                    extra: { societyKind: "APARTMENT_COMPLEX" },
                  }),
                )
              }
            />
          )}
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
