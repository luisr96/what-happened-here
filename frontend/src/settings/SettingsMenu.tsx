import { useEffect, useId, useRef, useState } from "react";
import type { DistanceUnits } from "../map/nearby";
import { MAP_STYLES, type Settings } from "./settings";

export type SettingsMenuProps = {
  readonly settings: Settings;
  readonly onChange: (next: Settings) => void;
};

// Short labels on screen; <abbr> gives the full word as a tooltip and to screen readers that
// expand abbreviations, while the accessible name stays the visible "km" / "mi".
const UNIT_OPTIONS: readonly { readonly id: DistanceUnits; readonly label: string; readonly title: string }[] = [
  { id: "km", label: "km", title: "kilometres" },
  { id: "mi", label: "mi", title: "miles" },
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
        className="ghost-icon-button settings__gear"
        aria-label="Settings"
        title="Settings"
        aria-expanded={open}
        aria-controls={popoverId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
          {/* A solid six-lobed cog with rounded teeth and a round hole (even-odd fill). */}
          <path
            fill="currentColor"
            fillRule="evenodd"
            d="M15.65 8.00L15.63 8.50L15.56 9.00L15.39 9.47L14.95 9.86L14.14 10.08L13.51 10.28L13.19 10.56L12.98 10.88L12.81 11.22L12.73 11.63L12.87 12.27L13.09 13.09L12.97 13.67L12.64 14.05L12.25 14.36L11.83 14.63L11.38 14.86L10.92 15.05L10.42 15.14L9.86 14.95L9.26 14.36L8.78 13.92L8.38 13.78L8.00 13.75L7.62 13.78L7.22 13.92L6.74 14.36L6.14 14.95L5.58 15.14L5.08 15.05L4.62 14.86L4.18 14.63L3.75 14.36L3.36 14.05L3.03 13.67L2.91 13.09L3.13 12.27L3.27 11.63L3.19 11.22L3.02 10.88L2.81 10.56L2.49 10.28L1.86 10.08L1.05 9.86L0.61 9.47L0.44 9.00L0.37 8.50L0.35 8.00L0.37 7.50L0.44 7.00L0.61 6.53L1.05 6.14L1.86 5.92L2.49 5.72L2.81 5.44L3.02 5.13L3.19 4.78L3.27 4.37L3.13 3.73L2.91 2.91L3.03 2.33L3.36 1.95L3.75 1.64L4.17 1.37L4.62 1.14L5.08 0.95L5.58 0.86L6.14 1.05L6.74 1.64L7.22 2.08L7.62 2.22L8.00 2.25L8.38 2.22L8.78 2.08L9.26 1.64L9.86 1.05L10.42 0.86L10.92 0.95L11.38 1.14L11.83 1.37L12.25 1.64L12.64 1.95L12.97 2.33L13.09 2.91L12.87 3.73L12.73 4.37L12.81 4.78L12.98 5.12L13.19 5.44L13.51 5.72L14.14 5.92L14.95 6.14L15.39 6.53L15.56 7.00L15.63 7.50ZM10.5 8a2.5 2.5 0 1 0-5 0a2.5 2.5 0 1 0 5 0Z"
          />
        </svg>
      </button>
      {open && (
        <div
          ref={popover}
          id={popoverId}
          className="settings__popover popover"
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
            <span id={unitsId} className="settings__legend">Unit</span>
            <div className="segmented">
              {UNIT_OPTIONS.map((option) => (
                <label key={option.id} className="segmented__option">
                  <input
                    type="radio"
                    className="segmented__input"
                    name={unitsId}
                    checked={settings.units === option.id}
                    onChange={() => onChange({ ...settings, units: option.id })}
                  />
                  <abbr title={option.title}>{option.label}</abbr>
                </label>
              ))}
            </div>
          </div>
          <div role="radiogroup" aria-labelledby={styleId} className="settings__group">
            <span id={styleId} className="settings__legend">Map style</span>
            {MAP_STYLES.map((style) => (
              <label key={style.id} className="settings__option">
                <input
                  type="radio"
                  className="radio"
                  name={styleId}
                  checked={settings.mapStyle === style.id}
                  onChange={() => onChange({ ...settings, mapStyle: style.id })}
                />
                {style.label}
                <span className="settings__thumb" data-style={style.id} aria-hidden="true" />
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
