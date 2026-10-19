import type { EventView } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { kindLabel } from "./kinds";

/** Hover text: label (the QID when there's no English label), kind, date. */
export function tooltipText(event: EventView): string {
  return [event.label ?? event.itemId, kindLabel(event.kind), formatRange(event.when, event.end)].join("\n");
}
