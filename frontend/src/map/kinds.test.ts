import { describe, expect, it } from "vitest";
import { KINDS, kindColor, kindLabel } from "./kinds";

describe("kinds", () => {
  it("lists the six kinds in API order", () => {
    expect(KINDS).toEqual(["HAPPENED", "BORN", "DIED", "FOUNDED", "ENDED", "OPENED"]);
  });

  it("labels each kind for people", () => {
    expect(KINDS.map(kindLabel)).toEqual(["Happened", "Born", "Died", "Founded", "Ended", "Opened"]);
  });

  it("gives each kind a distinct RGB colour", () => {
    const colors = KINDS.map(kindColor);
    for (const c of colors) {
      expect(c).toHaveLength(3);
      for (const v of c) {
        expect(Number.isInteger(v)).toBe(true);
        expect(v).toBeGreaterThanOrEqual(0);
        expect(v).toBeLessThanOrEqual(255);
      }
    }
    expect(new Set(colors.map((c) => c.join(","))).size).toBe(6);
  });
});
