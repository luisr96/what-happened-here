import type { Layer } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import type { EventView } from "../api/hooks";
import { kindColor } from "./kinds";

const MIN_RADIUS_PX = 2;
const MAX_RADIUS_PX = 10;
/** Pixels added each time importance doubles. */
const PX_PER_DOUBLING = 1.1;
// Low alphas because blending is additive: a few overlapping points already sum to near white.
const EXACT_ALPHA = 140;
const HALO_ALPHA = 35;

/**
 * Radius in pixels on a log scale: importance runs from 1 to ~350 Wikipedias, and a linear
 * scale would make a few world-famous items swallow the map.
 */
export function radiusPx(importance: number): number {
  const radius = MIN_RADIUS_PX + PX_PER_DOUBLING * Math.log2(1 + Math.max(0, importance));
  return Math.min(MAX_RADIUS_PX, radius);
}

/** Approximate coordinates (precision unknown or >= 0.1°) are wide, faint discs, not points. */
function haloRadiusPx(importance: number): number {
  return 1.6 * radiusPx(importance) + 2;
}

const COMMON = {
  pickable: true,
  radiusUnits: "pixels",
  getPosition: (e: EventView): [number, number] => [e.longitude, e.latitude],
  // Overlapping points add up their colour, so dense areas glow (kepler.gl's "additive" blending).
  parameters: {
    depthCompare: "always",
    blend: true,
    blendColorOperation: "add",
    blendColorSrcFactor: "src-alpha",
    blendColorDstFactor: "one",
    blendAlphaOperation: "add",
    blendAlphaSrcFactor: "one",
    blendAlphaDstFactor: "one-minus-src-alpha",
  },
} as const;

const RING_COLOR: [number, number, number] = [255, 255, 255];
const RING_WIDTH_PX = 2;
const RING_GAP_PX = 3;

/** All layers, bottom to top: approximate halos, exact points, then the selected event's ring. */
export function eventLayers(events: readonly EventView[], selectedId: string | null = null): Layer[] {
  const selected = selectedId === null ? null : events.find((e) => e.id === selectedId) ?? null;
  return [...pointLayers(events), selectionLayer(selected)];
}

/** Halos for approximate events underneath, exact points on top; both pickable for tooltips. */
export function pointLayers(events: readonly EventView[]): Layer[] {
  return [
    new ScatterplotLayer<EventView>({
      ...COMMON,
      id: "events-approximate",
      data: events.filter((e) => e.approximate),
      getFillColor: (e) => [...kindColor(e.kind), HALO_ALPHA],
      getRadius: (e) => haloRadiusPx(e.importance),
    }),
    new ScatterplotLayer<EventView>({
      ...COMMON,
      id: "events-exact",
      data: events.filter((e) => !e.approximate),
      getFillColor: (e) => [...kindColor(e.kind), EXACT_ALPHA],
      getRadius: (e) => radiusPx(e.importance),
    }),
  ];
}

/**
 * A ring around the selected event, just outside its point or halo. Separate from the point
 * layers so selecting an event rebuilds only this one-point layer, not every point's buffers.
 */
export function selectionLayer(selected: EventView | null): Layer {
  return new ScatterplotLayer<EventView>({
    id: "events-selected",
    data: selected === null ? [] : [selected],
    pickable: false,
    stroked: true,
    filled: false,
    radiusUnits: "pixels",
    lineWidthUnits: "pixels",
    getPosition: (e) => [e.longitude, e.latitude],
    getRadius: (e) => (e.approximate ? haloRadiusPx(e.importance) : radiusPx(e.importance)) + RING_GAP_PX,
    getLineColor: RING_COLOR,
    getLineWidth: RING_WIDTH_PX,
  });
}
