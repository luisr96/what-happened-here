import { render, screen, within } from "@testing-library/react";
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
  return screen.getByRole("complementary", { name: "Filters" });
}

describe("SidePanel", () => {
  it("is a landmark named \"Filters\" with a Filters heading and no Layers section", () => {
    render(<SidePanel {...props()} />);

    expect(panel()).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Filters" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Layers" })).not.toBeInTheDocument();
  });

  it("starts open with a toggle that controls an existing element", () => {
    render(<SidePanel {...props()} />);

    const toggle = screen.getByRole("button", { name: "Filters panel" });
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

    await user.click(screen.getByRole("button", { name: "Filters panel" }));

    const toggle = screen.getByRole("button", { name: "Filters panel" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();
  });

  it("shows the sections again when expanded", async () => {
    const user = userEvent.setup();
    render(<SidePanel {...props()} />);

    await user.click(screen.getByRole("button", { name: "Filters panel" }));
    await user.click(screen.getByRole("button", { name: "Filters panel" }));

    expect(screen.getByRole("button", { name: "Filters panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
  });

  it("toggles with Enter and Space from the keyboard", async () => {
    const user = userEvent.setup();
    render(<SidePanel {...props()} />);

    // Focused directly: the filters come before the button in tab order, as they do on screen.
    screen.getByRole("button", { name: "Filters panel" }).focus();

    await user.keyboard("{Enter}");
    expect(screen.getByRole("button", { name: "Filters panel" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Filters" })).not.toBeInTheDocument();

    await user.keyboard(" ");
    expect(screen.getByRole("button", { name: "Filters panel" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { name: "Filters" })).toBeInTheDocument();
  });
});

describe("SidePanel filters", () => {
  const LABELS = ["Happened", "Born", "Died", "Founded", "Ended", "Opened"];

  it("shows an Events group of six checkboxes in kind order", () => {
    render(<SidePanel {...props()} />);
    const group = screen.getByRole("group", { name: "Events" });
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

  it("shows a radio group named Show with All, Notable and Iconic in that order", () => {
    render(<SidePanel {...props()} />);
    const group = screen.getByRole("radiogroup", { name: "Show" });
    const radios = within(group).getAllByRole("radio");
    expect(radios).toHaveLength(3);
    for (const [i, name] of ["All", "Notable", "Iconic"].entries()) {
      expect(within(group).getByRole("radio", { name })).toBe(radios[i]);
    }
    expect(screen.queryByRole("slider")).not.toBeInTheDocument();
    expect(screen.queryByText("Covered in at least")).not.toBeInTheDocument();
  });

  it("puts the importance radios under the Filters heading", () => {
    render(<SidePanel {...props()} />);
    const filters = screen.getByRole("heading", { name: "Filters" }).closest("section");
    expect(filters).not.toBeNull();
    expect(filters).toContainElement(screen.getByRole("radiogroup", { name: "Show" }));
  });

  it.each([
    ["All", "Every event"],
    ["Notable", "Covered by 50+ Wikipedia languages"],
    ["Iconic", "Covered by 100+ Wikipedia languages"],
  ])("explains %s in a title tooltip on its label", (name, title) => {
    render(<SidePanel {...props()} />);
    const radio = screen.getByRole("radio", { name });
    const label = radio.closest("label") ?? document.querySelector(`label[for="${radio.id}"]`);
    expect(label).not.toBeNull();
    expect(label).toHaveAttribute("title", title);
  });

  it.each([
    [0, "All"],
    [49, "All"],
    [50, "Notable"],
    [99, "Notable"],
    [100, "Iconic"],
    [500, "Iconic"],
  ])("checks exactly the bucket for minImportance %i (%s)", (minImportance, name) => {
    render(<SidePanel {...props({ minImportance })} />);
    for (const other of ["All", "Notable", "Iconic"]) {
      const radio = screen.getByRole("radio", { name: other });
      if (other === name) expect(radio).toBeChecked();
      else expect(radio).not.toBeChecked();
    }
  });

  it.each([
    ["Notable", 0, 50],
    ["Iconic", 0, 100],
    ["All", 100, 0],
  ])("choosing %s reports its threshold", async (name, start, expected) => {
    const user = userEvent.setup();
    const onMinImportanceChange = vi.fn<(value: number) => void>();
    render(<SidePanel {...props({ minImportance: start, onMinImportanceChange })} />);
    await user.click(screen.getByRole("radio", { name }));
    expect(onMinImportanceChange).toHaveBeenCalledTimes(1);
    expect(onMinImportanceChange).toHaveBeenCalledWith(expected);
  });

  it("moves between importance radios with the arrow keys", async () => {
    const user = userEvent.setup();
    const onMinImportanceChange = vi.fn<(value: number) => void>();
    render(<SidePanel {...props({ minImportance: 0, onMinImportanceChange })} />);
    await user.click(screen.getByRole("radio", { name: "All" }));
    onMinImportanceChange.mockClear();
    await user.keyboard("{ArrowRight}");
    expect(onMinImportanceChange).toHaveBeenLastCalledWith(50);
  });

  it("is controlled: the checked radio follows the minImportance prop", () => {
    const { rerender } = render(<SidePanel {...props({ minImportance: 0 })} />);
    rerender(<SidePanel {...props({ minImportance: 100 })} />);
    expect(screen.getByRole("radio", { name: "Iconic" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "All" })).not.toBeChecked();
  });
});
