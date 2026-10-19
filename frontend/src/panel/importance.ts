/**
 * Importance as three plain words instead of a number. Importance is how many Wikipedia
 * language editions have an article on the item. Thresholds chosen with the sample (50+ is under
 * a tenth of it, 100+ about 1%: countries, major capitals, household names); retune after the
 * full import if needed.
 */
export const IMPORTANCE_BUCKETS = [
  { id: "all", label: "All", minImportance: 0, title: "Every event" },
  { id: "notable", label: "Notable", minImportance: 50, title: "Covered by 50+ Wikipedia languages" },
  { id: "famous", label: "Famous", minImportance: 100, title: "Covered by 100+ Wikipedia languages" },
] as const;

export type ImportanceBucket = (typeof IMPORTANCE_BUCKETS)[number];

/** The bucket a threshold falls in: the strictest one it reaches (49 is "All", 50..99 "Notable"). */
export function bucketFor(minImportance: number): ImportanceBucket {
  return IMPORTANCE_BUCKETS.findLast((b) => b.minImportance <= minImportance) ?? IMPORTANCE_BUCKETS[0];
}
