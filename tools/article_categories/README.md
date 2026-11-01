# Experimental article categories

`corpus.json` contains 622 manually reviewed articles posted to Hacker News between
2021 and 2026. Each record keeps its HN item ID, HN link, article URL, posting date,
title, category IDs, split, and publisher preview description when available.
Titles, URLs, and dates were collected using HN Algolia and independently verified
against the official Firebase API on 2026-10-31, with Finance verified on 2026-11-01. There are 427 fetched publisher
metadata excerpts; 195 records use titles only because metadata was missing,
blocked, generic, or unrelated after a redirect. Description provenance is explicit.

The 14 defaults are AI, Programming, Security, Privacy, Science, Space, Hardware,
Business, Startups, Economy, Finance, World news, Climate, and Cars. Each has 35
training examples and eight evaluation examples: 490 training and 112 labeled
holdouts, plus 20 unrelated holdouts. These defaults can be edited in the app;
users can also create categories taught with stories from their history.

`corpus_technology.json`, `corpus_society.json`, and `corpus_finance.json` retain
the reviewed collection shards. Finance covers banking, payments, investing, and
personal finance. Tesla is no longer a standalone category. `merge_corpus.py` produces the canonical corpus and prevents reposts from
appearing on both sides of the training/evaluation split. Discovery spans multiple
subtopics per category; a person reviewed exact headlines and source URLs rather
than accepting keyword-search results directly. `collect_technology_corpus.py`
supports reviewed candidate discovery and metadata refresh; `collect_corpus.py`
verifies the canonical corpus against Firebase.

Use only `split == "train"` when averaging category vectors. Generate embeddings
with the same EmbeddingGemma 2 text model, dimensionality, input prefix, and feature
format used by the app; normalize each example, average within each category, then
normalize the resulting mean. Keep the model identity and preprocessing version
with exported prototypes. Changing the model or its prompt requires regeneration.
The generation script in this directory provides the executable workflow.

## Generate the app prototypes

Use Android Studio's JBR as `JAVA_HOME` (on this Windows machine,
`C:/Program Files/Android/Android Studio1/jbr`). The standalone Gradle project
uses LiteRT-LM JVM 0.19.0, the same runtime version as Android, on one CPU thread.
It supports Windows, Linux and macOS; no Python inference packages are needed.

Download the text-only artifact outside the repository:

```text
https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/9be6e8b90982095dc05c2bd162e4b954ee4dbac7/embeddinggemma-2-text-270m.litertlm
```

Verify its SHA-256 before generation:
`2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb`
(164,626,432 bytes). Source model card:
[EmbeddingGemma 2 Text 270M](https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm).

Run from the repository root, substituting absolute paths for `MODEL`, `CORPUS`
and the output files. Keep both output JSON files outside the repository.

```powershell
./gradlew -p tools/article_categories run --args='MODEL CORPUS EMBEDDINGS_JSON'
python tools/article_categories/generate_prototypes.py EMBEDDINGS_JSON --kotlin core/src/commonMain/kotlin/com/simon/harmonichackernews/categorization/ArticleCategoryPrototypes.kt --training-data resources/src/commonMain/composeResources/files/article_categories.bin --report EVALUATION_JSON
```

The Java runner creates the cache beside `EMBEDDINGS_JSON`; quote individual paths
inside `--args` if they contain spaces. The model uses 768 output dimensions,
L2 normalization, and a maximum input length of 256 tokens. Preprocessing is
`title: TITLE | text: DESCRIPTION`, with whitespace collapsed, title limited to
300 characters, description to 900, and a missing description replaced by the
title. This is document-to-document similarity, using the same representation
for examples and live articles. Do not mix query/task prefixes into one side.

The checked-in Kotlin vectors were actually generated with this artifact and
workflow on 2026-10-31, with Finance generated on 2026-11-01. Each vector is the normalized mean of the 35 normalized
title-plus-description training embeddings for that category. Held-out records
are never included in the means. Kotlin output records the corpus and model
hashes so generation can be audited.

The generated `article_categories.bin` is an HNC1 resource: four magic bytes,
little-endian uint32 JSON-header length, UTF-8 JSON metadata (stories, category
assignments, model hash), then one 768-dimensional float32 little-endian vector per
training story in header order. It is about 1.65 MB. This lets the app display and
remove default examples and recompute their means without re-embedding hundreds
of articles on launch. No model weights are bundled.

To check the saved titles and URLs against the official HN Firebase API:

```powershell
python tools/article_categories/collect_corpus.py
```

To retrieve possible replacements without changing the corpus:

```powershell
python tools/article_categories/collect_corpus.py --candidates "$env:TEMP/hn-category-candidates.json"
```

