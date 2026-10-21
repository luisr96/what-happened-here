import { useEffect, useId, useRef, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { siMaplibre, siPostgresql, siReact, siSpringboot, siWikidata, type SimpleIcon } from "simple-icons";
import { useStatsSummary } from "../api/hooks";
import { AboutNumbers } from "./AboutNumbers";

const REPO_URL = "https://github.com/luisr96/what-happened-here";

/** simple-icons has no deck.gl mark, so a generic one: three stacked layers on a 24×24 grid. */
const LAYERS_ICON: Pick<SimpleIcon, "path"> = {
  path: "M12 2 2 7l10 5 10-5zM4.2 10.9 2 12l10 5 10-5-2.2-1.1L12 14.8zm0 5L2 17l10 5 10-5-2.2-1.1L12 19.8z",
};

/** The stack, data to map, each with its official mark from simple-icons where there is one. */
const STACK: readonly { readonly name: string; readonly icon: Pick<SimpleIcon, "path"> }[] = [
  { name: "Wikidata", icon: siWikidata },
  { name: "Spring Boot", icon: siSpringboot },
  { name: "PostgreSQL + PostGIS", icon: siPostgresql },
  { name: "React", icon: siReact },
  { name: "MapLibre", icon: siMaplibre },
  { name: "deck.gl", icon: LAYERS_ICON },
];
const FOCUSABLE = "a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), "
  + "textarea:not(:disabled), [tabindex]:not([tabindex='-1'])";

export type AboutDialogProps = {
  readonly onClose: () => void;
};

/**
 * A modal with what the app is, the dataset in numbers and how it's built. Focus moves into it on
 * open and stays there (Tab cycles); Escape, Close or a click on the backdrop dismisses it.
 */
export function AboutDialog({ onClose }: AboutDialogProps) {
  const stats = useStatsSummary();
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  // Only a press that also started on the backdrop closes: selecting text in the dialog and
  // letting go outside it would otherwise click the backdrop.
  const pressedBackdrop = useRef(false);

  useEffect(() => {
    closeButton.current?.focus();
  }, []);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      onClose();
      return;
    }
    if (e.key === "Tab") trapFocus(e, dialog.current);
  };

  // Portalled to <body>: the side panel's backdrop-filter would otherwise become the containing
  // block of this fixed overlay and squeeze it into the panel.
  return createPortal(
    <div
      className="about__backdrop"
      onPointerDown={(e) => {
        pressedBackdrop.current = e.target === e.currentTarget;
      }}
      onClick={(e) => {
        if (pressedBackdrop.current && e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialog}
        className="about__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={onKeyDown}
      >
        <header className="about__header">
          <h2 id={titleId} className="about__title">About the app</h2>
          <button
            ref={closeButton}
            type="button"
            className="ghost-icon-button about__close"
            title="Close"
            onClick={onClose}
          >
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
              <path
                d="M4 4l8 8M12 4l-8 8"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            </svg>
            <span className="visually-hidden">Close</span>
          </button>
        </header>

        <p className="about__lead">
          Explore what happened anywhere on Earth, across time, according to Wikidata/Wikipedia.
        </p>
        <ol className="about__steps">
          <li>Pan and zoom the map to somewhere.</li>
          <li>Drag the range on the timeline at the bottom to choose a period.</li>
          <li>Hover a point to see what happened; click it to get more details.</li>
          <li>Use the side panel to filter or change settings.</li>
        </ol>
        <p className="about__source-note">
          The events written about in the most languages appear first.
        </p>

        <AboutNumbers stats={stats} />

        <section className="about__section">
          <h3 className="about__label">How it's built</h3>
          <ul className="about__stack">
            {STACK.map(({ name, icon }) => (
              <li key={name} className="about__tech">
                <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true" focusable="false">
                  <path d={icon.path} fill="currentColor" />
                </svg>
                {name}
              </li>
            ))}
          </ul>
        </section>

        <footer className="about__footer">
          <a
            className="link-button about__source"
            href={REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
              <path
                d="M5.5 4.5L2 8l3.5 3.5M10.5 4.5L14 8l-3.5 3.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            Source on GitHub
          </a>
        </footer>
      </div>
    </div>,
    document.body,
  );
}

/** Keeps Tab and Shift+Tab inside the dialog, as a modal should. */
function trapFocus(e: KeyboardEvent, root: HTMLElement | null) {
  if (root === null) return;
  const focusable = root.querySelectorAll<HTMLElement>(FOCUSABLE);
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (first === undefined || last === undefined) return;
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault();
    last.focus();
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault();
    first.focus();
  }
}
