"""Randomly sample popular HN stories for the bundled cutoff curve, never training."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import html
import json
from pathlib import Path
import random
from urllib.request import Request, urlopen
from merge_corpus import article_key

SOURCES = ['https://hacker-news.firebaseio.com/v0/beststories.json',
           'https://hacker-news.firebaseio.com/v0/topstories.json']


def fetch(url):
    request = Request(url, headers={'User-Agent': 'Harmonic-Category-Coverage/1.0'})
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--training-corpus', type=Path, default=Path(__file__).with_name('corpus.json'))
    parser.add_argument('--seed', type=int, default=42)
    args = parser.parse_args()
    training = json.loads(args.training_corpus.read_text(encoding='utf-8'))
    excluded = {article_key(x['url']) for x in training['examples'] if x['split'] == 'train'}
    ids = list(dict.fromkeys(item_id for source in SOURCES for item_id in fetch(source)))
    with ThreadPoolExecutor(max_workers=8) as executor:
        rows = list(executor.map(lambda item_id: fetch(f'https://hacker-news.firebaseio.com/v0/item/{item_id}.json'), ids))
    eligible = {}
    for row in rows:
        if not row or row.get('type') != 'story' or row.get('dead') or row.get('deleted') or row.get('score', 0) < 100:
            continue
        if not row.get('url', '').startswith(('http://', 'https://')):
            continue
        key = article_key(row['url'])
        if key not in excluded:
            eligible.setdefault(key, row)
    if len(eligible) < 100:
        raise ValueError(f'Only {len(eligible)} eligible stories in {len(ids)} current top/best stories')
    sample = random.Random(args.seed).sample(list(eligible.values()), 100)
    payload = dict(collected_at=datetime.now(timezone.utc).isoformat(), sources=SOURCES, seed=args.seed,
                   minimum_points=100, candidate_ids=ids, eligible_count=len(eligible),
                   selection='Uniform sample of 100 unique linked stories with at least 100 points; training URLs excluded',
                   examples=[dict(hn_id=x['id'], title=html.unescape(x['title']), url=x['url'],
                                  score=x['score'], posted_at=x['time'], description='',
                                  category_ids=[], split='coverage') for x in sample])
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f"Sampled {len(sample)} stories from {len(eligible)} eligible of {len(ids)} top/best stories; seed {args.seed}")
    print(f"Points: {min(x['score'] for x in sample)}..{max(x['score'] for x in sample)}")


if __name__ == '__main__':
    main()
