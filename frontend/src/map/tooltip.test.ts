import { describe, expect, it } from "vitest";
import type { EventView } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { caesarDied, fijiFounded, lisbonEarthquake, mozartBorn } from "../test/apiFixtures";
import { tooltipText } from "./tooltip";

describe("tooltipText", () => {
  it("shows label, kind and a BC Julian day date on three lines", () => {
    expect(tooltipText(caesarDied)).toBe("Julius Caesar\nDied\n15 March 44 BC (Julian)");
  });

  it("falls back to the item id when the label is null", () => {
    expect(tooltipText(mozartBorn).split("\n").slice(0, 2)).toEqual(["Q254", "Born"]);
  });

  it("formats a century-precision date as the century, not a fake exact date", () => {
    const lines = tooltipText(fijiFounded).split("\n");
    expect(lines).toEqual(["Some village", "Founded", formatRange(fijiFounded.when, null)]);
    expect(lines[2]).toMatch(/century/i);
  });

  it("formats a start and end as a range", () => {
    const war: EventView = {
      ...lisbonEarthquake,
      label: "Thirty Years War",
      when: { year: 1618, month: 0, day: 0, precision: "YEAR", calendar: "GREGORIAN" },
      end: { year: 1648, month: 0, day: 0, precision: "YEAR", calendar: "GREGORIAN" },
    };
    const lines = tooltipText(war).split("\n");
    expect(lines).toEqual(["Thirty Years War", "Happened", formatRange(war.when, war.end)]);
    expect(lines[2]).toContain("1618");
    expect(lines[2]).toContain("1648");
  });
});
