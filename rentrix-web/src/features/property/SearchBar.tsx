import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { fetchSuggestions, searchKeys } from "@/api/search"
import { Button } from "@/components/ui/button"
import { MapPinIcon, SearchIcon } from "@/components/ui/icons.gen"
import { CityPicker } from "@/features/city/CityPicker"
import { cn } from "@/lib/utils"
import { unitLine } from "./labels"
import { useLocalityHints, useSignedIn } from "./useProperties"

/** A society picked in the box: shown as a chip, then the box takes its flat number (D-167). */
export interface PickedSociety {
  id: string
  name: string
}

interface Props {
  city: string | null
  /** The words in the box: an area, society or building name, a street or a PIN code. */
  query: string
  /** The society the search is narrowed to (`society=` in the URL), shown as a chip. */
  society?: PickedSociety | null
  onCityChange: (code: string) => void
  /** A text search; `society` when a society is picked (the text is then its flat number). */
  onSubmit: (value: { city: string | null; query: string; society?: string }) => void
  className?: string
}

/** Wait this long after the last key before asking for suggestions. */
const DEBOUNCE_MS = 200

/** One row of the suggestion list. */
interface Option {
  key: string
  group: "Societies" | "Areas" | "Flats" | "Towers"
  label: string
  detail: string
  pick: () => void
}

/**
 * City picker + one search box + Search (mockups `home.html`, `search.html`). The box finds a home
 * by its area, society, building, street or PIN code, sent as `q` (D-162), and suggests societies
 * (typos allowed), areas and flats while typing (D-164). Two-step pick (D-167): choosing a society
 * turns the box into "[Prestige Lakeside ×] Flat no…", which then suggests that society's flat
 * numbers and towers; Search with a flat number looks for it in that society, Search with none
 * opens the society page. A flat opens its home page, a tower its society page on that tower.
 */
