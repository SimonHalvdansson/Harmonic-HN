"""The exported centered-cosine model and its evaluation, independent of inference."""
import math
import hashlib
import json
import re
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = ROOT / 'core/src/commonMain/kotlin/com/simon/harmonichackernews/categorization'
MODEL_SHA256 = '2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb'


@lru_cache(maxsize=1)
def policy():
    categories = (KOTLIN / 'ArticleCategories.kt').read_text(encoding='utf-8')
    inputs = (KOTLIN / 'ArticleEmbeddingInput.kt').read_text(encoding='utf-8')
    number = lambda name: float(re.search(r'const val ' + name + r' = ([0-9.]+)f', categories)[1])
    return dict(cutoff=number('DEFAULT_CUTOFF'), minimum=number('MIN_CUTOFF'), maximum=number('MAX_CUTOFF'),
                secondary_ratio=number('SECONDARY_RATIO'),
                input_version=re.search(r'const val VERSION = "([^"]+)"', inputs)[1],
                representation_version=re.search(r'const val REPRESENTATION_VERSION = "([^"]+)"', categories)[1])


def normalize(vector):
    if len(vector) != 768 or not all(math.isfinite(x) for x in vector):
        raise ValueError('Expected 768 finite dimensions')
    length = math.sqrt(sum(x*x for x in vector))
    if not length: raise ValueError('Zero embedding')
    return [x / length for x in vector]


def representation(record, title_only=False):
    p = policy()
    if record.get('input_format') != p['input_version'] or record.get('model_sha256') != MODEL_SHA256:
        raise ValueError('Embedding input/model provenance does not match the app; regenerate vectors')
    return normalize(record['title_embedding'] if title_only else record['embedding'])


def has_preview(record):
    return record['input'] != record['title_input']


def corpus_fingerprint(corpus):
    # Stable across Windows/Unix line endings and JSON indentation.
    return hashlib.sha256(json.dumps(corpus,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf-8')).hexdigest()


def validate_records(corpus, records):
    for example in corpus['examples']:
        record=records[example['hn_id']]
        expected=hashlib.sha256((example['title']+'\0'+example['description']).encode('utf-8')).hexdigest()
        if record.get('source_sha256') != expected:
            raise ValueError(f"Stale embedding input for HN {example['hn_id']}; rerun EmbedArticles, optionally reusing unchanged vectors")


def project(vector, origin):
    return normalize([x-y for x,y in zip(normalize(vector),origin)])


def fit(corpus, records, title_only=False):
    validate_records(corpus,records)
    training = [x for x in corpus['examples'] if x['split'] == 'train']
    raw = {x['hn_id']: representation(records[x['hn_id']],title_only) for x in training}
    contextual = training if title_only else [x for x in training if has_preview(records[x['hn_id']])]
    contextual = contextual or training
    background = [raw[x['hn_id']] for x in contextual]
    origin = [sum(values)/len(background) for values in zip(*background)]
    projected = {key: project(vector,origin) for key,vector in raw.items()}
    centers = {}
    for category in corpus['categories']:
        members = [projected[x['hn_id']] for x in contextual if category['id'] in x['category_ids']]
        if not members: members = [projected[x['hn_id']] for x in training if category['id'] in x['category_ids']]
        if len(members) < 2: raise ValueError('Each default category needs at least two training examples')
        centers[category['id']] = normalize([sum(values) for values in zip(*members)])
    return origin, centers, raw


def scores(vector, origin, centers):
    query = project(vector, origin)
    return dict(sorted(((key,sum(x*y for x,y in zip(query,center))) for key,center in centers.items()),key=lambda x:-x[1]))


def labels(values, cutoff=None):
    p = policy()
    cutoff = p['cutoff'] if cutoff is None else cutoff
    strongest = max(values.values())
    return [key for key,value in sorted(values.items(),key=lambda x:-x[1])
            if value >= cutoff and value >= strongest*p['secondary_ratio']][:3]


def summarize(rows, field):
    result = dict(stories=len(rows), labeled=0, primary_matches=0, correct_labels=0, extra_labels=0, unknown=0, unknown_tagged=0)
    for row in rows:
        predicted = set(row[field]['predicted']); expected = set(row['expected']); accepted = set(row['acceptable'])
        result['labeled'] += bool(expected); result['primary_matches'] += bool(predicted & expected)
        result['correct_labels'] += len(predicted & accepted); result['extra_labels'] += len(predicted - accepted)
        result['unknown'] += not bool(expected); result['unknown_tagged'] += bool(predicted) and not bool(expected)
    result['label_precision'] = result['correct_labels'] / max(1,result['correct_labels']+result['extra_labels'])
    result['primary_coverage'] = result['primary_matches'] / max(1,result['labeled'])
    return result
