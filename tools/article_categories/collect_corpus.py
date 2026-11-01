"""Verify the curated corpus against HN, or retrieve replacement search candidates.

Uses only Python's standard library. Never changes corpus.json automatically.
"""

import argparse
import concurrent.futures
import html
import json
import pathlib
import urllib.parse
import urllib.request


def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Harmonic-Category-Corpus/1.0"})
    with urllib.request.urlopen(request, timeout=40) as response:
        return json.load(response)


def verify(example):
    item_id = example["hn_id"]
    item = get_json(f"https://hacker-news.firebaseio.com/v0/item/{item_id}.json")
    if not item or item.get("type") != "story":
        return f"{item_id}: missing story"
    differences = []
    if html.unescape(item.get("title", "")) != html.unescape(example["title"]):
        differences.append(f"title: {item.get('title')!r}")
    if item.get("url") != example["url"]:
        differences.append(f"url: {item.get('url')!r}")
    return f"{item_id}: " + "; ".join(differences) if differences else None


def candidates(example):
    params = urllib.parse.urlencode({
        "query": example["query"],
        "tags": "story",
        "hitsPerPage": 5,
        "numericFilters": "created_at_i>1609459200",
    })
    hits = get_json("https://hn.algolia.com/api/v1/search?" + params)["hits"]
    return {
        "replaces_hn_id": example["hn_id"],
        "query": example["query"],
        "category_ids": example["category_ids"],
        "hits": [{key: hit.get(key) for key in ("objectID", "title", "url", "created_at", "points")}
                 for hit in hits],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=pathlib.Path,
                        default=pathlib.Path(__file__).with_name("corpus.json"))
    parser.add_argument("--candidates", type=pathlib.Path,
                        help="Write search candidates to this temporary file instead of verifying HN IDs.")
    args = parser.parse_args()
    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    examples = corpus["examples"]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        if args.candidates:
            rows = list(executor.map(candidates, examples))
            args.candidates.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n",
                                       encoding="utf-8")
            print(f"Saved {len(rows)} candidate sets for manual review to {args.candidates}")
        else:
            errors = [result for result in executor.map(verify, examples) if result]
            for error in errors:
                print(error)
            if errors:
                raise SystemExit(f"{len(errors)} records differ from current HN data; review before regenerating.")
            print(f"Verified {len(examples)} HN titles and source URLs.")


if __name__ == "__main__":
    main()
