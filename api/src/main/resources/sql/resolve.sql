-- Replaces events and places with the staged load (CLAUDE.md, Importer flow step 3): full
-- replace only, never incremental. Keeps only places some event uses, copies their coordinates
-- onto those events, and drops events whose place is unknown. Reused by the step 6 importer,
-- which at full-dump scale should drop the events indexes before the insert and rebuild them
-- after (or load a fresh table and swap).

-- Temp tables are never auto-analyzed; without statistics the planner guesses the join.
ANALYZE stage_events;
ANALYZE stage_places;

TRUNCATE events, places;

-- A place can appear twice in the input; keep the most precise row (unknown precision last).
INSERT INTO places (qid, geom, coord_precision_deg)
SELECT DISTINCT ON (p.qid)
       p.qid, ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326), p.coord_precision_deg
FROM stage_places p
WHERE p.qid IN (SELECT place_qid FROM stage_events WHERE place_qid IS NOT NULL)
ORDER BY p.qid, p.coord_precision_deg ASC NULLS LAST;

INSERT INTO events (id, item_id, kind, label, wiki_title, importance,
                    year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar,
                    earliest_year, latest_year, place_qid, geom, coord_precision_deg)
SELECT e.id, e.item_id, e.kind, e.label, e.wiki_title, e.importance,
       e.year, e.month, e.day, e.precision, e.calendar,
       e.end_year, e.end_month, e.end_day, e.end_precision, e.end_calendar,
       e.earliest_year, e.latest_year, e.place_qid,
       COALESCE(p.geom, ST_SetSRID(ST_MakePoint(e.longitude, e.latitude), 4326)),
       CASE WHEN e.place_qid IS NULL THEN e.coord_precision_deg ELSE p.coord_precision_deg END
FROM stage_events e
LEFT JOIN places p ON p.qid = e.place_qid
WHERE e.place_qid IS NULL OR p.qid IS NOT NULL;
