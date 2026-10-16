# Extractor test cases

Hand-picked Wikidata items covering normal and tricky cases. For each: fetch
`https://www.wikidata.org/wiki/Special:EntityData/<QID>.json`, save under
`extractor/src/test/resources/entities/<QID>.json`, and write a test asserting the expected events.

QIDs are intentionally not listed: look each one up on wikidata.org and fill in the column.
Expected output is what the rules in CLAUDE.md *should* produce; if the real data disagrees,
check the item before changing the rule.

## People (Born / Died)
| Item | QID  | Expected | Why it's here |
|---|------|---|---|
| Wolfgang Amadeus Mozart | Q254 | Born (Salzburg, day), Died (Vienna, day) | Baseline person |
| Marie Curie |      | Born (Warsaw), Died (near Passy, France) | Born and died in different countries |
| Albert Einstein |      | Born (Ulm), Died (Princeton) | Baseline, different continents |
| Genghis Khan |      | Born with low precision (year or less) | Uncertain birth date |
| Julius Caesar |      | Born and Died BC | BC dates |
| Confucius |      | BC dates, low precision | BC + imprecise |
| William Shakespeare |      | Born/Died; check calendar | Julian calendar dates |
| A living person (e.g. a current head of state) |      | Born only, no Died | Missing death is normal |
| Someone with no place of birth on Wikidata |      | Died only, or nothing | Missing place |
| Sherlock Holmes | Q4653     | Nothing | Fictional person |

## Happened (point in time / start-end)
| Item | QID | Expected | Why |
|---|-----|---|---|
| 1755 Lisbon earthquake | Q191055    | Happened, day precision, own coords | Natural disaster |
| Eruption of Mount Vesuvius in 79 AD |     | Happened, AD 79 | Very early CE date |
| Signing of the Magna Carta (the charter) |     | Check: point in time + Runnymede | Political, may live on the document item |
| Treaty of Versailles |     | Happened, Versailles | Treaty, place via location |
| Fall of the Berlin Wall |     | Happened, Berlin, day | Modern political event |
| Apollo 11 |     | Happened; check what place resolves | Place may be off Earth / launch site |
| Chernobyl disaster |     | Happened, day, own coords | Industrial disaster |
| Sinking of the Titanic |     | Happened; check coords at sea | Event at sea |
| 2004 Indian Ocean earthquake and tsunami |     | Happened | Large-area event |
| French Revolution |     | Happened with start + end | Long range event |
| Battle of Hastings |     | Happened, day | One battle as a control case |
| A battle with only P276 location, no own coords |     | Happened, place via location | Place fallback |
| Discovery of Tutankhamun's tomb |     | Check what exists | Discovery-type event |
| Black Death |     | Check: range, place? | Event with vague or no single place |
| A fictional battle (e.g. from a novel or film) |     | Nothing | Fictional exclusion |

## Founded / Ended / Opened
| Item | QID | Expected | Why |
|---|-----|---|---|
| Rome (city) | Q220    | Founded, BC, legendary date | BC founding |
| New York City |     | Founded | City founding |
| University of Bologna |     | Founded | Institution |
| Harvard University |     | Founded | Institution |
| Eiffel Tower |  Q243   | Founded and/or Opened, Paris coords | Building, multiple time props |
| Golden Gate Bridge |     | Opened | Opening date |
| Twin Towers (World Trade Center 1973–2001) |     | Opened + Ended | Demolished/destroyed building |
| Soviet Union |     | Founded + Ended; place via capital/admin? | State; place may be unclear |
| East India Company |     | Founded + Ended, place via headquarters | Organization via P159 |
| Apple Inc. |     | Founded, place via headquarters | Company |
| Library of Alexandria |     | Founded (BC, imprecise), maybe Ended | Ancient, uncertain dates |
| Hagia Sophia |     | Founded/Opened | Ancient building, Julian dates |
| A village with inception and few sitelinks |     | Founded | Low-importance but valid |

## Edge cases that should produce nothing (or be filtered)
| Item | QID | Expected | Why |
|---|---|---|---|
| A scholarly article | | Nothing | P577 is not used |
| An asteroid (has discovery date) | | Nothing | Not on Earth |
| A crater on the Moon | | Nothing | Coordinates on another globe |
| An item with no Wikipedia sitelinks | | Nothing | Sitelink gate |
| An item with a deprecated-rank date only | | Nothing for that date | Rank rules |
| An item with two birth dates of different rank | | One Born, preferred value | Multiple values |
| A redirect/merged item | | Handled as redirect | Follower merges |
