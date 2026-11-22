import { useId, useState, type FormEvent } from "react"
import { Button } from "@/components/ui/button"
import { MapPinIcon, SearchIcon } from "@/components/ui/icons.gen"
import { CityPicker } from "@/features/city/CityPicker"
import { cn } from "@/lib/utils"
import { useLocalityHints } from "./useProperties"

interface Props {
  city: string | null
  locality: string
  onCityChange: (code: string) => void
  onSubmit: (value: { city: string | null; locality: string }) => void
  className?: string
}

/**
 * City picker + locality box + Search (mockups `home.html`, `search.html`). The locality hints
 * are the localities of the chosen city's published homes, so a hint always finds something.
 */
export function SearchBar({ city, locality, onCityChange, onSubmit, className }: Props) {
  const [text, setText] = useState(locality)
  const [shown, setShown] = useState(locality)
  const hints = useLocalityHints(city ?? undefined)
  const listId = useId()

  // The URL changed the locality (back button, "Clear"): show the new value.
  if (shown !== locality) {
    setShown(locality)
    setText(locality)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit({ city, locality: text.trim() })
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
          <span className="block text-xs text-ink-500">Locality</span>
          <input
            value={text}
            onChange={(event) => setText(event.target.value)}
            list={hints.data?.length ? listId : undefined}
            placeholder={examples ? `${examples}…` : "Type a locality"}
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
