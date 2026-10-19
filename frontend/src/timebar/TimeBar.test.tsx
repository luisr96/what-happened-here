import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TimeBar } from "./TimeBar";

describe("TimeBar", () => {
  it("is a region named \"Timeline\"", () => {
    render(<TimeBar />);

    expect(screen.getByRole("region", { name: "Timeline" })).toBeInTheDocument();
  });
});
