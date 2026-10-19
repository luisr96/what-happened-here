import { ScatterplotLayer } from "@deck.gl/layers";
import { describe, expect, it } from "vitest";
import { placeLayer } from "./eventLayers";

type Place = { lat: number; lon: number; radius: number };

function scatter(place: Place | null): ScatterplotLayer<Place> {
  const layer: unknown = placeLayer(place);
  if (!(layer instanceof ScatterplotLayer)) throw new Error("expected a ScatterplotLayer");
  return layer as ScatterplotLayer<Place>;
}

const fiji: Place = { lat: -17.8, lon: 179.9, radius: 2293 };

describe("placeLayer", () => {
  it("is a non-pickable filled and stroked circle sized in metres", () => {
    const layer = scatter(fiji);
    expect(layer.id).toBe("nearby-area");
    expect(layer.props.radiusUnits).toBe("meters");
    expect(layer.props.stroked).toBe(true);
    expect(layer.props.filled).toBe(true);
    expect(layer.props.pickable).toBe(false);
  });

  it("draws the place, near the antimeridian, at lon/lat with its radius", () => {
    const layer = scatter(fiji);
    expect(layer.props.data).toEqual([fiji]);
    const getRadius: unknown = layer.props.getRadius;
    const getPosition: unknown = layer.props.getPosition;
    const radius = typeof getRadius === "function" ? (getRadius as (p: Place) => number)(fiji) : getRadius;
    expect(radius).toBe(2293);
    if (typeof getPosition !== "function") throw new Error("getPosition is not an accessor");
    expect((getPosition as (p: Place) => readonly number[])(fiji).slice(0, 2)).toEqual([179.9, -17.8]);
  });

  it("draws nothing without a place", () => {
    expect(scatter(null).props.data).toEqual([]);
  });
});
