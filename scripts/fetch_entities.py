"""Fetch Wikidata entities as extractor test fixtures.

Usage: python scripts/fetch_entities.py Q254 Q7186 ...

Each entity is saved to extractor/src/test/resources/entities/<QID>.json in the same shape as
one line of the JSON dump (the bare, compact entity, not the {"entities": {...}} wrapper of
Special:EntityData). Statement references are dropped; claim values, ranks, qualifiers and
sitelinks are kept whole. Labels, descriptions and aliases are cut to English, plus the "mul"
label the extractor falls back to. A redirected QID is saved under the requested name, holding
the target entity, so the redirect stays visible in the file.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "extractor/src/test/resources/entities"
USER_AGENT = "what-happened-here-fixtures/0.1 (https://github.com/luisr96/what-happened-here)"
KEEP_LANGUAGES = {"labels": ("en", "mul"), "descriptions": ("en",), "aliases": ("en",)}


def fetch(qid):
    url = f"https://www.wikidata.org/wiki/Special:EntityData/{qid}.json"
    for backoff in (5, 15, 30, 60):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            retry_after = e.headers.get("Retry-After", "")
            time.sleep(int(retry_after) if retry_after.isdigit() else backoff)
    raise RuntimeError(f"{qid}: still rate limited")


def strip(entity):
    for field, languages in KEEP_LANGUAGES.items():
        entity[field] = {lang: v for lang, v in entity.get(field, {}).items() if lang in languages}
    # Sources take most of the file size and the extractor never reads them.
    for statements in entity.get("claims", {}).values():
        for statement in statements:
            statement.pop("references", None)
    return entity


def main(qids):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for qid in qids:
        entities = fetch(qid)["entities"]
        entity = strip(next(iter(entities.values())))
        path = OUT_DIR / f"{qid}.json"
        path.write_text(json.dumps(entity, ensure_ascii=False, separators=(",", ":")) + "\n",
                        encoding="utf-8", newline="\n")
        note = f" (redirect to {entity['id']})" if entity["id"] != qid else ""
        print(f"{qid}: {path.stat().st_size // 1024} KB{note}")
        time.sleep(1)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
