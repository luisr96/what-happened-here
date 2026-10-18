CREATE EXTENSION IF NOT EXISTS postgis;

-- geometry, not geography: a map viewport is a lon/lat rectangle, which geometry matches exactly
-- (geography would bend its edges along great circles). Radius queries in metres cast to
-- geography, served by events_geog_idx.

-- Items with Earth coordinates that some event refers to by QID (P276, P19, P20, P159, P131).
-- Joined by QID only, so no spatial index.
CREATE TABLE places (
    qid                 text PRIMARY KEY,
    geom                geometry(Point, 4326) NOT NULL,
    coord_precision_deg double precision -- null: precision unknown
);

-- One row per (item, kind); dates keep Wikidata's precision, with fields below it set to 0.
CREATE TABLE events (
    id                  text PRIMARY KEY, -- e.g. 'Q254:BORN', stable for the follower's upserts
    item_id             text NOT NULL,
    kind                text NOT NULL
        CHECK (kind IN ('HAPPENED', 'BORN', 'DIED', 'FOUNDED', 'ENDED', 'OPENED')),
    CONSTRAINT events_id_matches CHECK (id = item_id || ':' || kind),
    label               text,
    importance          integer NOT NULL CHECK (importance >= 0),

    year                bigint NOT NULL, -- negative for BC, as written
    month               smallint NOT NULL CHECK (month BETWEEN 0 AND 12),
    day                 smallint NOT NULL CHECK (day BETWEEN 0 AND 31),
    precision           text NOT NULL
        CHECK (precision IN ('DAY', 'MONTH', 'YEAR', 'DECADE', 'CENTURY', 'MILLENNIUM')),
    calendar            text NOT NULL CHECK (calendar IN ('GREGORIAN', 'JULIAN')),

    end_year            bigint,
    end_month           smallint CHECK (end_month BETWEEN 0 AND 12),
    end_day             smallint CHECK (end_day BETWEEN 0 AND 31),
    end_precision       text
        CHECK (end_precision IN ('DAY', 'MONTH', 'YEAR', 'DECADE', 'CENTURY', 'MILLENNIUM')),
    end_calendar        text CHECK (end_calendar IN ('GREGORIAN', 'JULIAN')),
    CONSTRAINT events_end_date_complete CHECK (
        num_nulls(end_year, end_month, end_day, end_precision, end_calendar) IN (0, 5)),

    -- Years the event may fall in (a century spans 100), used for filtering and the timeline.
    earliest_year       bigint NOT NULL,
    latest_year         bigint NOT NULL,
    CONSTRAINT events_year_range CHECK (latest_year >= earliest_year),

    place_qid           text REFERENCES places (qid), -- null when the item has its own coordinates
    geom                geometry(Point, 4326) NOT NULL, -- own, or copied from places on load
    coord_precision_deg double precision
);

CREATE INDEX events_geom_idx ON events USING gist (geom);
CREATE INDEX events_geog_idx ON events USING gist ((geom::geography));
CREATE INDEX events_years_idx ON events (earliest_year, latest_year);
CREATE INDEX events_importance_idx ON events (importance DESC);
CREATE INDEX events_item_id_idx ON events (item_id);
-- Finds the events to update when a place moves, and checks the foreign key on place deletes.
CREATE INDEX events_place_qid_idx ON events (place_qid) WHERE place_qid IS NOT NULL;
