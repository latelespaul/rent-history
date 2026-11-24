import { useId, useState, type FormEvent } from "react"
import { Button } from "@/components/ui/button"
import { MapPinIcon, SearchIcon } from "@/components/ui/icons.gen"
import { CityPicker } from "@/features/city/CityPicker"
import { cn } from "@/lib/utils"
import { useLocalityHints } from "./useProperties"

interface Props {
  city: string | null
  /** The words in the box: an area, society or building name, a street or a PIN code. */
  query: string
  onCityChange: (code: string) => void
  onSubmit: (value: { city: string | null; query: string }) => void
  className?: string
}

/**
 * City picker + one search box + Search (mockups `home.html`, `search.html`). The box finds a home
 * by its area, society (a typed one still pending review too), building, street or PIN code, sent
 * as `q` (D-162). The hints are the localities of the chosen city's published homes.
 */
export function SearchBar({ city, query, onCityChange, onSubmit, className }: Props) {
  const [text, setText] = useState(query)
  const [shown, setShown] = useState(query)
  const hints = useLocalityHints(city ?? undefined)
  const listId = useId()

  // The URL changed the search (back button, "Clear"): show the new value.
  if (shown !== query) {
    setShown(query)
    setText(query)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit({ city, query: text.trim() })
  }

  const examples = hints.data?.slice(0, 3).join(", ")

  return (
    <form
      role="search"
      onSubmit={submit}
      className={cn(
        "flex flex-col gap-2 rounded-xl border border-line-strong bg-surface p-2 shadow-md sm:flex-row sm:items-center",
        className,
      )}
    >
      <CityPicker label="City" value={city} onChange={onCityChange} className="sm:w-72" />
      <label className="flex min-h-14 flex-1 items-center gap-3 rounded-lg px-3">
        <MapPinIcon size={18} className="text-ink-500" />
        <span className="min-w-0 flex-1">
          <span className="block text-xs text-ink-500">
            Locality, society, building or PIN code
          </span>
          <input
            value={text}
            onChange={(event) => setText(event.target.value)}
            list={hints.data?.length ? listId : undefined}
            placeholder={examples ? `${examples}…` : "e.g. HSR Layout, Sobha, 560102"}
            className="w-full bg-transparent outline-none placeholder:text-ink-500"
          />
        </span>
        {hints.data && hints.data.length > 0 && (
          <datalist id={listId}>
            {hints.data.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
        )}
      </label>
      <Button type="submit" size="lg">
        <SearchIcon />
        Search
      </Button>
    </form>
  )
}
