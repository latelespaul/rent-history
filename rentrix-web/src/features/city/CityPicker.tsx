import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react"
import { Building2, Check, ChevronDown, MapPin, Search } from "lucide-react"
import { cn } from "@/lib/utils"
import type { City } from "@/types/api"
import {
  cityDetail,
  filterCities,
  highlight,
  launchCopy,
  plural,
  statsByCity,
  type TextPart,
} from "./cityText"
import { useCities, useStats } from "./useCities"

interface Props {
  /** Selected city code, or null when none is chosen. */
  value: string | null
  onChange: (code: string) => void
  /** Small visible label inside the field, e.g. "City". */
  label?: string
  placeholder?: string
  className?: string
}

function Marked({ parts }: { parts: TextPart[] }) {
  return (
    <>
      {parts.map((part, i) =>
        part.match ? (
          <mark key={i} className="rounded-sm bg-amber-200/70 text-inherit dark:bg-amber-400/30">
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        ),
      )}
    </>
  )
}

/**
 * The one city control of the web (D-85): a searchable combobox over `GET /cities`, for Home,
 * Search and later onboarding, the property form and admin filters. Type to filter on name,
 * alias and state; ↑/↓ move, Enter chooses, Esc closes (ARIA combobox + listbox pattern).
 */
export function CityPicker({
  value,
  onChange,
  label,
  placeholder = "Choose a city",
  className,
}: Props) {
  const cities = useCities()
  const stats = useStats()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [active, setActive] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const baseId = useId()
  const listId = `${baseId}-list`
  const labelId = `${baseId}-label`
  const optionId = (code: string) => `${baseId}-opt-${code}`

  const list = useMemo(() => cities.data ?? [], [cities.data])
  const matches = useMemo(() => filterCities(list, query), [list, query])
  const counts = useMemo(
    () => (stats.isSuccess ? statsByCity(stats.data.cities) : null),
    [stats.isSuccess, stats.data],
  )
  const selected = list.find((city) => city.code === value) ?? null
  const activeCity: City | undefined = matches[Math.min(active, matches.length - 1)]

  useEffect(() => {
    if (!open) return
    inputRef.current?.focus()
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener("pointerdown", onPointerDown)
    return () => document.removeEventListener("pointerdown", onPointerDown)
  }, [open])

  useEffect(() => {
    if (open && activeCity) {
      document.getElementById(optionId(activeCity.code))?.scrollIntoView?.({ block: "nearest" })
    }
    // optionId only depends on baseId, which never changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, activeCity])

  function openList() {
    // The query resets, so the matches are the whole list: start on the selected city.
    setQuery("")
    setActive(
      Math.max(
        0,
        list.findIndex((city) => city.code === value),
      ),
    )
    setOpen(true)
  }

  function close(refocus: boolean) {
    setOpen(false)
    if (refocus) triggerRef.current?.focus()
  }

  function choose(city: City) {
    onChange(city.code)
    close(true)
  }

  function onInputKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault()
        setActive((i) => (matches.length ? (i + 1) % matches.length : 0))
        break
      case "ArrowUp":
        event.preventDefault()
        setActive((i) => (matches.length ? (i - 1 + matches.length) % matches.length : 0))
        break
      case "Home":
        event.preventDefault()
        setActive(0)
        break
      case "End":
        event.preventDefault()
        setActive(Math.max(0, matches.length - 1))
        break
      case "Enter":
        event.preventDefault()
        if (activeCity) choose(activeCity)
        break
      case "Escape":
        event.preventDefault()
        close(true)
        break
      case "Tab":
        close(false)
        break
    }
  }

  const unavailable = cities.isError
  const triggerText = cities.isPending
    ? "Loading cities…"
    : unavailable
      ? "Cities could not be loaded"
      : null

  return (
    <div ref={rootRef} className={cn("relative", className)}>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={label ? `${labelId} ${baseId}-value` : undefined}
        disabled={cities.isPending || unavailable}
        onClick={() => (open ? close(false) : openList())}
        onKeyDown={(event) => {
          if (!open && (event.key === "ArrowDown" || event.key === "ArrowUp")) {
            event.preventDefault()
            openList()
          }
        }}
        className={cn(
          "flex w-full items-center gap-3 rounded-lg border bg-background px-3 text-left transition-colors outline-none",
          "hover:bg-muted/60 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
          "disabled:cursor-not-allowed disabled:opacity-60",
          label ? "min-h-14 py-2" : "min-h-11 py-2",
          open && "border-ring",
        )}
      >
        <Building2 className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span className="min-w-0 flex-1">
          {label && (
            <span id={labelId} className="block text-xs text-muted-foreground">
              {label}
            </span>
          )}
          <span id={`${baseId}-value`} className="block truncate">
            {triggerText ??
              (selected ? (
                <>
                  <span className="font-medium">{selected.name}</span>{" "}
                  <span className="text-sm text-muted-foreground">{selected.state}</span>
                </>
              ) : (
                <span className="text-muted-foreground">{placeholder}</span>
              ))}
          </span>
        </span>
        <ChevronDown
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform",
            open && "rotate-180",
          )}
          aria-hidden
        />
      </button>

      {open && (
        <div className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-lg border bg-popover text-popover-foreground shadow-lg sm:right-auto sm:w-96">
          <div className="flex items-center gap-2 border-b px-3">
            <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            <input
              ref={inputRef}
              role="combobox"
              aria-label="Search city"
              aria-expanded
              aria-controls={listId}
              aria-autocomplete="list"
              aria-activedescendant={activeCity ? optionId(activeCity.code) : undefined}
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setActive(0)
              }}
              onKeyDown={onInputKeyDown}
              placeholder={
                list[0] ? `Search city, e.g. ${list[0].aliases[0] ?? list[0].name}` : "Search city"
              }
              className="h-11 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-muted-foreground"
            />
            <span className="shrink-0 text-xs text-muted-foreground">{list.length} active</span>
          </div>

          <ul
            id={listId}
            role="listbox"
            aria-label="Cities"
            className="max-h-80 overflow-y-auto p-1"
            hidden={matches.length === 0}
          >
            {matches.map((city, index) => {
              const isSelected = city.code === value
              const count = counts ? (counts.get(city.code)?.publishedProperties ?? 0) : null
              return (
                <li
                  key={city.code}
                  id={optionId(city.code)}
                  role="option"
                  aria-selected={isSelected}
                  onPointerDown={(event) => event.preventDefault()}
                  onPointerMove={() => setActive(index)}
                  onClick={() => choose(city)}
                  className={cn(
                    "flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-3 py-2",
                    index === active && "bg-muted",
                  )}
                >
                  <MapPin className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium">
                      <Marked parts={highlight(city.name, query)} />
                    </span>
                    {cityDetail(city) && (
                      <span className="block truncate text-xs text-muted-foreground">
                        <Marked parts={highlight(cityDetail(city), query)} />
                      </span>
                    )}
                  </span>
                  {count !== null && (
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {count > 0 ? plural(count, "property", "properties") : "new"}
                    </span>
                  )}
                  {isSelected && <Check className="size-4 shrink-0 text-primary" aria-hidden />}
                </li>
              )
            })}
          </ul>

          {matches.length === 0 && (
            <div role="status" className="space-y-1 px-4 py-5 text-sm">
              <p className="font-medium">Rentrix is not live in “{query.trim()}” yet.</p>
              <p className="text-muted-foreground">{launchCopy(list)}.</p>
            </div>
          )}

          <p className="border-t bg-muted/50 px-4 py-2 text-xs text-muted-foreground">
            {launchCopy(list)}
          </p>
        </div>
      )}
    </div>
  )
}
