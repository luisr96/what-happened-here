"""Derive hand-made test fixtures from saved real entities.

Usage: python scripts/make_synthetic_entities.py

Some rule cases are hard to find in live data, or real items fail for an unrelated reason first
(Sherlock Holmes has no places, Tycho has no dates). These variants change one thing in an item
that otherwise yields good events, so only the rule under test can explain a missing event. They
are saved as synthetic-*.json; entity ids are unchanged, the file name marks them as hand-made.
Run fetch_entities.py for the source items first.
"""
import copy
import json
from pathlib import Path

DIR = Path(__file__).resolve().parent.parent / "extractor/src/test/resources/entities"
FICTIONAL_HUMAN = "Q15632617"
HUMAN = "Q5"
MYTHICAL_CHARACTER = "Q4271324"
FILM_CHARACTER = "Q15773347"
MOON = "http://www.wikidata.org/entity/Q405"


def single_birth_date(e):
    births = e["claims"]["P569"]
    assert len(births) == 1, "Q254 no longer has exactly one P569; revisit the synthetic variants"
    return births[0]


def birth_date_copy(real, statement_id, time, rank):
    decoy = copy.deepcopy(real)
    decoy["id"] = f"Q254$synthetic-{statement_id}"
    decoy["rank"] = rank
    decoy["mainsnak"]["datavalue"]["value"]["time"] = time
    return decoy


def deprecated_only(e):
    """Every birth date is deprecated, so no Born event; Died is untouched."""
    single_birth_date(e)["rank"] = "deprecated"


def deprecated_earlier(e):
    """A deprecated birth date earlier than the real normal-rank one.

    The earliest-date tie-break would pick it unless deprecated values are dropped first.
    """
    real = single_birth_date(e)
    e["claims"]["P569"].insert(0, birth_date_copy(real, "deprecated-birth", "+1750-01-01T00:00:00Z", "deprecated"))


def two_birth_ranks(e):
    """A normal-rank birth date, earlier and listed first, beside the real one made preferred.

    List order and the earliest-date tie-break both point at the decoy, so only rank picks right.
    """
    real = single_birth_date(e)
    real["rank"] = "preferred"
    e["claims"]["P569"].insert(0, birth_date_copy(real, "decoy-birth", "+1750-01-01T00:00:00Z", "normal"))


def no_sitelinks(e):
    """Fails the sitelink gate despite having good dates and places."""
    e["sitelinks"] = {}


def set_types(e, *qids):
    """Replaces the item's P31 statements with one normal-rank statement per type."""
    template = e["claims"]["P31"][0]
    e["claims"]["P31"] = []
    for i, qid in enumerate(qids):
        s = copy.deepcopy(template)
        s["id"] = f"Q254$synthetic-type-{i}"
        s["rank"] = "normal"
        s["mainsnak"]["datavalue"]["value"].update({"id": qid, "numeric-id": int(qid[1:])})
        e["claims"]["P31"].append(s)


def fictional(e):
    """Mozart typed as a fictional human: valid Born/Died data, excluded only for being fictional."""
    set_types(e, FICTIONAL_HUMAN)


def mythical(e):
    """Mozart typed only as a mythical character: excluded, as no human type keeps it."""
    set_types(e, MYTHICAL_CHARACTER)


def human_film_character(e):
    """Mozart typed as human and film character: kept, because a human type wins."""
    set_types(e, HUMAN, FILM_CHARACTER)


def off_earth(e):
    """The Lisbon earthquake with its coordinates on the Moon's globe."""
    for s in e["claims"]["P625"]:
        s["mainsnak"]["datavalue"]["value"]["globe"] = MOON


VARIANTS = {
    "synthetic-deprecated-only": ("Q254", deprecated_only),
    "synthetic-deprecated-earlier": ("Q254", deprecated_earlier),
    "synthetic-two-birth-ranks": ("Q254", two_birth_ranks),
    "synthetic-no-sitelinks": ("Q254", no_sitelinks),
    "synthetic-fictional": ("Q254", fictional),
    "synthetic-mythical": ("Q254", mythical),
    "synthetic-human-film-character": ("Q254", human_film_character),
    "synthetic-off-earth": ("Q191055", off_earth),
}


def main():
    for name, (source, change) in VARIANTS.items():
        entity = json.loads((DIR / f"{source}.json").read_text(encoding="utf-8"))
        change(entity)
        (DIR / f"{name}.json").write_text(
            json.dumps(entity, ensure_ascii=False, separators=(",", ":")) + "\n",
            encoding="utf-8", newline="\n")
        print(name)


if __name__ == "__main__":
    main()
