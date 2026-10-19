-- Rebuilds timeline_cells from events (see V2__timeline_cells.sql). Run in the same transaction
-- as any change to events: the loader after resolve.sql, and later the importer and follower.
-- The (level, size) pairs must match TimelineCells.CELL_SIZES.

TRUNCATE timeline_cells;

INSERT INTO timeline_cells (level, cell_x, cell_y, decade, kind, importance, n)
SELECT l.level,
       floor(ST_X(e.geom)::numeric / l.size)::integer,
       floor(ST_Y(e.geom)::numeric / l.size)::integer,
       floor(e.earliest_year / 10.0)::bigint * 10,
       e.kind,
       e.importance,
       count(*)
FROM events e
CROSS JOIN (VALUES (0, 10.0), (1, 1.0), (2, 0.1)) AS l (level, size)
GROUP BY 1, 2, 3, 4, 5, 6;
