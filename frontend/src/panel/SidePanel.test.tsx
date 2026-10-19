import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SidePanel } from "./SidePanel";

function panel() {
  return screen.getByRole("complementary", { name: "Filters and layers" });
}

describe("SidePanel", () => {
  it("is a landmark named \"Filters and layers\" with Filters and Layers sections", () => {
    render(<SidePanel />);

    expect(panel()).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Filters" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Layers" })).toBeInTheDocument();
  });

  it("starts open with a toggle that controls an existing element", () => {
    render(<SidePanel />);

    const toggle = screen.getByRole("button", { name: "Filters and layers panel" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const controlledId = toggle.getAttribute("aria-controls");
    expect(controlledId).toBeTruthy();
    const controlled = document.getElementById(controlledId ?? "");
    expect(controlled).not.toBeNull();
    expect(controlled).toContainElement(screen.getByRole("heading", { name: "Filters" }));
  });

  it("hides the sections but keeps the toggle when collapsed", async () => {
    const user = userEvent.setup();
    render(<SidePanel />);

    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));

    const toggle = screen.getByRole("button", { name: "Filters and layers panel" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Layers" })).not.toBeInTheDocument();
  });

  it("shows the sections again when expanded", async () => {
    const user = userEvent.setup();
    render(<SidePanel />);

    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));
    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));

    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Layers" })).toBeInTheDocument();
  });

  it("toggles with Enter and Space from the keyboard", async () => {
    const user = userEvent.setup();
    render(<SidePanel />);

    await user.tab();
    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveFocus();

    await user.keyboard("{Enter}");
    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();

    await user.keyboard(" ");
    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
  });
});
