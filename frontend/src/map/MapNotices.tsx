export type MapNoticesProps = {
  readonly noKinds: boolean;
  readonly truncated: boolean;
  readonly error: string | null;
};

/** Messages about what the map shows: an empty kind filter, a capped result, a failed request. */
export function MapNotices({ noKinds, truncated, error }: MapNoticesProps) {
  return (
    <div className="map-notices">
      {error !== null && <p role="alert" className="map-notice map-notice--error">{error}</p>}
      {noKinds && <p className="map-notice">Select at least one kind</p>}
      {/* Always mounted, filled only when truncated: a live region announces text added to it. */}
      <p role="status" className="map-notice" hidden={!truncated}>
        {truncated && "Showing the most important events here. Zoom in or raise the minimum importance to see more."}
      </p>
    </div>
  );
}
