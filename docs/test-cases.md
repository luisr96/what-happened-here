# Extractor test cases

Hand-picked Wikidata items covering normal and tricky cases. Each one is saved under
`extractor/src/test/resources/entities/<QID>.json` and gets a test asserting the expected events.

Fixtures are fetched with `python scripts/fetch_entities.py <QID>...`. It saves each entity in the
same shape as a line of the dump and keeps only English labels, descriptions and aliases (plus the
`mul` label), with all claim values, ranks, qualifiers and sitelinks but no references. Re-fetching updates an item to its latest revision.

Rows whose QID column says `synthetic-*` are **synthetic**: hand-made variants of Mozart (Q254)
for cases that are hard to find in live data. They are regenerated with
`python scripts/make_synthetic_entities.py`, which documents each change.

Expected output is what the rules in CLAUDE.md *should* produce; if the real data disagrees,
check the item before changing the rule.

## People (Born / Died)
| Item | QID  | Expected | Why it's here |
|---|------|---|---|
| Wolfgang Amadeus Mozart | Q254 | Born (Salzburg, day), Died (Vienna, day) | Baseline person; P20 has two values, Vienna (Q1741) is preferred |
| Marie Curie | Q7186 | Born (Warsaw), Died (near Passy, France) | Born and died in different countries; no `en` label, only `mul` (label fallback) |
| Albert Einstein | Q937 | Born (Ulm), Died (Princeton) | Baseline, different continents |
| Genghis Khan | Q720 | Born with low precision (year or less) | Uncertain birth date |
| Julius Caesar | Q1048 | Born and Died BC | BC dates |
| Confucius | Q4604 | BC dates, low precision | BC + imprecise |
| William Shakespeare | Q692 | Born/Died; check calendar | Julian calendar dates |
| Emmanuel Macron (living head of state) | Q3052772 | Born only, no Died | Missing death is normal; no `en` label, only `mul` |
| Balbinus (Roman emperor, no P19) | Q1805 | Died only, or nothing | Missing place |
| Sherlock Holmes | Q4653 | Nothing | Fictional person (has dates but no places, so the fictional rule itself is tested by `synthetic-fictional`) |

## Happened (point in time / start-end)
| Item | QID | Expected | Why |
|---|-----|---|---|
| 1755 Lisbon earthquake | Q191055    | Happened, day precision, own coords | Natural disaster |
| Eruption of Mount Vesuvius in 79 AD | Q2411998 | Happened, AD 79 | Very early CE date |
| Magna Carta (the charter) | Q12519 | Nothing (only P571, no place property) | The event lives on the document item, which has no place |
| Treaty of Versailles | Q8736 | Happened, Versailles | Treaty, place via location |
| Fall of the Berlin Wall | Q69163529 | Happened, Berlin, day | Modern political event |
| Apollo 11 | Q43653 | Nothing (uses P619/P620 launch/landing, not covered) | Place may be off Earth / launch site |
| Chernobyl disaster | Q486 | Happened, day, own coords | Industrial disaster |
| Sinking of the Titanic | Q2577588 | Happened; check coords at sea | Event at sea |
| 2004 Indian Ocean earthquake and tsunami | Q130754 | Happened | Large-area event |
| French Revolution | Q6534 | Happened with start + end | Long range event |
| Battle of Hastings | Q83224 | Happened, day | One battle as a control case |
| Battle of Sellasia (P276 only, no P625) | Q204343 | Happened, place via location | Place fallback |
| Tomb of Tutankhamun (KV62) | Q835309 | Nothing (no date properties) | Discovery-type event; candidate for P575 later |
| Black Death | Q42005 | Check: range, place? | Event with vague or no single place |
| Battle of the Hornburg (Helm's Deep, fictional) | Q1764229 | Nothing | Fictional event (has no date either) |

## Founded / Ended / Opened
| Item | QID | Expected | Why |
|---|-----|---|---|
| Rome (city) | Q220    | Founded, BC, legendary date | BC founding |
| New York City | Q60 | Founded | City founding |
| University of Bologna | Q131262 | Founded | Institution |
| Harvard University | Q13371 | Founded | Institution |
| Eiffel Tower | Q243 | Founded and/or Opened (1889-03-31: two normal-rank P1619, earliest wins), Paris coords | Building, multiple time props, equal-rank tie-break |
| Golden Gate Bridge | Q44440 | Opened | Opening date |
| World Trade Center (original complex) | Q11235 | Founded (1966-08-25) + Ended (2001-09-11), own coords | Destroyed building |
| Soviet Union | Q15180 | Founded + Ended; place via capital/admin? | State; place may be unclear |
| East India Company | Q83164 | Founded + Ended, place via headquarters | Organization via P159 |
| Apple Inc. | Q312 | Founded, place via headquarters | Company |
| Library of Alexandria | Q435 | Founded (BC, imprecise), maybe Ended | Ancient, uncertain dates |
| Hagia Sophia | Q12506 | Founded/Opened | Ancient building, Julian dates |
| Ivanovka (Russian village, 3 Wikipedias) | Q50829 | Founded | Low-importance but valid |

## Edge cases that should produce nothing (or be filtered)
| Item | QID | Expected | Why |
|---|---|---|---|
| Molecular Structure of Nucleic Acids (Watson & Crick 1953) | Q1895685 | Nothing | P577 is not used |
| 433 Eros (asteroid) | Q16711 | Nothing | Only date is P575 (not used yet), no coords |
| Tycho (lunar crater) | Q631696 | Nothing | Coordinates on the Moon's globe (also no date) |
| Lisbon earthquake with coords on the Moon (synthetic) | synthetic-off-earth | Nothing | Globe must be Q2; only the globe differs from Q191055 |
| Mozart typed as fictional human Q15632617 (synthetic) | synthetic-fictional | Nothing | Fictional exclusion with otherwise valid events |
| Mozart with sitelinks removed (synthetic) | synthetic-no-sitelinks | Nothing | Sitelink gate |
| Mozart with every P569 deprecated (synthetic) | synthetic-deprecated-only | Died only, no Born | Rank rules |
| Mozart plus an earlier deprecated P569 (synthetic) | synthetic-deprecated-earlier | Born 1756-01-27 | Deprecated dropped before the earliest-date tie-break |
| Mozart plus an earlier normal-rank P569 listed first, real one preferred (synthetic) | synthetic-two-birth-ranks | One Born, 1756-01-27 (preferred) | Multiple values |
| Q19710815, redirect to George W. Bush (Q207) | Q19710815 | Handled as redirect | Follower merges |
