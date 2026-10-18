-- Staging tables for one load, filled with COPY; dropped when the transaction ends.
-- Reused by the step 6 importer.

CREATE TEMP TABLE stage_events (
    id                  text NOT NULL,
    item_id             text NOT NULL,
    kind                text NOT NULL,
    label               text,
    importance          integer NOT NULL,
    year                bigint NOT NULL,
    month               smallint NOT NULL,
    day                 smallint NOT NULL,
    precision           text NOT NULL,
    calendar            text NOT NULL,
    end_year            bigint,
    end_month           smallint,
    end_day             smallint,
    end_precision       text,
    end_calendar        text,
    earliest_year       bigint NOT NULL,
    latest_year         bigint NOT NULL,
    place_qid           text,             -- set for a place reference
    longitude           double precision, -- set for own coordinates
    latitude            double precision,
    coord_precision_deg double precision
) ON COMMIT DROP;

CREATE TEMP TABLE stage_places (
    qid                 text NOT NULL,
    longitude           double precision NOT NULL,
    latitude            double precision NOT NULL,
    coord_precision_deg double precision
) ON COMMIT DROP;
