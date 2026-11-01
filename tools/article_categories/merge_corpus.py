"""Merge reviewed shards, keeping reposts together so training/evaluation cannot leak."""
import json
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).parent


def article_key(url):
    parts = urlsplit(url)
    return urlunsplit(("", parts.netloc.lower().removeprefix("www."), parts.path.rstrip("/"), parts.query, ""))


def main():
    categories = []
    examples = {}
    for name in ("corpus_technology.json", "corpus_society.json", "corpus_finance.json"):
        shard = json.loads((ROOT / name).read_text(encoding="utf-8"))
        categories.extend(shard["categories"])
        for item in shard["examples"]:
            key = article_key(item["url"])
            if key not in examples:
                examples[key] = item
            else:
                previous = examples[key]
                previous["category_ids"] = sorted(set(previous["category_ids"] + item["category_ids"]))
                if item["split"] == "evaluation":
                    previous["split"] = "evaluation"
                if not previous.get("description") and item.get("description"):
                    previous["description"] = item["description"]
                    previous["description_source"] = item["description_source"]
    records = list(examples.values())
    assert len({x["hn_id"] for x in records}) == len(records)
    result = {"schema_version": 1, "collected_at": "2026-11-01", "categories": categories, "examples": records}
    (ROOT / "corpus.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Merged {len(records)} unique articles; {sum(x['split'] == 'train' for x in records)} training")
    for category in categories:
        print(category["id"], {split: sum(x["split"] == split and category["id"] in x["category_ids"] for x in records)
                               for split in ("train", "evaluation")})


if __name__ == "__main__":
    main()
