# Extractor test cases

Hand-picked Wikidata items covering normal and tricky cases. Each one is saved under
`extractor/src/test/resources/entities/<QID>.json` and gets a test asserting the expected events.

Fixtures are fetched with `python scripts/fetch_entities.py <QID>...`. It saves each entity in the
same shape as a line of the dump and keeps only English labels, descriptions and aliases (plus the
`mul` label), with all claim values, ranks, qualifiers and sitelinks but no references.
Re-fetching updates an item to its latest revision, so expectations may need re-checking.

Rows whose QID column says `synthetic-*` are **synthetic**: hand-made variants of real fixtures
for cases that are hard to find in live data. They are regenerated with
`python scripts/make_synthetic_entities.py`, which documents each change.

Expected output is what the rules in CLAUDE.md *should* produce; if the real data disagrees,
check the item before changing the rule.

Notation: dates are `year-month-day` with the precision they carry (`-0222` = 222 BC at year
precision, `1564-04` = month precision); **J** = Julian calendar. Places are a QID (resolved
later from the places table) or **own coords** (P625; *approx.* = precision >= 0.1 degrees or
unknown).

## People (Born / Died)
| Item | QID | Expected | Why it's here |
|---|---|---|---|
| Wolfgang Amadeus Mozart | Q254 | Born 1756-01-27 at Q34713 (Salzburg); Died 1791-12-05 at Q1741 (Vienna) | Baseline person; P20 has two values, Vienna is preferred |
| Marie Curie | Q7186 | Born 1867-11-07 at Q9365159 (her birthplace in Warsaw; first of two equal-rank P19); Died 1934-07-04 at Q2364887 | Born and died in different countries; no `en` label, only `mul` |
| Albert Einstein | Q937 | Born 1879-03-14 at Q3012 (Ulm); Died 1955-04-18 at Q138518 (Princeton) | Baseline, different continents |
| Genghis Khan | Q720 | Born 1162-05-31 J at Q2068087; Died 1227-08-25 J at Q211243 | Uncertain birth date (Wikidata states day precision anyway) |
| Julius Caesar | Q1048 | Born -0100-07 J (month, preferred) at Q220; Died -0044-03-15 J at Q944814 | BC dates |
| Confucius | Q4604 | Born -0551 J (year, preferred) at Q210287; Died -0479 J (year, preferred) at Q1012627 | BC + imprecise; preferred coarse value beats normal exact ones |
| William Shakespeare | Q692 | Born 1564-04 J (month; day values deprecated) at Q189288; Died 1616-04-23 J at Q189288 | Julian calendar dates |
| Emmanuel Macron (living head of state) | Q3052772 | Born 1977-12-21 at Q41604; no Died | Missing death is normal; no `en` label, only `mul` |
| Balbinus (Roman emperor, no P19) | Q1805 | Died 0238-07-29 J at Q220 only | Missing place of birth |
| Sherlock Holmes | Q4653 | Nothing | Fictional person (has dates but no places, so the fictional rule itself is tested by `synthetic-fictional`) |
| King Arthur | Q45792 | Died 0600 J (century) at Q1028477; no Born (no P19) | Mythical character but also typed "human whose existence is disputed", so kept |
| Hagar | Q214617 | Born -1930 J (year) at Q11768; no Died (no P20) | Film character but also typed "human biblical figure", so kept |
| Zeus | Q34201 | Nothing | Deity with no dates or places; the type rule itself is tested by `synthetic-mythical` |

