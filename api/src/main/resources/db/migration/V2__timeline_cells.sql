-- Event counts per grid cell, decade, kind and importance, so /stats/timeline sums a few rows
-- instead of counting every event in a large viewport. Rebuilt from events by
-- sql/timeline_cells.sql. Levels: 0 = 10° cells, 1 = 1°, 2 = 0.1°. A point's cell is
-- floor(lon / size), floor(lat / size), computed in numeric so 0.3 / 0.1 is exactly 3.
CREATE TABLE timeline_cells (
    level      smallint NOT NULL,
    cell_x     integer NOT NULL,
    cell_y     integer NOT NULL,
    decade     bigint NOT NULL, -- floor(earliest_year / 10) * 10: -50 covers -50..-41
    kind       text NOT NULL,
    importance integer NOT NULL, -- exact, so minImportance filters stay exact
    n          integer NOT NULL CHECK (n > 0),
    PRIMARY KEY (level, cell_x, cell_y, decade, kind, importance)
);
