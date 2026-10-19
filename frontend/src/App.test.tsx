import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { App } from "./App";

vi.mock("./map/MapView", () => ({
  MapView: () => <section aria-label="Map" />,
}));

describe("App", () => {
  it("lays out the map, the side panel and the timeline", () => {
    render(<App />);

    expect(screen.getByRole("region", { name: "Map" })).toBeInTheDocument();
    expect(screen.getByRole("complementary", { name: "Filters and layers" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Timeline" })).toBeInTheDocument();
  });
});
