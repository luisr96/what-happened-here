import { useEffect, useId, useRef, useState } from "react";
import type { DistanceUnits } from "../map/nearby";
import { MAP_STYLES, type Settings } from "./settings";

export type SettingsMenuProps = {
  readonly settings: Settings;
  readonly onChange: (next: Settings) => void;
};

const UNIT_OPTIONS: readonly { readonly id: DistanceUnits; readonly label: string }[] = [
  { id: "km", label: "Kilometres" },
  { id: "mi", label: "Miles" },
];

/**
 * A gear button that opens a small non-modal popover of settings. Escape closes it and returns
 * focus to the gear; so does a click outside or on the gear again.
 */
export function SettingsMenu({ settings, onChange }: SettingsMenuProps) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const gear = useRef<HTMLButtonElement>(null);
  const popover = useRef<HTMLDivElement>(null);
  const popoverId = useId();
  const unitsId = useId();
  const styleId = useId();

  // Opening moves focus to the first checked choice, where arrow keys pick another.
  useEffect(() => {
    if (open) popover.current?.querySelector<HTMLInputElement>("input:checked")?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (e: PointerEvent) => {
      if (e.target instanceof Node && !root.current?.contains(e.target)) setOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutside);
    return () => document.removeEventListener("pointerdown", closeOnOutside);
  }, [open]);

  const close = () => {
    setOpen(false);
    gear.current?.focus();
  };

  return (
    <div className="settings" ref={root}>
      <button
        ref={gear}
        type="button"
        className="settings__gear"
        aria-label="Settings"
        title="Settings"
        aria-expanded={open}
        aria-controls={popoverId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
          <g fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round">
            <circle cx="8" cy="8" r="2.2" />
            <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4" />
          </g>
        </svg>
      </button>
      {open && (
        <div
          ref={popover}
          id={popoverId}
          className="settings__popover"
          role="dialog"
          aria-label="Settings"
          // Tabbing out to another control closes it, like a click outside. A click on a
          // non-focusable part inside also blurs, with no relatedTarget: that keeps it open.
          onBlur={(e) => {
            if (e.relatedTarget instanceof Node && !root.current?.contains(e.relatedTarget)) setOpen(false);
          }}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              e.stopPropagation();
              close();
            }
          }}
        >
          <div role="radiogroup" aria-labelledby={unitsId} className="settings__group">
            <span id={unitsId} className="settings__legend">Distance units</span>
            {UNIT_OPTIONS.map((option) => (
              <label key={option.id}>
                <input
                  type="radio"
                  name={unitsId}
                  checked={settings.units === option.id}
                  onChange={() => onChange({ ...settings, units: option.id })}
                />
                {option.label}
              </label>
            ))}
          </div>
          <div role="radiogroup" aria-labelledby={styleId} className="settings__group">
            <span id={styleId} className="settings__legend">Map style</span>
            {MAP_STYLES.map((style) => (
              <label key={style.id}>
                <input
                  type="radio"
                  name={styleId}
                  checked={settings.mapStyle === style.id}
                  onChange={() => onChange({ ...settings, mapStyle: style.id })}
                />
                {style.label}
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
