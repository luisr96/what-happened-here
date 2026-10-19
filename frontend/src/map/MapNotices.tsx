export type MapNoticesProps = {
  readonly noKinds: boolean;
  readonly error: string | null;
};

/**
 * Messages about what the map shows: an empty kind filter or a failed request. A capped result
 * (`truncated`) gets no notice: the map then shows the most important events, which is what it
 * aims to show anyway.
 */
export function MapNotices({ noKinds, error }: MapNoticesProps) {
  return (
    <div className="map-notices">
      {error !== null && <p role="alert" className="map-notice map-notice--error">{error}</p>}
      {noKinds && <p className="map-notice">Select at least one kind</p>}
    </div>
  );
}
