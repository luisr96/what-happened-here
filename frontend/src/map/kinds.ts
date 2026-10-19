import type { CSSProperties } from "react";
import type { Kind } from "../api/client";

export type Rgb = readonly [number, number, number];

const LABELS: Readonly<Record<Kind, string>> = {
  HAPPENED: "Happened",
  BORN: "Born",
  DIED: "Died",
  FOUNDED: "Founded",
  ENDED: "Ended",
  OPENED: "Opened",
};

// Bright hues that stay apart on the dark basemap and glow when blended additively.
const COLORS: Readonly<Record<Kind, Rgb>> = {
  HAPPENED: [255, 196, 61],
  BORN: [94, 220, 145],
  DIED: [240, 98, 110],
  FOUNDED: [94, 163, 255],
  ENDED: [190, 130, 255],
  OPENED: [64, 224, 230],
};

/** Every kind, in API order; taken from LABELS, whose type makes it list each kind exactly once. */
export const KINDS = Object.keys(LABELS) as readonly Kind[];

export function kindLabel(kind: Kind): string {
  return LABELS[kind];
}

export function kindColor(kind: Kind): Rgb {
  return COLORS[kind];
}

/**
 * The kind's colour as a `--kind` custom property, so the stylesheet can draw a swatch or dot in
 * the same colour as the map's points without the colours being repeated in CSS.
 */
export function kindStyle(kind: Kind): CSSProperties {
  return { "--kind": `rgb(${COLORS[kind].join(" ")})` } as CSSProperties;
}
