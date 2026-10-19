import { useId, useState } from "react";
import type { Kind } from "../api/client";
import { KINDS, kindLabel, kindStyle } from "../map/kinds";
import type { Settings } from "../settings/settings";
import { SettingsMenu } from "../settings/SettingsMenu";
import { bucketFor, IMPORTANCE_BUCKETS } from "./importance";

export type SidePanelProps = {
  readonly kinds: readonly Kind[];
  readonly onKindsChange: (kinds: readonly Kind[]) => void;
  readonly minImportance: number;
  readonly onMinImportanceChange: (minImportance: number) => void;
  /** With both given, a Settings gear sits at the top of the panel. */
  readonly settings?: Settings;
  readonly onSettingsChange?: (next: Settings) => void;
};


export function SidePanel({
  kinds, onKindsChange, minImportance, onMinImportanceChange, settings, onSettingsChange,
}: SidePanelProps) {
  const [open, setOpen] = useState(true);
  const contentId = useId();
  const importanceId = useId();

  // Rebuilt from KINDS rather than appended, so the list keeps API order whatever the click order.
  const toggleKind = (kind: Kind, checked: boolean) =>
    onKindsChange(KINDS.filter((k) => (k === kind ? checked : kinds.includes(k))));

  return (
    <aside className="side-panel" aria-label="Filters" data-open={open}>
      <div id={contentId} className="side-panel__content panel" hidden={!open}>
        <section className="panel-section">
          <div className="panel-section__header">
            <h2>Filters</h2>
            {settings !== undefined && onSettingsChange !== undefined && (
              <SettingsMenu settings={settings} onChange={onSettingsChange} />
            )}
          </div>
          <fieldset className="kinds">
            <legend className="field-label">Events</legend>
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
          <div role="radiogroup" aria-labelledby={importanceId} className="importance">
            <span id={importanceId} className="field-label">Importance</span>
            <div className="segmented">
              {IMPORTANCE_BUCKETS.map((bucket) => (
                <label key={bucket.id} className="segmented__option" title={bucket.title}>
                  <input
                    type="radio"
                    className="segmented__input"
                    name={importanceId}
                    checked={bucketFor(minImportance).id === bucket.id}
                    onChange={() => onMinImportanceChange(bucket.minImportance)}
                  />
                  {bucket.label}
                </label>
              ))}
            </div>
          </div>
        </section>
      </div>
      <button
        type="button"
        className="side-panel__toggle"
        aria-expanded={open}
        aria-controls={contentId}
        aria-label="Filters panel"
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
