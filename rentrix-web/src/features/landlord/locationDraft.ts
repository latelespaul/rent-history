import type {
  LocationInput,
  LocationStatus,
  PropertyLocation,
  SocietyKind,
  TowerKind,
} from "@/types/api"

/** A level the landlord has not set. */
export interface NoLevel {
  kind: "none"
}

/** An existing row of the hierarchy, picked from a list. */
export interface PickedLevel {
  kind: "pick"
  id: string
  name: string
  status: LocationStatus
  /** An area's PIN codes, for the PIN autofill in "Add your home". */
  pincodes?: string[]
}

/** A proposal typed in the form; saved `PENDING` and shown "Pending review" (D-109). */
export interface NewLevel<Extra = object> {
  kind: "new"
  name: string
  extra: Extra
}

export interface LocationDraft {
  area: NoLevel | PickedLevel | NewLevel
  society: NoLevel | PickedLevel | NewLevel<{ societyKind: SocietyKind }>
  tower: NoLevel | PickedLevel | NewLevel<{ towerKind: TowerKind }>
  wing: NoLevel | PickedLevel | NewLevel
}

export type LevelName = keyof LocationDraft

const ORDER: LevelName[] = ["area", "society", "tower", "wing"]

const NONE: NoLevel = { kind: "none" }

export const EMPTY_DRAFT: LocationDraft = { area: NONE, society: NONE, tower: NONE, wing: NONE }

/**
 * Sets one level and clears every level under it: a new area has other societies, a new
 * society other towers (W10 "picker resets lower levels when a higher level changes").
 */
export function setLevel<L extends LevelName>(
  draft: LocationDraft,
  level: L,
  value: LocationDraft[L],
): LocationDraft {
  const next = { ...draft, [level]: value }
  for (const lower of ORDER.slice(ORDER.indexOf(level) + 1)) next[lower] = NONE
  return next
}

/** The form's state for a saved property's place. */
export function draftFromLocation(location: PropertyLocation | undefined): LocationDraft {
  if (!location) return EMPTY_DRAFT
  const pick = (l: { id: string; name: string; status: LocationStatus } | undefined) =>
    l ? ({ kind: "pick", id: l.id, name: l.name, status: l.status } as const) : NONE
  return {
    area: pick(location.area),
    society: pick(location.society),
    tower: pick(location.tower),
    wing: pick(location.wing),
  }
}

/** True when the area is set, the one level the API requires. */
export function hasArea(draft: LocationDraft): boolean {
  return draft.area.kind !== "none"
}

/**
 * The `location` body of `POST` / `PATCH /properties` (D-109, D-110): each level an id or an
 * inline proposal, never both; a new area takes the form's PIN code.
 */
export function toLocationInput(draft: LocationDraft, pincode: string): LocationInput {
  const input: LocationInput = {}
  const { area, society, tower, wing } = draft
  if (area.kind === "pick") input.areaId = area.id
  if (area.kind === "new") input.newArea = { name: area.name.trim(), pincode: pincode.trim() }
  if (society.kind === "pick") input.societyId = society.id
  if (society.kind === "new")
    input.newSociety = { name: society.name.trim(), kind: society.extra.societyKind }
  if (tower.kind === "pick") input.towerId = tower.id
  if (tower.kind === "new")
    input.newTower = { name: tower.name.trim(), kind: tower.extra.towerKind }
  if (wing.kind === "pick") input.wingId = wing.id
  if (wing.kind === "new") input.newWing = { name: wing.name.trim() }
  return input
}

/** Whether two drafts send the same `location` (used to skip an unchanged one on PATCH). */
export function sameDraft(a: LocationDraft, b: LocationDraft): boolean {
  return JSON.stringify(toLocationInput(a, "")) === JSON.stringify(toLocationInput(b, ""))
}
