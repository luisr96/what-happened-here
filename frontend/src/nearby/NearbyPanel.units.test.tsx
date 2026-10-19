import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { caesarDied } from "../test/apiFixtures";
import { createQueryWrapper } from "../test/queryWrapper";
import { server } from "../test/server";
import { NearbyPanel } from "./NearbyPanel";

const rome = { lat: 41.9, lon: 12.5, radius: 2293 };

function renderPanel(units?: "km" | "mi", radius = rome.radius) {
  server.use(http.get("*/api/places/nearby", () => HttpResponse.json({ events: [caesarDied], truncated: false })));
  render(
    <NearbyPanel
      place={{ ...rome, radius }}
      filters={{}}
      onSelect={vi.fn()}
      onClose={vi.fn()}
      onRadiusChange={vi.fn()}
      {...(units !== undefined && { units })}
    />,
    { wrapper: createQueryWrapper() },
  );
}

describe("NearbyPanel distance units", () => {
  it("uses kilometres when no units are given", () => {
    renderPanel();
    expect(screen.getByText("Within 2.3 km")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Search radius" })).toHaveAttribute("aria-valuetext", "2.3 km");
  });

  it("shows the Within text and the slider value in miles", () => {
    renderPanel("mi");
    expect(screen.getByText("Within 1.4 mi")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Search radius" })).toHaveAttribute("aria-valuetext", "1.4 mi");
  });

  it("shows feet for a small radius in miles", () => {
    renderPanel("mi", 100);
    expect(screen.getByText("Within 330 ft")).toBeInTheDocument();
  });
});
