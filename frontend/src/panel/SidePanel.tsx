import { useId, useState, type CSSProperties } from "react";
import type { Kind } from "../api/client";
import { KINDS, kindLabel, kindStyle } from "../map/kinds";
import type { Settings } from "../settings/settings";
import { SettingsMenu } from "../settings/SettingsMenu";

export type SidePanelProps = {
  readonly kinds: readonly Kind[];
  readonly onKindsChange: (kinds: readonly Kind[]) => void;
  readonly minImportance: number;
  readonly onMinImportanceChange: (minImportance: number) => void;
  /** With both given, a Settings gear sits at the top of the panel. */
  readonly settings?: Settings;
  readonly onSettingsChange?: (next: Settings) => void;
};

export const MAX_MIN_IMPORTANCE = 100;

export function SidePanel({
  kinds, onKindsChange, minImportance, onMinImportanceChange, settings, onSettingsChange,
}: SidePanelProps) {
  const [open, setOpen] = useState(true);
  const contentId = useId();
  const sliderId = useId();
  const importanceText = minImportance === 0 ? "Any"
    : `${minImportance} ${minImportance === 1 ? "language" : "languages"}`;

  // Rebuilt from KINDS rather than appended, so the list keeps API order whatever the click order.
  const toggleKind = (kind: Kind, checked: boolean) =>
    onKindsChange(KINDS.filter((k) => (k === kind ? checked : kinds.includes(k))));

  return (
    <aside className="side-panel" aria-label="Filters and layers" data-open={open}>
      <div id={contentId} className="side-panel__content panel" hidden={!open}>
        <section className="panel-section">
          <div className="panel-section__header">
            <h2>Filters</h2>
            {settings !== undefined && onSettingsChange !== undefined && (
              <SettingsMenu settings={settings} onChange={onSettingsChange} />
            )}
          </div>
          <fieldset className="kinds">
            <legend className="field-label">Kinds</legend>
            {KINDS.map((kind) => (
              <label key={kind} className="kinds__option" style={kindStyle(kind)}>
                <input
                  type="checkbox"
                  className="checkbox"
                  checked={kinds.includes(kind)}
                  onChange={(e) => toggleKind(kind, e.target.checked)}
                />
                <span className="kinds__swatch" aria-hidden="true" />
                {kindLabel(kind)}
              </label>
            ))}
          </fieldset>
          <div className="importance">
            <label
              htmlFor={sliderId}
              className="field-label"
              title="Number of Wikipedia language editions with an article on it"
            >
              Covered in at least
            </label>
            {/* A span, not <output>: that is a live "status" region, which the truncation hint owns. */}
            <span className="value-pill">{importanceText}</span>
            <input
              id={sliderId}
              type="range"
              className="range"
              style={{ "--fill": String(minImportance / MAX_MIN_IMPORTANCE) } as CSSProperties}
              min={0}
              max={MAX_MIN_IMPORTANCE}
              step={1}
              value={minImportance}
              aria-valuetext={importanceText}
              onChange={(e) => onMinImportanceChange(Number(e.target.value))}
            />
          </div>
        </section>
        <section className="panel-section">
          <div className="panel-section__header">
            <h2>Layers</h2>
          </div>
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
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
          <path
            d={open ? "M10 3.5 5.5 8l4.5 4.5" : "M6 3.5 10.5 8 6 12.5"}
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
    </aside>
  );
}
