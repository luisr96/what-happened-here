import { describe, expect, it } from "vitest";
import type { components } from "../api/schema";
import { formatDate, formatRange, formatYear } from "./historicDate";

type DateView = components["schemas"]["DateView"];
type Precision = DateView["precision"];

function date(
  year: number,
  precision: Precision,
  month = 0,
  day = 0,
  calendar: "GREGORIAN" | "JULIAN" = "GREGORIAN",
): DateView {
  return { year, month, day, precision, calendar };
}

describe("formatDate", () => {
  it.each([
    ["day precision", date(1789, "DAY", 7, 14), "14 July 1789"],
    ["month precision", date(1789, "MONTH", 7), "July 1789"],
    ["year precision", date(1789, "YEAR"), "1789"],
    ["a CE year without an AD prefix", date(79, "YEAR"), "79"],
    ["a BC year", date(-44, "YEAR"), "44 BC"],
    ["a BC day", date(-44, "DAY", 3, 15), "15 March 44 BC"],
    ["a BC month", date(-44, "MONTH", 3), "March 44 BC"],
    ["1 BC (no year zero)", date(-1, "YEAR"), "1 BC"],
  ])("shows %s", (_, input, expected) => {
    expect(formatDate(input)).toBe(expected);
  });

  it.each([
    [1887, "1880s"],
    [1880, "1880s"],
    [2005, "2000s"],
    [1, "0s"],
    [9, "0s"],
    [-45, "40s BC"],
    [-49, "40s BC"],
    [-40, "40s BC"],
    [-5, "0s BC"],
    [-1, "0s BC"],
  ])("shows year %i at decade precision as %s", (year, expected) => {
    expect(formatDate(date(year, "DECADE"))).toBe(expected);
  });

  it.each([
    [1801, "19th century"],
    [1900, "19th century"],
    [1850, "19th century"],
    [1901, "20th century"],
    [50, "1st century"],
    [150, "2nd century"],
    [250, "3rd century"],
    [350, "4th century"],
    [1050, "11th century"],
    [1150, "12th century"],
    [1250, "13th century"],
    [2050, "21st century"],
    [2150, "22nd century"],
    [10050, "101st century"],
    [11050, "111th century"],
  ])("shows year %i at century precision as %s", (year, expected) => {
    expect(formatDate(date(year, "CENTURY"))).toBe(expected);
  });

  it.each([
    [1950, "2nd millennium"],
    [1001, "2nd millennium"],
    [2000, "2nd millennium"],
    [2001, "3rd millennium"],
    [500, "1st millennium"],
  ])("shows year %i at millennium precision as %s", (year, expected) => {
    expect(formatDate(date(year, "MILLENNIUM"))).toBe(expected);
  });

  it.each([
    [-7250, "7300–7201 BC"],
    [-44, "100–1 BC"],
    [-1, "100–1 BC"],
    [-100, "100–1 BC"],
    [-101, "200–101 BC"],
    [-250, "300–201 BC"],
  ])("shows BC year %i at century precision as the span %s", (year, expected) => {
    expect(formatDate(date(year, "CENTURY"))).toBe(expected);
  });

  it.each([
    [-4500, "5000–4001 BC"],
    [-1000, "1000–1 BC"],
    [-1, "1000–1 BC"],
    [-1001, "2000–1001 BC"],
    [-2500, "3000–2001 BC"],
  ])("shows BC year %i at millennium precision as the span %s", (year, expected) => {
    expect(formatDate(date(year, "MILLENNIUM"))).toBe(expected);
  });

  it.each([
    ["day", date(-44, "DAY", 3, 15, "JULIAN"), "15 March 44 BC (Julian)"],
    ["month", date(1582, "MONTH", 10, 0, "JULIAN"), "October 1582 (Julian)"],
  ])("marks a Julian date at %s precision", (_, input, expected) => {
    expect(formatDate(input)).toBe(expected);
  });

  it.each([
    ["year", date(1500, "YEAR", 0, 0, "JULIAN"), "1500"],
    ["decade", date(1505, "DECADE", 0, 0, "JULIAN"), "1500s"],
    ["century", date(1450, "CENTURY", 0, 0, "JULIAN"), "15th century"],
    ["millennium", date(-2500, "MILLENNIUM", 0, 0, "JULIAN"), "3000–2001 BC"],
    ["BC century", date(-44, "CENTURY", 0, 0, "JULIAN"), "100–1 BC"],
  ])("omits the Julian marker at %s precision", (_, input, expected) => {
    expect(formatDate(input)).toBe(expected);
  });

  it("covers every month name at month precision", () => {
    const names = [
      "January", "February", "March", "April", "May", "June",
      "July", "August", "September", "October", "November", "December",
    ];
    names.forEach((name, i) => {
      expect(formatDate(date(2000, "MONTH", i + 1))).toBe(`${name} 2000`);
    });
  });

  it("falls back to the year for an unknown precision without throwing", () => {
    // A value newer than the generated types (e.g. an API that added a precision).
    const unknown = {
      year: -44, month: 3, day: 15, precision: "HOUR", calendar: "GREGORIAN",
    } as unknown as DateView;
    expect(() => formatDate(unknown)).not.toThrow();
    expect(formatDate(unknown)).toBe("44 BC");
  });

  it.each([0, 13])("shows only the year when month %i is out of range", (month) => {
    expect(formatDate(date(1789, "MONTH", month))).toBe("1789");
    expect(formatDate(date(1789, "DAY", month, 14))).toBe("1789");
  });

  it("shows only month and year when the day is out of range", () => {
    expect(formatDate(date(1789, "DAY", 7, 0))).toBe("July 1789");
    expect(formatDate(date(1789, "DAY", 7, 32))).toBe("July 1789");
  });
});