## Happened (point in time / start-end)
| Item | QID | Expected | Why |
|---|---|---|---|
| 1755 Lisbon earthquake | Q191055 | Happened 1755-11-01, own coords (36, -11) approx. | Natural disaster, 1-degree point at sea |
| Eruption of Mount Vesuvius in 79 AD | Q2411998 | Happened 0079-10-24 J (earliest normal; 08-24 is deprecated), own coords | Very early CE date |
| Magna Carta (the charter) | Q12519 | Nothing (only P571, no place property) | The event lives on the document item, which has no place |
| Treaty of Versailles | Q8736 | Happened 1919-06-28 (P585; P580 has no P582), at Q1583447 (first P276) | Point in time beats a start without an end |
| Fall of the Berlin Wall | Q69163529 | Happened 1989-11-09, own coords | Modern political event |
| Apollo 11 | Q43653 | Nothing (uses P619/P620 launch/landing, not covered) | Place may be off Earth / launch site |
| Chernobyl disaster | Q486 | Happened 1986-04-26, own coords | Industrial disaster |
| Sinking of the Titanic | Q2577588 | Happened 1912-04-14 to 1912-04-15 (P580-P582 beat month-precision P585), own coords not approx. | Event at sea, range rule |
| 2004 Indian Ocean earthquake and tsunami | Q130754 | Happened 2004-12-26, own coords | Large-area event |
| French Revolution | Q6534 | Happened 1789-07-14 to 1799-11-09, at Q142 (France) | Long range event; range beats P585 |
| Battle of Hastings | Q83224 | Happened 1066-10-14 J, own coords | One battle as a control case |
| Battle of Sellasia (P276 only, no P625) | Q204343 | Happened -0222 (year), at Q15709786 | Place fallback to P276 |
| Tomb of Tutankhamun (KV62) | Q835309 | Nothing (no date properties) | Discovery-type event; candidate for P575 later |
| Black Death | Q42005 | Happened 1346 J to 1352 J (year), at Q48 (first P276) | Event with vague or no single place |
| Battle of the Hornburg (Helm's Deep, fictional) | Q1764229 | Nothing | Fictional event (has no date either) |
| 2015 Balkline 47.2 European Championship | Q101208683 | Happened 2015 (year, from P585; no end date) | P582 2013-04-29 is earlier than P580 2015-04-27: reversed end is unusable, so P585 2015 wins over the start alone |
| 1899 Arima rumblings | Q11516855 | Happened 1899-07-05 (no end date) | P582 1890 (year) is certainly earlier than P580 1899-07-05, compared at year precision |
| Kalofer massacre | Q118902351 | Happened 1878-07 (month, no end date) | P582 1877-08-07 is certainly earlier than P580 1878-07 (month) |

## Founded / Ended / Opened
| Item | QID | Expected | Why |
|---|---|---|---|
| Rome (city) | Q220 | Founded -0753-04-21 J, own coords | BC founding, legendary date |
| New York City | Q60 | Founded 1624 (year, preferred), own coords | City founding |
| University of Bologna | Q131262 | Founded 1088 J (year), own coords approx. (no stated precision) | Institution |
| Harvard University | Q13371 | Founded 1636-09-08, own coords | Institution |
| Eiffel Tower | Q243 | Founded 1887-01-28; Opened 1889-03-31 (two normal P1619, earliest wins); own coords | Building, multiple time props, equal-rank tie-break |
| Golden Gate Bridge | Q44440 | Founded 1937-05-27; Opened 1937-05-28; own coords | Opening date |
| World Trade Center (original complex) | Q11235 | Founded 1966-08-25; Ended 2001-09-11; own coords | Destroyed building |
| Soviet Union | Q15180 | Founded 1922-12-30 (1923 value deprecated); Ended 1991-12-26; own coords (65, 90) approx. | State; place is a 1-degree centroid |
| East India Company | Q83164 | Founded 1600-12-31; Ended 1874-06-01; at Q84 (London, P159) | Organization via P159 |
| Apple Inc. | Q312 | Founded 1976-04-01 at Q189471 (preferred P159) | Company |
| Library of Alexandria | Q435 | Founded -0300 J (century); Ended 0400 J (century); own coords | Ancient, uncertain dates |
| Hagia Sophia | Q12506 | Founded 0532-02-23 J (preferred); Ended 0404 J (year, earliest normal P576); Opened 0537 J (year); own coords | Ancient building; Ended before Founded is a data quirk (P576 values belong to earlier churches), a junk-report candidate |
| Ivanovka (Russian village, 3 Wikipedias) | Q50829 | Founded 1738 (year), own coords | Low-importance but valid |

## Edge cases that should produce nothing (or be filtered)
| Item | QID | Expected | Why |
|---|---|---|---|
| Molecular Structure of Nucleic Acids (Watson & Crick 1953) | Q1895685 | Nothing | P577 is not used |
| 433 Eros (asteroid) | Q16711 | Nothing | Only date is P575 (not used yet), no coords |
| Tycho (lunar crater) | Q631696 | Nothing | Coordinates on the Moon's globe (also no date) |
| Lisbon earthquake with coords on the Moon (synthetic) | synthetic-off-earth | Nothing (no fallback to its P276) | Off-Earth-only P625 excludes the item |
| Mozart typed as fictional human Q15632617 (synthetic) | synthetic-fictional | Nothing | Fictional exclusion with otherwise valid events |
| Mozart typed only as mythical character Q4271324 (synthetic) | synthetic-mythical | Nothing | Denylisted type with no human type excludes |
| Mozart typed as human Q5 and film character Q15773347 (synthetic) | synthetic-human-film-character | Born and Died as Mozart | A human type keeps an item despite a denylisted type |
| 2011–12 Pro12 (rugby union league season) | Q144644 | Nothing | Typed only as a season subtype (season of the United Rugby Championship); would otherwise give Happened 2011-09-02 to 2012-05-27 at Q25 |
| 2022 FIFA World Cup | Q284163 | Happened 2022-11-20 to 2022-12-18 at Q1186333 | Tournament edition typed with the generic sports season Q27020041, which is kept |
| Ivanovka without an English Wikipedia article | Q50829 | Events as usual, wikiTitle null | No enwiki sitelink; the item still passes the gate through other Wikipedias |
| Zanclean (geologic stage, -5,333,000 to -3,600,000) | Q146094 | Nothing | Geologic time interval (stage/age); its GSSP coordinates would otherwise give Happened |
| Mozart with sitelinks removed (synthetic) | synthetic-no-sitelinks | Nothing | Sitelink gate |
| Mozart with every P569 deprecated (synthetic) | synthetic-deprecated-only | Died only, no Born | Rank rules |
| Mozart plus an earlier deprecated P569 (synthetic) | synthetic-deprecated-earlier | Born 1756-01-27 | Deprecated dropped before the earliest-date tie-break |
| Mozart plus an earlier normal-rank P569 listed first, real one preferred (synthetic) | synthetic-two-birth-ranks | Born 1756-01-27 (preferred) | Multiple values |
| Q19710815, redirect to George W. Bush (Q207) | Q19710815 | Item id Q207 (not Q19710815); Born 1946-07-06 at Q12073283 | The follower maps redirects; the extractor just reports the entity it was given |
| Isernia la Pineta (Palaeolithic site) | Q1673859 | Nothing | Founded around -698000; dates whose earliest possible year is before 10,000 BC are excluded like future dates |
| Pre-Columbian era | Q202390 | No Happened event | Range -48000 to 1492: a start before 10,000 BC is unusable, so there is no range; P585 would still apply (this item has none) |
