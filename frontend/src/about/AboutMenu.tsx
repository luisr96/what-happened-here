import { useRef, useState } from "react";
import { AboutDialog } from "./AboutDialog";
import { ABOUT_SEEN_KEY } from "./aboutSeen";

/**
 * A "?" button that opens the About dialog. The dialog also opens by itself on a visitor's first
 * load; dismissing it is remembered in localStorage so later visits go straight to the map.
 */
export function AboutMenu() {
  const [open, setOpen] = useState(() => !seenBefore());
  const button = useRef<HTMLButtonElement>(null);

  const close = () => {
    setOpen(false);
    markSeen();
    button.current?.focus();
  };

  return (
    <>
      <button
        ref={button}
        type="button"
        className="ghost-icon-button about__button"
        aria-label="About"
        title="About"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
          <circle cx="8" cy="8" r="6.75" fill="none" stroke="currentColor" strokeWidth="1.5" />
          <path
            d="M6.1 6.2a1.95 1.95 0 1 1 2.7 1.8c-.5.2-.8.6-.8 1.1v.4"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
          <circle cx="8" cy="11.6" r="0.9" fill="currentColor" />
        </svg>
      </button>
      {open && <AboutDialog onClose={close} />}
    </>
  );
}

// Storage can be blocked (private modes, policies): then the dialog just opens on every load.
function seenBefore(): boolean {
  try {
    return localStorage.getItem(ABOUT_SEEN_KEY) === "1";
  } catch {
    return false;
  }
}

function markSeen() {
  try {
    localStorage.setItem(ABOUT_SEEN_KEY, "1");
  } catch {
    // Not remembered; harmless.
  }
}
