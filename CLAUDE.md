# What Happened Here

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
- Importance = number of Wikipedia sitelinks: keys ending in `wiki`, excluding non-Wikipedia
  project wikis: `commonswiki`, `specieswiki`, `wikidatawiki`, `metawiki`, `mediawikiwiki`,
  `sourceswiki`, `outreachwiki`, `incubatorwiki`, `wikimaniawiki`, `foundationwiki`,
  `wikifunctionswiki`, `abstractwiki`. Wikivoyage/Wikiquote/Wikisource etc. don't count
  (Rome: 344 sitelinks, 273 Wikipedias). The head-of-dump report lists any `*wiki` key that is
  neither excluded nor a language-code pattern, so new Wikimedia projects get noticed.

## What counts as an event
An event is **(item, kind of moment, when, where)**. One item can produce several events
(a person -> Born + Died; a building -> Founded/Opened + Ended).

| Kind | When | Where (try in order) |
|---|---|---|
| Happened | P580 start + P582 end when both exist, else P585 point in time, else P580 alone | P625 own coords -> P276 location -> P131 admin area |
| Born | P569 | P19 place of birth |
| Died | P570 | P20 place of death |
| Founded | P571 inception | P625 -> P159 headquarters -> P131 |
| Ended | P576 dissolved/abolished/demolished | same as Founded |
| Opened | P1619 date of official opening | P625 -> P131 |

Later candidates, measured by the head-of-dump report (how many items have them but produce no
event, e.g. Apollo 11 and Tutankhamun's tomb today): P575 time of discovery (with P189 location
of discovery); P619/P620 launch/landing dates (with P448 launch site).
Not used on purpose: P577 publication date (would flood the data with scholarly articles).

### Rules for every kind
- Gate: item must have at least one Wikipedia sitelink.
- Ranks: use preferred-rank statements if any, else normal; never deprecated. Statements
  without a recognised rank are skipped.
- Multiple values: pick the single best-ranked value; don't emit duplicate events. Tie-break
  among equal ranks: for dates, the earliest date (deterministic; e.g. Eiffel Tower's two P1619
  values); for places (P625, P276, P19, P20, P159, P131), current values first, then the first
  in statement order (e.g. a town that moved between provinces). A value is former when it has
  an end-time qualifier (P582) with a value or `somevalue`; a `novalue` P582 means "no end".
- Unusable values (broken date, malformed or off-Earth coordinates) are skipped and the next
  usable value is taken: preferred usable, else normal usable, never deprecated.
- An item whose P625 values exist but are all off Earth (e.g. Tycho, a lunar crater) produces no
  events; the place chain does not fall back to P276/P131 for it. (Event assembly must check
  that P625 statements exist, since a null best value can't tell "none" from "off Earth".)
- Statements with snaktype `somevalue` or `novalue` carry no value; skip them.
- Dates: keep precision (day/month/year/decade/century/millennium), support BC years,
  record calendar (Julian vs Gregorian). UI shows e.g. "c. 1200s", never a fake exact date.
- Place given as another item (P276, P19, P20, P159, P131): store the place QID; coordinates are
  resolved later from a separate places table (never copied into the event).
- Coordinates keep their stated precision (degrees); a 1-degree point (Lisbon 1755, at sea) must
  be distinguishable from an exact one so the UI can show it as approximate. Approximate =
  precision unknown or >= 0.1 degrees (~11 km, coarser than a town).
- Dates: a value claiming more precision than it carries (e.g. `+1887-00-00` at day precision) is
  broken and dropped; fields below the stated precision are cleared to 0.
- Exclusions: fictional items (detected via P31 types: fictional human/character, literary,
  film and mythical character, legendary figure, deity, fictional battle; there is no single
  "fictional" flag). An item also typed as human (Q5), human whose existence is disputed or
  human biblical figure is kept (King Arthur, Hagar), even if also typed fictional human. Also
  excluded: coordinates not on Earth (globe must be Q2), future dates, broken dates. Further
  exclusions are decided from looking at sample output, not guessed upfront.

## Architecture
| Piece | Job | Tech | Runs where |
|---|---|---|---|
| extractor | One raw Wikidata entity JSON -> 0..n clean events. Pure, in-memory, no DB, no framework. | Plain Java + Jackson | Library used by importer and follower |
| importer | One-off bulk load from the dump | Spring Batch | Local machine / temp VM |
| database | Events + places, spatial/time queries | Postgres + PostGIS | Hosted Postgres |
| follower | Keep DB current via recent-changes polling | Small Spring Boot app | GitHub Actions on a schedule |
| api | Serves the frontend | Spring Boot | Hosted container |
| frontend | Map, time slider, "what happened here" | deck.gl on MapLibre (free tiles, no token); React + Vite; Vitest + Playwright | Static hosting |

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

## Extractor model (target design)
- `ExtractedItem(itemId, label, importance, instanceOf, events)`: per-item result. Item-level
  facts live here once, not on every event; `instanceOf` (P31) feeds the junk report.
- `Event(kind, when, endDate, where)`.
- `HistoricDate(year, month, day, precision, calendar)`: `year` is a `long`, negative for BC;
  never use `java.time` types for historic dates.
- Place is a sealed type: own coordinates (lat, lon, precision) or a reference to a place QID.
- Unusable values return null / are dropped, never thrown: one bad value costs one event, not the run.
- Group small types by nesting (e.g. `HistoricDate.Precision`, `Event.Kind`, `Event.Place`)
  rather than one file per enum.

## Tech
- Java 21, Gradle (Kotlin DSL) multi-project build: extractor, importer, follower, api
  (add subprojects as they're needed). Base package `io.github.luisr96.whathappenedhere`.
- Jackson streaming for the dump; Spring Batch for the importer; Postgres + PostGIS;
  Flyway migrations; Testcontainers for DB tests; Docker for deployment.
- Frontend: deck.gl layers over a MapLibre basemap with free tiles (no API token), built with npm
  outside Gradle. Not Kepler.gl: the API already filters by viewport and time server-side, so
  Kepler's client-side dataset model isn't needed.

## Workflow for every feature
1. **Tests first**: delegate to the `test-writer` agent with the feature description. It writes
   failing tests from the spec without seeing the implementation. Confirm they fail.
2. **Implement** until all tests pass. Don't edit the new tests to make them pass; if a test looks
   wrong, stop and explain why instead.
3. **Review**: delegate to the `code-reviewer` agent. Fix every "must fix", and "should fix" unless
   there's a stated reason not to. Re-run tests.
4. **Commit**: propose a Conventional Commits message (`feat:`, `fix:`, `test:`, `refactor:`,
   `docs:`, `build:`, `chore:`, with optional scope, e.g. `feat(extractor): parse Wikidata times`),
   show `git diff --stat` and a short summary, and wait for approval. One logical change per commit.