export function SearchBar({ city, query, society, onCityChange, onSubmit, className }: Props) {
  const navigate = useNavigate()
  const { ready, viewer } = useSignedIn()
  const [text, setText] = useState(query)
  const [shown, setShown] = useState(query)
  const [picked, setPicked] = useState<PickedSociety | null>(society ?? null)
  const [shownSociety, setShownSociety] = useState(society?.id ?? null)
  const [typed, setTyped] = useState(query)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const input = useRef<HTMLInputElement>(null)
  const hints = useLocalityHints(city ?? undefined)
  const listId = useId()

  // The URL changed the search (back button, "Clear"): show the new value.
  if (shown !== query) {
    setShown(query)
    setText(query)
    setTyped(query)
  }
  if (shownSociety !== (society?.id ?? null)) {
    setShownSociety(society?.id ?? null)
    setPicked(society ?? null)
  }

  useEffect(() => {
    const timer = setTimeout(() => setTyped(text.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text])

  const asking =
    open && ready && (picked !== null || typed.replace(/[^\p{L}\p{N}]/gu, "").length >= 2)
  const suggestions = useQuery({
    queryKey: searchKeys.suggestions(city ?? "", picked?.id ?? "", typed, viewer),
    queryFn: ({ signal }) =>
      fetchSuggestions({ city: city ?? undefined, q: typed, society: picked?.id }, signal),
    enabled: asking,
    staleTime: 1000 * 30,
  })

  function pickSociety(next: PickedSociety) {
    setPicked(next)
    setText("")
    setTyped("")
    setActive(-1)
    setOpen(true)
    input.current?.focus()
  }

  function unpick() {
    setPicked(null)
    setActive(-1)
    input.current?.focus()
  }

  const data = asking ? suggestions.data : undefined
  const options: Option[] = [
    ...(data?.societies ?? []).map((s) => ({
      key: `s-${s.id}-${s.tower?.id ?? ""}`,
      group: "Societies" as const,
      label: s.tower ? `${s.name} · ${s.tower.name}` : s.name,
      detail: `${s.area.name}, ${s.city.name} · ${s.publishedHomes} ${s.publishedHomes === 1 ? "home" : "homes"}`,
      pick: () =>
        s.tower
          ? navigate(`/s/${encodeURIComponent(s.id)}?tower=${encodeURIComponent(s.tower.id)}`)
          : pickSociety({ id: s.id, name: s.name }),
    })),
    ...(data?.towers ?? []).map((t) => ({
      key: `t-${t.id}`,
      group: "Towers" as const,
      label: t.name,
      detail: `${t.publishedHomes} ${t.publishedHomes === 1 ? "home" : "homes"}`,
      pick: () =>
        picked && navigate(`/s/${encodeURIComponent(picked.id)}?tower=${encodeURIComponent(t.id)}`),
    })),
    ...(data?.homes ?? []).map((h) => ({
      key: `h-${h.id}`,
      group: "Flats" as const,
      label: [h.society.name, unitLine(h, h.unitLabel)].filter(Boolean).join(" · "),
      detail: `${h.locality}, ${h.city.name}`,
      pick: () => navigate(`/p/${encodeURIComponent(h.id)}`),
    })),
    ...(data?.areas ?? []).map((a) => ({
      key: `a-${a.id}`,
      group: "Areas" as const,
      label: a.name,
      detail: a.city.name,
      pick: () => {
        setText(a.name)
        setOpen(false)
        onSubmit({ city: city ?? a.city.code, query: a.name })
      },
    })),
  ]
  const expanded = open && options.length > 0

  function submit(event: FormEvent) {
    event.preventDefault()
    setOpen(false)
    const words = text.trim()
    if (picked && !words) {
      navigate(`/s/${encodeURIComponent(picked.id)}`)
      return
    }
    onSubmit({ city, query: words, society: picked?.id })
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault()
      setOpen(true)
      if (options.length === 0) return
      const step = event.key === "ArrowDown" ? 1 : -1
      setActive((i) => (i + step + options.length) % options.length)
    } else if (event.key === "Enter" && expanded && active >= 0 && options[active]) {
      event.preventDefault()
      setOpen(false)
      options[active].pick()
    } else if (event.key === "Escape") {
      setOpen(false)
      setActive(-1)
    } else if (event.key === "Backspace" && picked && text === "") {
      unpick()
    }
  }

  const examples = hints.data?.slice(0, 3).join(", ")
  const optionId = (i: number) => `${listId}-o${i}`

  return (
    <form
      role="search"
      onSubmit={submit}
      className={cn(
        "relative flex flex-col gap-2 rounded-xl border border-line-strong bg-surface p-2 shadow-md sm:flex-row sm:items-center",
        className,
      )}
    >
      <CityPicker
        label="City"
        value={city}
        onChange={(code) => {
          setPicked(null)
          onCityChange(code)
        }}
        className="sm:w-72"
      />
      <div className="relative flex min-h-14 flex-1 flex-wrap items-center gap-3 rounded-lg px-3 sm:flex-nowrap">
        <MapPinIcon size={18} className="shrink-0 text-ink-500" />
        <span className="min-w-0 flex-1">
          <label htmlFor={`${listId}-input`} className="block text-xs text-ink-500">
            {picked ? "Flat number or tower" : "Locality, society, building or PIN code"}
          </label>
          <span className="flex min-w-0 flex-wrap items-center gap-1.5">
            {picked && (
              <span
                data-testid="society-chip"
                className="inline-flex max-w-full items-center gap-1 rounded-full bg-teal-50 py-0.5 pr-1 pl-2.5 text-sm font-semibold text-teal-800"
              >
                <span className="truncate">{picked.name}</span>
                <button
                  type="button"
                  onClick={unpick}
                  aria-label={`Remove ${picked.name}`}
                  className="grid size-6 place-items-center rounded-full hover:bg-teal-100"
                >
                  ×
                </button>
              </span>
            )}
            <input
              id={`${listId}-input`}
              ref={input}
              role="combobox"
              aria-expanded={expanded}
              aria-controls={expanded ? `${listId}-list` : undefined}
              aria-autocomplete="list"
              aria-activedescendant={expanded && active >= 0 ? optionId(active) : undefined}
              autoComplete="off"
              value={text}
              onChange={(event) => {
                setText(event.target.value)
                setOpen(true)
                setActive(-1)
              }}
              onFocus={() => setOpen(true)}
              onBlur={() =>
                setTimeout(() => {
                  if (document.activeElement !== input.current) setOpen(false)
                }, 150)
              }
              onKeyDown={onKeyDown}
              placeholder={
                picked ? "Flat no…" : examples ? `${examples}…` : "e.g. HSR Layout, Sobha, 560102"
              }
              className="min-w-24 flex-1 bg-transparent outline-none placeholder:text-ink-500"
            />
          </span>
        </span>
        {expanded && (
          <ul
            id={`${listId}-list`}
            role="listbox"
            aria-label="Suggestions"
            // on a phone the list sits in the page and pushes Search down, so it never covers the button;
            // wider, it hangs over the results below the box
            className="z-30 max-h-80 w-full basis-full overflow-y-auto rounded-lg border border-line bg-surface py-1 shadow-lg sm:absolute sm:top-full sm:right-0 sm:left-0 sm:mt-1 sm:w-auto"
          >
            {options.map((option, i) => (
              <li
                key={option.key}
                id={optionId(i)}
                role="option"
                aria-selected={i === active}
                data-testid="suggestion"
                data-group={option.group}
                // mousedown, not click: the input's blur would close the list first
                onMouseDown={(event) => {
                  event.preventDefault()
                  setOpen(false)
                  option.pick()
                }}
                className={cn(
                  "flex min-h-11 cursor-pointer flex-col justify-center px-3 py-1.5",
                  i === active ? "bg-teal-50" : "hover:bg-paper",
                )}
              >
                {(i === 0 || options[i - 1].group !== option.group) && (
                  <span className="text-[11px] font-semibold tracking-wide text-ink-500 uppercase">
                    {option.group}
                  </span>
                )}
                <span className="text-sm font-semibold text-ink-900">{option.label}</span>
                <span className="text-xs text-ink-500">{option.detail}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <Button type="submit" size="lg">
        <SearchIcon />
        Search
      </Button>
    </form>
  )
}
