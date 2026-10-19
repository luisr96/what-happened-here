import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Kind } from "../api/client";
import { KINDS } from "../map/kinds";
import { SidePanel } from "./SidePanel";

type Props = {
  readonly kinds: readonly Kind[];
  readonly onKindsChange: (kinds: readonly Kind[]) => void;
  readonly minImportance: number;
  readonly onMinImportanceChange: (value: number) => void;
};

function props(overrides: Partial<Props> = {}): Props {
  return {
    kinds: KINDS,
    onKindsChange: vi.fn<(kinds: readonly Kind[]) => void>(),
    minImportance: 0,
    onMinImportanceChange: vi.fn<(value: number) => void>(),
    ...overrides,
  };
}

function panel() {
  return screen.getByRole("complementary", { name: "Filters and layers" });
}

describe("SidePanel", () => {
  it("is a landmark named \"Filters and layers\" with Filters and Layers sections", () => {
    render(<SidePanel {...props()} />);

    expect(panel()).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Filters" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Layers" })).toBeInTheDocument();
  });

  it("starts open with a toggle that controls an existing element", () => {
    render(<SidePanel {...props()} />);

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
    render(<SidePanel {...props()} />);

    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));

    const toggle = screen.getByRole("button", { name: "Filters and layers panel" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Layers" })).not.toBeInTheDocument();
  });

  it("shows the sections again when expanded", async () => {
    const user = userEvent.setup();
    render(<SidePanel {...props()} />);

    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));
    await user.click(screen.getByRole("button", { name: "Filters and layers panel" }));

    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Layers" })).toBeInTheDocument();
  });

  it("toggles with Enter and Space from the keyboard", async () => {
    const user = userEvent.setup();
    render(<SidePanel {...props()} />);

    // Focused directly: the filters come before the button in tab order, as they do on screen.
    screen.getByRole("button", { name: "Filters and layers panel" }).focus();

    await user.keyboard("{Enter}");
    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();

    await user.keyboard(" ");
    expect(screen.getByRole("button", { name: "Filters and layers panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
  });
});

describe("SidePanel filters", () => {
  const LABELS = ["Happened", "Born", "Died", "Founded", "Ended", "Opened"];

  it("shows a Kinds group of six checkboxes in kind order", () => {
    render(<SidePanel {...props()} />);
    const group = screen.getByRole("group", { name: "Kinds" });
    const boxes = within(group).getAllByRole("checkbox");
    expect(boxes).toHaveLength(6);
    for (const [i, label] of LABELS.entries()) {
      expect(within(group).getByRole("checkbox", { name: label })).toBe(boxes[i]);
    }
  });

  it("checks exactly the selected kinds", () => {
    render(<SidePanel {...props({ kinds: ["BORN", "OPENED"] })} />);
    for (const label of LABELS) {
      const box = screen.getByRole("checkbox", { name: label });
      if (label === "Born" || label === "Opened") expect(box).toBeChecked();
      else expect(box).not.toBeChecked();
    }
  });

  it("unchecking a kind reports the remaining kinds", async () => {
    const user = userEvent.setup();
    const onKindsChange = vi.fn<(kinds: readonly Kind[]) => void>();
    render(<SidePanel {...props({ onKindsChange })} />);
    await user.click(screen.getByRole("checkbox", { name: "Born" }));
    expect(onKindsChange).toHaveBeenCalledTimes(1);
    expect(onKindsChange).toHaveBeenCalledWith(["HAPPENED", "DIED", "FOUNDED", "ENDED", "OPENED"]);
  });

  it("checking a kind reports the new list in kind order, not click order", async () => {
    const user = userEvent.setup();
    const onKindsChange = vi.fn<(kinds: readonly Kind[]) => void>();
    render(<SidePanel {...props({ kinds: ["OPENED", "DIED"], onKindsChange })} />);
    await user.click(screen.getByRole("checkbox", { name: "Happened" }));
    expect(onKindsChange).toHaveBeenCalledWith(["HAPPENED", "DIED", "OPENED"]);
  });

  it("unchecking the last kind reports an empty list", async () => {
    const user = userEvent.setup();
    const onKindsChange = vi.fn<(kinds: readonly Kind[]) => void>();
    render(<SidePanel {...props({ kinds: ["ENDED"], onKindsChange })} />);
    await user.click(screen.getByRole("checkbox", { name: "Ended" }));
    expect(onKindsChange).toHaveBeenCalledWith([]);
  });

  it("toggles a kind from the keyboard with Space", async () => {
    const user = userEvent.setup();
    const onKindsChange = vi.fn<(kinds: readonly Kind[]) => void>();
    render(<SidePanel {...props({ onKindsChange })} />);
    screen.getByRole("checkbox", { name: "Died" }).focus();
    await user.keyboard(" ");
    expect(onKindsChange).toHaveBeenCalledWith(["HAPPENED", "BORN", "FOUNDED", "ENDED", "OPENED"]);
  });

  it("has a \"Covered in at least\" slider from 0 to 100 with the current value", () => {
    render(<SidePanel {...props({ minImportance: 20 })} />);
    const slider = screen.getByRole("slider", { name: "Covered in at least" });
    expect(slider).toHaveAttribute("min", "0");
    expect(slider).toHaveAttribute("max", "100");
    expect(slider).toHaveValue("20");
  });

  it("puts the importance slider under the Filters heading", () => {
    render(<SidePanel {...props()} />);
    const filters = screen.getByRole("heading", { name: "Filters" }).closest("section");
    expect(filters).not.toBeNull();
    expect(filters).toContainElement(screen.getByRole("slider", { name: "Covered in at least" }));
  });

  it("explains the importance label in a tooltip", () => {
    render(<SidePanel {...props()} />);
    const label = screen.getByText("Covered in at least");
    expect(label).toHaveAttribute("title", "Number of Wikipedia language editions with an article on it");
  });

  it("shows the minimum importance as a number of languages", () => {
    render(<SidePanel {...props({ minImportance: 20 })} />);
    expect(screen.getByText("20 languages")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Covered in at least" })).toHaveAttribute("aria-valuetext", "20 languages");
    expect(screen.queryByText(/Wikipedias/)).not.toBeInTheDocument();
  });

  it("uses the singular for 1 language", () => {
    render(<SidePanel {...props({ minImportance: 1 })} />);
    expect(screen.getByText("1 language")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Covered in at least" })).toHaveAttribute("aria-valuetext", "1 language");
  });

  it("shows \"Any\" for a minimum importance of 0", () => {
    render(<SidePanel {...props({ minImportance: 0 })} />);
    expect(screen.getByText("Any")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Covered in at least" })).toHaveAttribute("aria-valuetext", "Any");
  });

  it("reports a moved slider as a number", () => {
    const onMinImportanceChange = vi.fn<(value: number) => void>();
    render(<SidePanel {...props({ onMinImportanceChange })} />);
    fireEvent.change(screen.getByRole("slider", { name: "Covered in at least" }), { target: { value: "35" } });
    expect(onMinImportanceChange).toHaveBeenCalledWith(35);
  });
});
