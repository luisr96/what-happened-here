import { useState } from "react";
import type { DistanceUnits } from "../map/nearby";

export type MapStyleId = "dark" | "positron" | "liberty" | "bright" | "fiord";

/** Per-viewer preferences, kept in this browser between visits. */
export type Settings = { readonly units: DistanceUnits; readonly mapStyle: MapStyleId };

export const DEFAULT_SETTINGS: Settings = { units: "km", mapStyle: "dark" };

/** OpenFreeMap's styles (no token needed), in the order the settings list them. */
export const MAP_STYLES: readonly { readonly id: MapStyleId; readonly label: string }[] = [
  { id: "dark", label: "Dark" },
  { id: "positron", label: "Positron" },
  { id: "liberty", label: "Liberty" },
  { id: "bright", label: "Bright" },
  { id: "fiord", label: "Fiord" },
];

export function styleUrl(id: MapStyleId): string {
  return `https://tiles.openfreemap.org/styles/${id}`;
}

const STORAGE_KEY = "whh.settings";
const UNITS = ["km", "mi"] as const satisfies readonly DistanceUnits[];

/**
 * The settings and a setter that also saves them. Storage is a convenience: it can be empty,
 * blocked (a private window) or hold data from an older version, so what's read back is checked
 * field by field and anything unusable falls back to its default.
 */
export function useSettings(): [Settings, (next: Settings) => void] {
  const [settings, setSettings] = useState(readStored);
  const update = (next: Settings) => {
    setSettings(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Not saved (storage blocked or full); the change still applies for this visit.
    }
  };
  return [settings, update];
}

function readStored(): Settings {
  let stored: unknown;
  try {
    stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
  } catch {
    return DEFAULT_SETTINGS;
  }
  if (typeof stored !== "object" || stored === null || Array.isArray(stored)) return DEFAULT_SETTINGS;
  const { units, mapStyle } = stored as { units?: unknown; mapStyle?: unknown };
  return {
    units: UNITS.find((u) => u === units) ?? DEFAULT_SETTINGS.units,
    mapStyle: MAP_STYLES.find((s) => s.id === mapStyle)?.id ?? DEFAULT_SETTINGS.mapStyle,
  };
}
