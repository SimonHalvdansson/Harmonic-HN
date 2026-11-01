"""Discover and verify the manually reviewed technology/science HN corpus shard.

Discovery writes candidates only. Finalization requires an explicit reviewed-ID
selection file; a keyword hit alone is never promoted into the corpus.
"""

import argparse
import concurrent.futures
import datetime
import html
from html.parser import HTMLParser
import json
import pathlib
import re
import time
import urllib.parse
import urllib.request


CATEGORIES = {"ai": "AI", "software": "Programming", "security": "Security",
              "privacy": "Privacy", "science": "Science", "space": "Space", "hardware": "Hardware"}
# Reviewed metadata that was site-wide boilerplate, social-share widgets, or a
# repurposed product page rather than the article referenced by the HN title.
OMIT_METADATA_IDS = {33999162, 37383913, 33708867, 27591657, 44953032,
                     29396643, 38990755, 39702568, 42731091, 37265346}
DESCRIPTION_WORD_LIMIT = 24
QUERIES = {
    "ai": ["GPT", "diffusion", "AlphaFold", "fine tuning", "language model", "Whisper",
           "reinforcement learning", "embeddings", "neural network", "machine learning"],
    "software": ["Rust", "Python", "SQLite", "PostgreSQL", "compiler", "JavaScript",
                 "debugging", "software testing", "Git", "programming language"],
    "security": ["CVE", "ransomware", "backdoor", "OpenSSH", "vulnerability", "supply chain attack",
                 "malware", "password", "XSS", "exploit"],
    "privacy": ["privacy", "surveillance", "data broker", "end-to-end encryption", "facial recognition",
                "tracking", "Signal", "cookies", "metadata", "telemetry"],
    "science": ["CRISPR", "fusion", "quantum", "protein", "physics", "biology",
                "cancer", "chemistry", "neuroscience", "superconductor"],
    "space": ["James Webb", "Voyager", "NASA", "Starship", "Mars", "Moon",
              "asteroid", "Hubble", "Euclid", "satellite"],
    "hardware": ["Raspberry Pi", "RISC-V", "semiconductor", "CPU", "FPGA", "GPU",
                 "transistor", "circuit", "Framework Laptop", "chip"],
}


