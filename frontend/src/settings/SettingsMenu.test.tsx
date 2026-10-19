import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { type Settings } from "./settings";
import { SettingsMenu } from "./SettingsMenu";

function renderMenu(settings: Settings = { units: "km", mapStyle: "dark" }) {
  const onChange = vi.fn<(next: Settings) => void>();
  render(
    <div>
      <p>Outside</p>
      <SettingsMenu settings={settings} onChange={onChange} />
    </div>,
  );
  return { onChange };
}

const gear = () => screen.getByRole("button", { name: "Settings" });
const dialog = () => screen.queryByRole("dialog", { name: "Settings" });

async function open() {
  const user = userEvent.setup();
  await user.click(gear());
  const d = dialog();
  if (d === null) throw new Error("settings popover did not open");
  return { user, d };
}

describe("SettingsMenu", () => {
  it("is an icon-only gear button titled Settings, collapsed at first", () => {
    renderMenu();
    expect(gear()).toHaveAttribute("title", "Settings");
    expect(gear().querySelector("svg")).not.toBeNull();
    expect(gear().textContent?.trim()).toBe("");
    expect(gear()).toHaveAttribute("aria-expanded", "false");
    expect(dialog()).not.toBeInTheDocument();
  });

  it("opens a non-modal Settings dialog with the units and map style groups", async () => {
    renderMenu();
    const { d } = await open();
    expect(gear()).toHaveAttribute("aria-expanded", "true");
    expect(d).not.toHaveAttribute("aria-modal", "true");
    const units = within(d).getByRole("radiogroup", { name: "Unit" });
    expect(within(units).getAllByRole("radio")).toHaveLength(2);
    expect(within(units).getByRole("radio", { name: "km" })).toBeInTheDocument();
    expect(within(units).getByRole("radio", { name: "mi" })).toBeInTheDocument();
    const styles = within(d).getByRole("radiogroup", { name: "Map style" });
    for (const label of ["Dark", "Positron", "Liberty", "Bright", "Fiord"]) {
      expect(within(styles).getByRole("radio", { name: label })).toBeInTheDocument();
    }
  });

  it("checks the current settings", async () => {
    renderMenu({ units: "mi", mapStyle: "liberty" });
    const { d } = await open();
    expect(within(d).getByRole("radio", { name: "mi" })).toBeChecked();
    expect(within(d).getByRole("radio", { name: "km" })).not.toBeChecked();
    expect(within(d).getByRole("radio", { name: "Liberty" })).toBeChecked();
  });

  it("moves focus to the first checked radio when it opens", async () => {
    renderMenu({ units: "mi", mapStyle: "liberty" });
    const { d } = await open();
    await waitFor(() => expect(within(d).getByRole("radio", { name: "mi" })).toHaveFocus());
  });

  it("calls onChange with miles, keeping the map style", async () => {
    const { onChange } = renderMenu({ units: "km", mapStyle: "fiord" });
    const { user, d } = await open();
    await user.click(within(d).getByRole("radio", { name: "mi" }));
    expect(onChange).toHaveBeenLastCalledWith({ units: "mi", mapStyle: "fiord" });
  });

  it("calls onChange with a map style, keeping the units", async () => {
    const { onChange } = renderMenu({ units: "mi", mapStyle: "dark" });
    const { user, d } = await open();
    await user.click(within(d).getByRole("radio", { name: "Positron" }));
    expect(onChange).toHaveBeenLastCalledWith({ units: "mi", mapStyle: "positron" });
  });

  it("closes on Escape and returns focus to the gear", async () => {
    renderMenu();
    const { user } = await open();
    await user.keyboard("{Escape}");
    expect(dialog()).not.toBeInTheDocument();
    expect(gear()).toHaveFocus();
    expect(gear()).toHaveAttribute("aria-expanded", "false");
  });

  it("closes on a click outside", async () => {
    renderMenu();
    const { user } = await open();
    await user.click(screen.getByText("Outside"));
    expect(dialog()).not.toBeInTheDocument();
  });

  it("stays open on a click inside", async () => {
    renderMenu();
    const { user, d } = await open();
    await user.click(within(d).getByRole("radiogroup", { name: "Map style" }));
    expect(dialog()).toBeInTheDocument();
  });

  it("closes when the gear is clicked again", async () => {
    renderMenu();
    const { user } = await open();
    await user.click(gear());
    expect(dialog()).not.toBeInTheDocument();
    expect(gear()).toHaveAttribute("aria-expanded", "false");
  });
});

describe("SettingsMenu focus leaving", () => {
  it("closes when focus moves to a control outside the popover", async () => {
    const user = userEvent.setup();
    render(
      <>
        <SettingsMenu settings={{ units: "km", mapStyle: "dark" }} onChange={() => {}} />
        <button type="button">elsewhere</button>
      </>,
    );
    await user.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.getByRole("dialog", { name: "Settings" })).toBeInTheDocument();

    act(() => screen.getByRole("button", { name: "elsewhere" }).focus());

    expect(screen.queryByRole("dialog", { name: "Settings" })).not.toBeInTheDocument();
  });
});

describe("SettingsMenu unit abbreviations", () => {
  it("spells out km and mi in an abbr title", async () => {
    const user = userEvent.setup();
    render(<SettingsMenu settings={{ units: "km", mapStyle: "dark" }} onChange={() => {}} />);
    await user.click(screen.getByRole("button", { name: "Settings" }));

    expect(screen.getByTitle("kilometres")).toHaveTextContent("km");
    expect(screen.getByTitle("miles")).toHaveTextContent("mi");
    expect(screen.getByRole("radio", { name: "km" })).toBeChecked();
  });
});