Review each candidate's title, URL, date, category, and description manually before
editing the corpus. A broad search can return unrelated posts; the query is retained
only as a discovery aid. Do not promote the first result without reviewing it.

## Limits and evaluation

This is an editorial seed corpus for an opt-in experiment, not a representative
sample of HN. Publisher metadata and blocked-page coverage differ by topic, and
title keyword discovery can introduce sampling bias. Labels describe the
main editorial topic rather than every valid overlapping topic: AlphaFold can also
be science, AI legislation can also concern AI, and security can also concern world
events. Category names are presentation labels; renaming one must not change its ID
or regenerate its meaning.

Inspect held-out similarities before choosing a default cutoff. Evaluate both
title-only inputs and title plus description, and report false positives on the
unclassified examples. Retrieved metadata can change over time; fixed snapshots
make this experiment reproducible. Keep low-scoring
stories unlabeled, allow overlapping categories, and treat cosine scores as
similarities rather than probabilities. A higher cutoff means fewer labels.

At the default cutoff of **0.78**, with at most three labels, the 112 labeled
holdouts produce 96 correct primary-topic labels and 121 extra labels using
available metadata. Title-only inputs produce 110 correct and 209 extra labels.
11 of 20 unrelated holdouts stay unlabeled with metadata, and 4 with titles only.
Finance matches five of eight holdouts with metadata and all eight with titles
only. This development set is not an independent accuracy estimate. The lower
cutoff improves coverage but also increases overlap and false positives.
Additional labels may be reasonable overlaps (Finance/Economy, Startups/Business),
but count as false positives against the corpus's single-primary-topic labels.

The app keeps a bounded session cache of embeddings and performs inference only for
composed feed and submissions rows after a short delay. A separate, bounded cache
of 1,024 scored story identities supplies immediate tags when rows return or a
comments screen opens. Known scores survive native-engine unloading in the
background; the larger embedding cache does not. Training changes invalidate
scores and reuse existing embeddings. Completed native work is retained when a
row leaves the composition, and title-only requests never replace richer results.
Scoring and cache updates run off the UI thread. The cache is session-local and
does not survive process termination. Changing names, selections, or the cutoff
reuses cached embeddings. Training changes recompute means and re-score stories
without repeating their inference. It reuses existing preview descriptions and never fetches
full article bodies for this feature. EmbeddingGemma is separate from the
summarization model picker. It loads after the first frame when opted in, and
turning categorization off releases its native engine and embedding cache. Android
also releases them when the app goes into the background or receives a memory-pressure
callback, and checks system memory before loading. Foregrounding resumes the model
when memory is available; after a foreground pressure callback, the settings screen
offers an explicit resume action. Native operations finish safely before disposal.
Android 14 and later do not deliver the legacy foreground low-memory callbacks, so
this is best-effort resource management, not a guarantee against process termination.
Adding history examples while the feature is off loads the model temporarily and
releases it afterward. Batches are saved atomically after successful inference;
cancellation before commit saves none of that batch. The app rechecks history membership before
saving. User categories, excluded default IDs, and normalized history vectors
persist locally and survive removing/re-downloading the model.

No article HTML, full text, credentials, model weights, or diagnostic captures are
stored with the corpus. Download models and write evaluation output to a temporary
directory, keeping only deliberately exported app prototypes in source control.

The exploration screen uses `generate_exploration.py` to export all 490 training
examples into `resources/src/commonMain/composeResources/files/article_category_exploration.json`.
It fits UMAP (cosine, 20 neighbors, min_dist 0.18), t-SNE (cosine, perplexity 30,
1,500 iterations), and PCA to the original normalized vectors, without using
category labels. UMAP and t-SNE use seed 42. Each projection preserves a shared
scale across its two axes and includes every point, including outliers. The asset
records library versions and corpus SHA-256. Two-dimensional distances are for
exploration; classification always uses the original 768-dimensional space.

The slider density is a Gaussian kernel estimate (bandwidth 0.006, or 0.6 similarity points) of each
training example’s best category cosine score. Each example is subtracted from
its own category’s vector sum before scoring, so the curve does not benefit from
self-inclusion. It is a fixed reference distribution over the default examples,
not a probability or an estimate of real-feed accuracy.

Generate the exploration asset with Python packages numpy, scikit-learn and
umap-learn (versions used are recorded in the asset):

```sh
python tools/article_categories/generate_exploration.py /tmp/embeddings.json \
  --output resources/src/commonMain/composeResources/files/article_category_exploration.json
```

The curve is sampled over the slider range 75–90. Each map example also stores
its three highest full-dimensional cosine scores against the default category
centers. These use the same scores as classification, including the example in
its own default center; only the histogram uses leave-one-out scores. The map
interpolates points, labels and connectors in Draw rather than recomputing a
projection or recomposing every point during an animation.