describe("formatRange", () => {
  it("shows a single date when there is no end", () => {
    expect(formatRange(date(1789, "DAY", 7, 14))).toBe("14 July 1789");
    expect(formatRange(date(1789, "DAY", 7, 14), null)).toBe("14 July 1789");
  });

  it("joins start and end with a spaced en dash", () => {
    expect(formatRange(date(1914, "YEAR"), date(1918, "YEAR"))).toBe("1914 – 1918");
  });

  it("formats each side at its own precision", () => {
    expect(formatRange(date(-44, "DAY", 3, 15), date(1850, "CENTURY"))).toBe(
      "15 March 44 BC – 19th century",
    );
  });

  it("keeps the Julian marker on each side that has it", () => {
    expect(
      formatRange(date(1700, "DAY", 2, 1, "JULIAN"), date(1700, "YEAR", 0, 0, "JULIAN")),
    ).toBe("1 February 1700 (Julian) – 1700");
  });

  it("merges two BC centuries into one span from the start's first year to the end's last", () => {
    expect(formatRange(date(-5350, "CENTURY"), date(-4450, "CENTURY"))).toBe("5400–4401 BC");
  });

  it("merges two BC millennia into one span", () => {
    expect(formatRange(date(-4500, "MILLENNIUM"), date(-2500, "MILLENNIUM"))).toBe("5000–2001 BC");
  });

  it("merges a BC millennium start with a BC century end", () => {
    expect(formatRange(date(-4500, "MILLENNIUM"), date(-150, "CENTURY"))).toBe("5000–101 BC");
  });

  it("shows a single span when both sides are the same BC century", () => {
    expect(formatRange(date(-44, "CENTURY"), date(-10, "CENTURY"))).toBe("100–1 BC");
  });

  it("joins a BC millennium start and an AD end with a spaced en dash", () => {
    expect(formatRange(date(-4500, "MILLENNIUM"), date(1500, "YEAR"))).toBe("5000–4001 BC – 1500");
  });

  it("joins a BC century start and a finer BC end with a spaced en dash", () => {
    expect(formatRange(date(-250, "CENTURY"), date(-44, "YEAR"))).toBe("300–201 BC – 44 BC");
  });

  it("keeps AD centuries as ordinals in a range", () => {
    expect(formatRange(date(1250, "CENTURY"), date(1850, "CENTURY"))).toBe("13th century – 19th century");
  });

  it("shows the date once when both sides format identically", () => {
    expect(formatRange(date(1887, "DECADE"), date(1883, "DECADE"))).toBe("1880s");
  });
});

describe("formatYear", () => {
  it.each([
    [1789, "1789"],
    [79, "79"],
    [-44, "44 BC"],
    [-1, "1 BC"],
    [-10000, "10000 BC"],
  ])("labels year %i as %s", (year, expected) => {
    expect(formatYear(year)).toBe(expected);
  });
});
