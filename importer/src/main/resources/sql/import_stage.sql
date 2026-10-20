-- The importer's staging tables: same names and columns as the API's sql/stage.sql (plus
-- instance_of, for the import report), so sql/resolve.sql runs unchanged, but permanent instead
-- of TEMP ... ON COMMIT DROP, because an import commits chunk by chunk and must survive a
-- restart. Emptied when a new import starts.

CREATE TABLE IF NOT EXISTS stage_events (
    id                  text NOT NULL,
    item_id             text NOT NULL,
    kind                text NOT NULL,
    label               text,
    wiki_title          text,
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
    place_qid           text,
    longitude           double precision,
    latitude            double precision,
    coord_precision_deg double precision,
    instance_of         text[]
);

CREATE TABLE IF NOT EXISTS stage_places (
    qid                 text NOT NULL,
    longitude           double precision NOT NULL,
    latitude            double precision NOT NULL,
    coord_precision_deg double precision
);

TRUNCATE stage_events, stage_places;
