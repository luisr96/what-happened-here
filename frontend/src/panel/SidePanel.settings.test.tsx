import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { KINDS } from "../map/kinds";
import type { Settings } from "../settings/settings";
import { SidePanel } from "./SidePanel";

describe("SidePanel settings", () => {
  it("shows the Settings gear in the left panel and passes changes on", async () => {
    const user = userEvent.setup();
    const onSettingsChange = vi.fn<(next: Settings) => void>();
    render(
      <SidePanel
        kinds={KINDS}
        onKindsChange={vi.fn()}
        minImportance={0}
        onMinImportanceChange={vi.fn()}
        settings={{ units: "km", mapStyle: "dark" }}
        onSettingsChange={onSettingsChange}
      />,
    );
    const panel = screen.getByRole("complementary", { name: "Filters and layers" });

    await user.click(within(panel).getByRole("button", { name: "Settings" }));
    await user.click(screen.getByRole("radio", { name: "mi" }));

    expect(onSettingsChange).toHaveBeenLastCalledWith({ units: "mi", mapStyle: "dark" });
  });
});
