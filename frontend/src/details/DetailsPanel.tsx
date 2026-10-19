import type { RefObject } from "react";
import { useEvent, type EventDetails } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { kindLabel, kindStyle } from "../map/kinds";
import { usePanelFocus } from "../util/usePanelFocus";

export type DetailsPanelProps = {
  readonly id: string;
  readonly onClose: () => void;
  /** Where focus goes on close when whatever had it before the panel opened can't take it back. */
  readonly returnFocusTo?: RefObject<HTMLElement | null>;
  /** When given (opened from the "what happened here" list), a "Back to list" button calls it. */
  readonly onBack?: () => void;
};

/**
 * The selected event: what, when, its English Wikipedia summary and links. Fetches its own data;
 * App only says which event is selected. A new id shows "Loading…", never the previous event.
 */
export function DetailsPanel({ id, onClose, returnFocusTo, onBack }: DetailsPanelProps) {
  const details = useEvent(id);
  const panel = usePanelFocus<HTMLElement>(id, returnFocusTo);

  return (
    <aside
      ref={panel}
      tabIndex={-1}
      className="details-panel panel"
      aria-label="Event details"
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <button type="button" className="details-panel__close" aria-label="Close details" title="Close details" onClick={onClose}>
        <CloseIcon />
      </button>
      {onBack !== undefined && (
        <button type="button" className="panel-back" onClick={onBack}>
          {/* Drawn exactly cap-height tall and sat on the baseline (see .panel-back svg), so the
              chevron lines up with the capitals whatever the font's metrics. */}
          <svg viewBox="0 0 7 10" width="7" height="10" aria-hidden="true" focusable="false">
            <path d="M5.5 1 1.5 5l4 4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Back to list
        </button>
      )}
      {details.isPending && <p role="status" className="details-panel__note">Loading…</p>}
      {details.isError && <p role="alert" className="details-panel__error">{details.error.message}</p>}
      {details.isSuccess && <Details {...details.data} />}
    </aside>
  );
}

function Details({ event, wikipedia }: EventDetails) {
  // The URLs come from Wikipedia through our API; only plain https ones become links or images.
  const articleUrl = httpsOrNull(wikipedia?.url);
  const thumbnailUrl = httpsOrNull(wikipedia?.thumbnailUrl);
  return (
    <>
      {thumbnailUrl !== null && (
        // Decorative: the heading and extract already say what it shows.
        <img className="details-panel__image" src={thumbnailUrl} alt="" />
      )}
      <h2>{event.label ?? event.itemId}</h2>
      <p className="details-panel__when" style={kindStyle(event.kind)}>
        {`${kindLabel(event.kind)} ${formatRange(event.when, event.end)}`}
      </p>
      {event.approximate && <p className="details-panel__note">Approximate location</p>}
      {wikipedia === null ? (
        <p className="details-panel__note">No English Wikipedia article.</p>
      ) : (
        <p className="details-panel__extract">{wikipedia.extract}</p>
      )}
      <p className="details-panel__links">
        {articleUrl !== null && (
          <a className="link-button link-button--primary" href={articleUrl} target="_blank" rel="noopener noreferrer">
            Read on Wikipedia
            <ExternalIcon />
          </a>
        )}
        <a
          className="link-button"
          href={`https://www.wikidata.org/wiki/${encodeURIComponent(event.itemId)}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          View on Wikidata
          <ExternalIcon />
        </a>
      </p>
    </>
  );
}

/** The close button's cross (NearbyPanel draws the same one). */
function CloseIcon() {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
      <path d="M4 4l8 8M12 4l-8 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

/** A small "opens elsewhere" arrow; hidden from assistive tech so link names stay as written. */
function ExternalIcon() {
  return (
    <svg viewBox="0 0 12 12" width="11" height="11" aria-hidden="true" focusable="false">
      <path d="M4.5 2.5h5v5M9.5 2.5l-7 7" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function httpsOrNull(url: string | null | undefined): string | null {
  if (url == null) return null;
  try {
    return new URL(url).protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}
