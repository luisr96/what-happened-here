import type { RefObject } from "react";
import { useEvent, type EventDetails } from "../api/hooks";
import { formatRange } from "../format/historicDate";
import { kindLabel } from "../map/kinds";
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
      className="details-panel"
      aria-label="Event details"
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <button type="button" className="details-panel__close" aria-label="Close details" title="Close details" onClick={onClose}>
        ×
      </button>
      {onBack !== undefined && (
        <button type="button" className="panel-back" onClick={onBack}>
          <span aria-hidden="true">‹ </span>Back to list
        </button>
      )}
      {details.isPending && <p role="status">Loading…</p>}
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
      <p className="details-panel__when">
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
          <a href={articleUrl} target="_blank" rel="noopener noreferrer">Read on Wikipedia</a>
        )}
        <a href={`https://www.wikidata.org/wiki/${encodeURIComponent(event.itemId)}`} target="_blank" rel="noopener noreferrer">
          View on Wikidata
        </a>
      </p>
    </>
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
