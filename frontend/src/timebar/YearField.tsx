import { useState } from "react";

export type YearFieldProps = {
  /** "From" or "To": names the box ("From year") and its era toggle ("From era"). */
  readonly label: string;
  /** Year in historical numbering (negative = BC); null while there is no data. */
  readonly year: number | null;
  readonly onCommit: (year: number) => void;
};

/**
 * A typed year: a positive number plus a BC/AD toggle, so BC never has to be typed. The number
 * applies on Enter or when leaving the box; the era applies as soon as it's flipped.
 */
export function YearField({ label, year, onCommit }: YearFieldProps) {
  // What's being typed, or null when the box shows `year`.
  const [draft, setDraft] = useState<string | null>(null);
  const [invalid, setInvalid] = useState(false);
  const bc = year !== null && year < 0;

  const commit = (): boolean => {
    if (draft === null) return true;
    const typed = Number(draft);
    if (draft.trim() === "" || !Number.isInteger(typed) || typed < 1) {
      setInvalid(true);
      return false;
    }
    onCommit(bc ? -typed : typed);
    setDraft(null);
    return true;
  };

  const revert = () => {
    setDraft(null);
    setInvalid(false);
  };

  return (
    <span className="year-field">
      <input
        type="number"
        className="year-field__input"
        min={1}
        step={1}
        aria-label={`${label} year`}
        aria-invalid={invalid}
        disabled={year === null}
        value={draft ?? (year === null ? "" : String(Math.abs(year)))}
        onChange={(e) => {
          setDraft(e.target.value);
          setInvalid(false);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") commit();
          if (e.key === "Escape") revert();
        }}
        onBlur={() => {
          if (!commit()) revert();
        }}
      />
      <button
        type="button"
        className="year-field__era"
        data-era={bc ? "bc" : "ad"}
        aria-label={`${label} era`}
        title={bc ? "Switch to AD" : "Switch to BC"}
        disabled={year === null}
        onClick={() => {
          if (year !== null) onCommit(-year);
        }}
      >
        {bc ? "BC" : "AD"}
      </button>
    </span>
  );
}
