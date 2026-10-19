import type { components } from "../api/schema";

type DateView = components["schemas"]["DateView"];

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

/** Year in historical numbering (negative = BC, never 0) as shown on an axis: "1789", "44 BC". */
export function formatYear(year: number): string {
  return year < 0 ? `${-year} BC` : String(year);
}

/**
 * A date at the precision Wikidata states, never more exact: "14 July 1789", "1880s",
 * "19th century". Periods follow Wikidata (and the backend's `HistoricDate`): decades count
 * from 0, centuries and millennia from 1, BC mirrors CE. An out-of-range month or day falls
 * back to the next coarser precision.
 */
export function formatDate(date: DateView): string {
  const year = date.year;
  const bc = year < 0 ? " BC" : "";
  const magnitude = Math.abs(year);
  const month = MONTHS[date.month - 1];
  const day = date.day >= 1 && date.day <= 31 ? date.day : undefined;
  // The calendar only changes the day and month shown, so only those carry the marker.
  const julian = date.calendar === "JULIAN" ? " (Julian)" : "";

  switch (date.precision) {
    case "DAY":
      if (month !== undefined && day !== undefined) {
        return `${day} ${month} ${formatYear(year)}${julian}`;
      }
      return formatDate({ ...date, precision: "MONTH" });
    case "MONTH":
      return month === undefined ? formatYear(year) : `${month} ${formatYear(year)}${julian}`;
    case "DECADE":
      return `${Math.floor(magnitude / 10) * 10}s${bc}`;
    case "CENTURY":
    case "MILLENNIUM": {
      const span = bcSpan(date);
      if (span !== null) return `${span.first}–${span.last} BC`;
      const unit = date.precision === "CENTURY" ? 100 : 1000;
      return `${ordinal(Math.ceil(magnitude / unit))} ${date.precision === "CENTURY" ? "century" : "millennium"}`;
    }
    default:
      return formatYear(year);
  }
}

/** "1914 – 1918"; one side alone when there's no end or both sides read the same. */
export function formatRange(start: DateView, end?: DateView | null): string {
  const from = formatDate(start);
  if (!end) return from;
  // Two BC periods read as one span: "5400–4401 BC", not "5400–5301 BC – 4500–4401 BC".
  const startSpan = bcSpan(start);
  const endSpan = bcSpan(end);
  if (startSpan !== null && endSpan !== null) return `${startSpan.first}–${endSpan.last} BC`;
  const to = formatDate(end);
  return from === to ? from : `${from} – ${to}`;
}

/**
 * A BC century or millennium as the years it covers, oldest first ("73rd century BC" is
 * 7300–7201 BC): ordinals that far back are hard to read. Null for anything else.
 */
function bcSpan(date: DateView): { first: number; last: number } | null {
  if (date.year >= 0 || (date.precision !== "CENTURY" && date.precision !== "MILLENNIUM")) return null;
  const unit = date.precision === "CENTURY" ? 100 : 1000;
  const first = Math.ceil(-date.year / unit) * unit;
  return { first, last: first - unit + 1 };
}

/** 11th–13th take "th" whatever their last digit: 111th, but 101st. */
function ordinal(n: number): string {
  const lastTwo = n % 100;
  if (lastTwo >= 11 && lastTwo <= 13) return `${n}th`;
  const suffix = ["th", "st", "nd", "rd"][n % 10] ?? "th";
  return `${n}${suffix}`;
}
