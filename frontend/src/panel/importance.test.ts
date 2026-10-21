import { describe, expect, it } from "vitest";
import { IMPORTANCE_BUCKETS, bucketFor } from "./importance";

describe("IMPORTANCE_BUCKETS", () => {
  it("lists All, Notable and Iconic with thresholds 0, 50 and 100", () => {
    expect(IMPORTANCE_BUCKETS.map((b) => [b.id, b.label, b.minImportance])).toEqual([
      ["all", "All", 0],
      ["notable", "Notable", 50],
      ["iconic", "Iconic", 100],
    ]);
  });

  it("explains each bucket in its title", () => {
    expect(IMPORTANCE_BUCKETS.map((b) => b.title)).toEqual([
      "Every event",
      "Covered by 50+ Wikipedia languages",
      "Covered by 100+ Wikipedia languages",
    ]);
  });
});

describe("bucketFor", () => {
  it.each([
    [0, "all"],
    [49, "all"],
    [50, "notable"],
    [99, "notable"],
    [100, "iconic"],
    [500, "iconic"],
  ])("maps minImportance %i to %s", (minImportance, id) => {
    expect(bucketFor(minImportance).id).toBe(id);
  });
});
