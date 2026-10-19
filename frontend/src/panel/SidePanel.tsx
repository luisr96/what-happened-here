import { useId, useState } from "react";
import type { Kind } from "../api/client";
import { KINDS, kindColor, kindLabel } from "../map/kinds";

export type SidePanelProps = {
  readonly kinds: readonly Kind[];
  readonly onKindsChange: (kinds: readonly Kind[]) => void;
  readonly minImportance: number;
  readonly onMinImportanceChange: (minImportance: number) => void;
};

export const MAX_MIN_IMPORTANCE = 100;

export function SidePanel({ kinds, onKindsChange, minImportance, onMinImportanceChange }: SidePanelProps) {
  const [open, setOpen] = useState(true);
  const contentId = useId();
  const sliderId = useId();
  const importanceText = minImportance === 0 ? "Any" : `${minImportance} Wikipedias`;

  // Rebuilt from KINDS rather than appended, so the list keeps API order whatever the click order.
  const toggleKind = (kind: Kind, checked: boolean) =>
    onKindsChange(KINDS.filter((k) => (k === kind ? checked : kinds.includes(k))));

  return (
    <aside className="side-panel" aria-label="Filters and layers" data-open={open}>
      <div id={contentId} className="side-panel__content" hidden={!open}>
        <section>
          <h2>Filters</h2>
          <fieldset className="kinds">
            <legend>Kinds</legend>
            {KINDS.map((kind) => (
              <label key={kind} className="kinds__option">
                <input
                  type="checkbox"
                  checked={kinds.includes(kind)}
                  onChange={(e) => toggleKind(kind, e.target.checked)}
                />
                <span className="kinds__swatch" aria-hidden="true" style={{ background: `rgb(${kindColor(kind).join(" ")})` }} />
                {kindLabel(kind)}
              </label>
            ))}
          </fieldset>
          <div className="importance">
            <label htmlFor={sliderId}>Minimum importance</label>
            {/* A span, not <output>: that is a live "status" region, which the truncation hint owns. */}
            <span className="importance__value">{importanceText}</span>
            <input
              id={sliderId}
              type="range"
              min={0}
              max={MAX_MIN_IMPORTANCE}
              step={1}
              value={minImportance}
              aria-valuetext={importanceText}
              onChange={(e) => onMinImportanceChange(Number(e.target.value))}
            />
          </div>
        </section>
        <section>
          <h2>Layers</h2>
        </section>
      </div>
      <button
        type="button"
        className="side-panel__toggle"
        aria-expanded={open}
        aria-controls={contentId}
        aria-label="Filters and layers panel"
        onClick={() => setOpen(!open)}
      >
        {open ? "‹" : "›"}
      </button>
    </aside>
  );
}
