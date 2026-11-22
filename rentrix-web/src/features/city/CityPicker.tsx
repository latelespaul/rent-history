import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react"
import {
  CheckIcon,
  ChevronDownIcon,
  CityIcon,
  MapPinIcon,
  SearchIcon,
} from "@/components/ui/icons.gen"
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
          <mark key={i} className="rounded-[3px] bg-marigold-100 px-px text-inherit">
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
          "flex w-full items-center gap-3 rounded-md border border-line-strong bg-surface px-3 text-left transition-colors",
          "hover:bg-sunk/60 disabled:cursor-not-allowed disabled:opacity-60",
          label ? "min-h-14 py-2" : "min-h-11 py-2",
          open && "border-teal-600 ring-3 ring-teal-50",
        )}
      >
        <CityIcon size={18} className="text-ink-500" />
        <span className="min-w-0 flex-1">
          {label && (
            <span id={labelId} className="block text-xs text-ink-500">
              {label}
            </span>
          )}
          <span id={`${baseId}-value`} className="block truncate">
            {triggerText ??
              (selected ? (
                <>
                  <span className="font-semibold text-ink-900">{selected.name}</span>{" "}
                  <span className="text-sm text-ink-500">{selected.state}</span>
                </>
              ) : (
                <span className="text-ink-500">{placeholder}</span>
              ))}
          </span>
        </span>
        <ChevronDownIcon
          size={18}
          className={cn("text-ink-500 transition-transform", open && "rotate-180")}
        />
      </button>

      {open && (
        <div className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-xl border border-line-strong bg-surface text-ink-900 shadow-md sm:right-auto sm:w-[340px]">
          <div className="flex items-center gap-2 border-b border-line px-3">
            <SearchIcon size={18} className="text-ink-500" />
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
              className="h-11 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-ink-500"
            />
            <span className="shrink-0 text-xs text-ink-500">{list.length} active</span>
          </div>

          <ul
            id={listId}
            role="listbox"
            aria-label="Cities"
            className="max-h-[300px] overflow-y-auto p-1.5"
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
                    "flex min-h-11 cursor-pointer items-center gap-2.5 rounded-sm px-2.5 py-2 text-sm",
                    index === active && "bg-teal-50",
                  )}
                >
                  <MapPinIcon size={18} className="text-ink-500" />
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold text-ink-900">
                      <Marked parts={highlight(city.name, query)} />
                    </span>
                    {cityDetail(city) && (
                      <span className="block truncate text-xs text-ink-500">
                        <Marked parts={highlight(cityDetail(city), query)} />
                      </span>
                    )}
                  </span>
                  {count !== null && (
                    <span className="shrink-0 text-xs text-ink-500">
                      {count > 0 ? plural(count, "property", "properties") : "new"}
                    </span>
                  )}
                  {isSelected && <CheckIcon size={18} className="text-teal-700" />}
                </li>
              )
            })}
          </ul>

          {matches.length === 0 && (
            <div role="status" className="space-y-1 px-4 py-4 text-sm text-ink-700">
              <p className="font-semibold text-ink-900">
                Rentrix is not live in “{query.trim()}” yet.
              </p>
              <p className="text-ink-500">{launchCopy(list)}.</p>
            </div>
          )}

          <p className="border-t border-line bg-sunk px-3 py-2 text-xs text-ink-500">
            {launchCopy(list)}
          </p>
        </div>
      )}
    </div>
  )
}