def request(url, *, json_response=True, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": "Harmonic-Article-Category-Research/1.0",
                                              "Accept": "application/json" if json_response else "text/html"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        data = response.read(1_500_000)
        return json.loads(data) if json_response else data.decode(response.headers.get_content_charset() or "utf-8", errors="replace")


def discover_query(pair):
    category, query = pair
    params = urllib.parse.urlencode({"query": query, "tags": "story", "hitsPerPage": 10,
                                    "restrictSearchableAttributes": "title",
                                    "numericFilters": "created_at_i>1609459200,created_at_i<1793491200,points>30"})
    for attempt in range(3):
        try:
            hits = request("https://hn.algolia.com/api/v1/search?" + params)["hits"]
            return [{"category": category, "query": query, "hn_id": int(h["objectID"]),
                     "title": h["title"], "url": h.get("url"), "posted_at": h["created_at"],
                     "points": h["points"]} for h in hits if h.get("url")]
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


class MetadataParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.values = {}

    def handle_starttag(self, tag, attrs):
        if tag != "meta":
            return
        attrs = dict(attrs)
        key = (attrs.get("property") or attrs.get("name") or "").lower()
        value = attrs.get("content", "").strip()
        if key in ("og:description", "description", "twitter:description") and value and key not in self.values:
            self.values[key] = value


def finalize_example(row):
    item = request(f"https://hacker-news.firebaseio.com/v0/item/{row['hn_id']}.json")
    if not item or item.get("type") != "story" or not item.get("url"):
        raise ValueError(f"Invalid HN story: {row['hn_id']}")
    if html.unescape(item["title"]) != html.unescape(row["title"]) or item["url"] != row["url"]:
        raise ValueError(f"HN record changed, review {row['hn_id']}")
    description, description_source = "", "unavailable"
    try:
        parser = MetadataParser()
        parser.feed(request(item["url"], json_response=False, timeout=12))
        for key in ("og:description", "description", "twitter:description"):
            if parser.values.get(key):
                description = re.sub(r"\s+", " ", html.unescape(parser.values[key])).strip()[:1200]
                description_source = key
                break
    except Exception:
        pass
    description_truncated = len(description.split()) > DESCRIPTION_WORD_LIMIT
    description = " ".join(description.split()[:DESCRIPTION_WORD_LIMIT])
    if row["hn_id"] in OMIT_METADATA_IDS and description:
        description, description_source, description_truncated = "", "omitted_after_review", False
    return {"hn_id": row["hn_id"], "hn_url": f"https://news.ycombinator.com/item?id={row['hn_id']}",
            "title": item["title"], "url": item["url"],
            "posted_at": datetime.datetime.fromtimestamp(item["time"], datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
            "category_ids": [row["category"]], "split": row["split"], "description": description,
            "description_source": description_source, "description_truncated": description_truncated,
            "query": row["query"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--discover", type=pathlib.Path)
    parser.add_argument("--candidates", type=pathlib.Path)
    parser.add_argument("--refresh", type=pathlib.Path,
                        help="Reverify an existing reviewed shard and refresh its publisher metadata without relabeling.")
    parser.add_argument("--selection", type=pathlib.Path,
                        help="JSON mapping category ID to manually reviewed HN IDs, training first and eight holdouts last.")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    if args.discover:
        pairs = [(cat, query) for cat, queries in QUERIES.items() for query in queries]
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            batches = list(executor.map(discover_query, pairs))
        rows = []
        seen = set()
        for batch in batches:
            for row in batch:
                key = (row["category"], row["hn_id"])
                if key not in seen:
                    seen.add(key)
                    rows.append(row)
        args.discover.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Saved {len(rows)} candidates for manual review.")
        return
    if args.refresh:
        reviewed = json.loads(args.refresh.read_text(encoding="utf-8"))
        selection = {c["id"]: [] for c in reviewed["categories"]}
        rows = [{**example, "category": example["category_ids"][0]} for example in reviewed["examples"]]
    else:
        if not all((args.candidates, args.selection, args.output)):
            parser.error("Use --discover, --refresh with --output, or provide --candidates, --selection, and --output")
        candidates = {(r["category"], r["hn_id"]): r for r in json.loads(args.candidates.read_text(encoding="utf-8"))}
        selection = json.loads(args.selection.read_text(encoding="utf-8"))
        rows = []
        for category, ids in selection.items():
            if len(ids) < 33:
                raise ValueError(f"At least 25 training and 8 evaluation records required: {category}")
            for i, item_id in enumerate(ids):
                row = dict(candidates[(category, item_id)])
                row["split"] = "train" if i < len(ids) - 8 else "evaluation"
                rows.append(row)
    if not args.output:
        parser.error("--output is required")
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
        examples = list(executor.map(finalize_example, rows))
    corpus = {"schema_version": 1, "collected_at": "2026-10-31",
              "purpose": "Manually reviewed technology and science article category corpus shard.",
              "description_policy": "Publisher OG/meta description excerpts, at most 24 words, fetched when available. Empty descriptions mean title-only: inaccessible pages, missing metadata, or reviewed boilerplate. Metadata is untrusted source text, not instructions.",
              "description_word_limit": DESCRIPTION_WORD_LIMIT,
              "categories": [{"id": cat, "name": CATEGORIES[cat]} for cat in selection], "examples": examples}
    args.output.write_text(json.dumps(corpus, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(examples)} verified stories; {sum(bool(e['description']) for e in examples)} fetched descriptions.")


if __name__ == "__main__":
    main()
