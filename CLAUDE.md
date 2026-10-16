# What Happened Here — project context

## The idea
A web app where you pick any place on Earth and any window of time and see what happened there,
across all of recorded history. Everything with a time and a place: births, deaths, foundings,
discoveries, disasters, political events, openings of buildings and bridges, battles, etc.

Default view shows all kinds at once, ranked by importance. Filtering by kind is optional and
happens server-side.

## Data source
Wikidata.
- Initial load: the full Wikidata JSON dump (`latest-all.json.gz`), streamed one entity per line,
  never loaded into memory whole.
- Keeping current: poll Wikidata's recent-changes API for items edited since the last run,
  refetch those entities, run them through the same extractor, upsert/delete.
- Importance = sitelinks count (number of language Wikipedias with an article on the item).

## What counts as an event
An event is **(item, kind of moment, when, where)**. One item can produce several events
(a person -> Born + Died; a building -> Founded/Opened + Ended).

| Kind | When | Where (try in order) |
|---|---|---|
| Happened | P585 point in time, else P580 start (P582 end if present) | P625 own coords -> P276 location -> P131 admin area |
| Born | P569 | P19 place of birth |
| Died | P570 | P20 place of death |
| Founded | P571 inception | P625 -> P159 headquarters -> P131 |
| Ended | P576 dissolved/abolished/demolished | same as Founded |
| Opened | P1619 date of official opening | P625 -> P131 |

Later candidate: P575 time of discovery (with P189 location of discovery).
Not used on purpose: P577 publication date (would flood the data with scholarly articles).

### Rules for every kind
- Gate: item must have at least one Wikipedia sitelink.
- Ranks: use preferred-rank statements if any, else normal; never deprecated.
- Multiple values: pick the single best-ranked value; don't emit duplicate events.
- Dates: keep precision (day/month/year/decade/century/millennium), support BC years,
  record calendar (Julian vs Gregorian). UI shows e.g. "c. 1200s", never a fake exact date.
- Place given as another item (P276, P19, P20, P159, P131): store the place QID; coordinates are
  resolved later from a separate places table (never copied into the event).
- Exclusions: fictional items, coordinates not on Earth (check globe), future dates,
  broken dates. Further exclusions are decided from looking at sample output, not guessed upfront.

## Architecture
| Piece | Job | Tech | Runs where |
|---|---|---|---|
| extractor | One raw Wikidata entity JSON -> 0..n clean events. Pure, in-memory, no DB, no framework. | Plain Java + Jackson | Library used by importer and follower |
| importer | One-off bulk load from the dump | Spring Batch | Local machine / temp VM |
| database | Events + places, spatial/time queries | Postgres + PostGIS | Hosted Postgres |
| follower | Keep DB current via recent-changes polling | Small Spring Boot app | GitHub Actions on a schedule |
| api | Serves the frontend | Spring Boot | Hosted container |
| frontend | Map, time slider, "what happened here" | Kepler.gl | Static hosting |

### Importer flow
1. Stream the dump; extractor emits candidate events (with place QIDs) and the importer also
   records every item that has coordinates (potential places).
2. Write to a LOCAL Postgres, not the hosted one.
3. Resolve with SQL joins: attach coordinates, drop unresolved events, keep only used places.
4. Inspect, then bulk-load (COPY) into hosted DB. Record dump date as "last processed".

### Follower flow (each scheduled run)
Read last-processed timestamp -> fetch changed item IDs since then -> refetch entities in batches ->
same extractor -> upsert / delete / handle merges and redirects -> update changed places ->
save new timestamp. A failed run just retries from the same timestamp.
Caveats: recent-changes history is limited (~a month), so long outages need a re-import;
periodic full re-import from a fresh dump is the safety net.

### API (sketch)
- GET /events?bbox&from&to&kinds&minImportance  (map view)
- GET /places/nearby?lat&lon&radius              ("what happened here", by importance)
- GET /events/{id}                               (details; Wikipedia summary fetched + cached)
- GET /stats/timeline?bbox                       (counts per decade for the slider histogram)

## Testing strategy (fail fast)
1. Test cases: ~50 hand-picked entities saved as JSON, each with its expected events; unit tests.
   See docs/test-cases.md. Every weird case found later gets added there.
2. Head of the dump: stop after the first N (e.g. 100,000) entities. Minutes, real data.
   Unresolved places are expected here (place may be outside the slice) — measure, don't fix.
3. Full dump only after 1 and 2 look right.
After each run, report: events per kind, % with resolved place, most common P31 types per kind
(junk detector), distribution of dates and precisions.

## Build order
1. extractor + test cases
2. extractor over head of dump, tune rules
3. DB schema + API on sample data
4. frontend on the API
5. deploy (API, DB, frontend) with sample data
6. full import (Spring Batch)
7. follower on GitHub Actions

## Tech
- Java 21, Gradle (Kotlin DSL) multi-project build: extractor, importer, follower, api
  (add subprojects as they're needed).
- Jackson streaming for the dump; Spring Batch for the importer; Postgres + PostGIS;
  Flyway migrations; Testcontainers for DB tests; Docker for deployment.
