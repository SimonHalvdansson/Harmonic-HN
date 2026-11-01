# Experimental article categories

The reviewed corpus contains 896 unique HN articles: 675 training stories and 221
evaluation stories. A further 40-story HN audit is kept in `corpus_validation.json`
and excluded from reference fitting. These are development datasets, with
editorial topic labels, rather than an independent estimate of HN-wide accuracy.

The 18 categories are AI, Programming, Security, Privacy, Science, Space, Hardware,
Business, Startups, Economy, Finance, World news, Climate, Cars, History, Culture,
Energy, and Health. Reference counts follow coverage needs: Programming and
Hardware have 47 examples, Science has 46, History/Energy/Health have 40, Culture
has 35, and the remaining categories have 34 or 35. Reposts and stories used as
regressions are kept out of training when they also appear in evaluation.

`corpus_technology.json`, `corpus_society.json`, `corpus_finance.json`, and
`corpus_expansion.json` retain the reviewed source records. `corpus_frontpage.json`
is the initial front-page snapshot plus the Talkie regression. `merge_corpus.py`
produces the canonical corpus and groups article URLs across shards before
assigning splits. The separate audit is never included in that merge.

Each record retains its official HN ID, source URL, title, posting date, split,
category IDs, and description provenance. Additional acceptable labels make
legitimate overlaps explicit in evaluation. Primary coverage measures whether at
least one expected topic appears; label precision counts all emitted labels
against the accepted topics. Unknown stories should remain unlabeled.

## Match the app's text pipeline

`RefreshDescriptions` compiles the app's `HtmlDescriptionExtractor` and uses its
metadata selection and paragraph fallback, capped at 600 characters. It reads at
most 1 MiB of HTML. The app's generic HTML previews call that same bounded helper.
Specialized provider/API previews and later publisher changes can supply different
context, so snapshots and title-only evaluation remain necessary.

`ArticleEmbeddingInput` is compiled directly from core into the generator. It
collapses whitespace, bounds the headline to 300 characters and useful preview
text to 600, rejects duplicated titles and obvious boilerplate, and formats:

```text
title: HEADLINE | text: PREVIEW
```

A missing or unusable preview is replaced with the headline. No AI summary or full
article body is embedded. Records marked `description_review: "omit"` remain
excluded during refresh because their URLs redirected to unrelated/generic pages.

## Two reference views

Title-only stories are compared with title-only references. Stories with useful
preview text are compared with contextual references; those references exclude
examples without useful previews. Both views use the same fixed mobile model,
768 dimensions, L2 normalization, and a maximum input length of 256 tokens.

For each view, fit a background vector using training examples only. Subtract it
from each normalized vector and normalize again. A category center is the
normalized mean of its projected examples. Apply the same projection to a query
before computing cosine similarity. Removing the shared background prevents a
large generic similarity from making neighboring topics appear equally strong.

The default cutoff is **0.24**, displayed as 24/100; it is not a probability.
The slider spans 10–45, covering the useful range observed on the HN audit.
Additional labels must also reach 75% of the strongest known topic's score, even
when that strongest topic is deselected. Up to three selected labels are shown.
Saved cutoffs are clamped to the slider range. Categorization has not shipped in v3.2,
so there is no migration for intermediate development defaults.

Live scoring needs one inference per input and uses the existing preview cache.
Adding a history example stores both input views, reusing cached inference where
possible. Removing or editing examples rebuilds both sets of centers without
repeating their inference. Empty categories abstain. Model removal preserves
user categories and examples; inference and score caches remain session-local.

## Reproduce generation

Use Android Studio's JBR as `JAVA_HOME` (on this machine,
`C:/Program Files/Android/Android Studio1/jbr`). The standalone JVM project imports
the repository's version catalog, so Kotlin, Ksoup, and LiteRT-LM match the app.

Download the pinned text-only model outside the repository:

```text
https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/9be6e8b90982095dc05c2bd162e4b954ee4dbac7/embeddinggemma-2-text-270m.litertlm
```

Its SHA-256 is
`2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb`
and its size is 164,626,432 bytes. The runner records the actual model hash, input
version, source-text hash, and both native vectors. Generation rejects stale
source text or mismatched model/input provenance.

Run from the repository root, replacing placeholders with absolute paths. Quote
individual paths inside `--args` if they contain spaces. Keep models, raw vectors,
evaluation reports, and native caches outside the repository.

```powershell
./gradlew -p tools/article_categories run -PmainClass=RefreshDescriptions --args='CORPUS REFRESHED_CORPUS'
python tools/article_categories/merge_corpus.py
./gradlew -p tools/article_categories run --args='MODEL CORPUS EMBEDDINGS_JSON'
python tools/article_categories/generate_prototypes.py EMBEDDINGS_JSON --kotlin core/src/commonMain/kotlin/com/simon/harmonichackernews/categorization/ArticleCategoryPrototypes.kt --training-data resources/src/commonMain/composeResources/files/article_categories.bin --report REPORT_JSON
```

Refresh/review each source shard before merging it. The refreshed output must
replace its reviewed shard; a merge cannot consume pending metadata from the old
Python collector. `collect_corpus.py` can independently verify canonical titles
and URLs against the official Firebase API. Keyword search results are candidates,
never automatic training labels.

The optional fourth runner argument selects `document`, `classification`, or
`clustering` for controlled prompt experiments. The fifth argument is an older
embedding JSON to reuse: only vectors from the same model and exact input text
are reused. Production exports accept the shared document formatter only.

Evaluate without modifying fitted references:

```powershell
python tools/article_categories/evaluate_categories.py EMBEDDINGS_JSON --validation tools/article_categories/corpus_validation.json --validation-embeddings AUDIT_EMBEDDINGS_JSON --report REPORT_JSON
python -m unittest discover -s tools/article_categories -p 'test_*.py'
```

The HNC2 training resource contains a JSON header (stories, assignments, model and
representation versions), then paired preview/title vectors as little-endian
float32 arrays in story order. It is about 4.4 MB and contains no model weights.
Kotlin defaults are exported separately for fast scoring without loading this
editable resource. Tests compare both rebuilt views with the exported defaults.

## Exploration asset

`generate_exploration.py` uses NumPy, scikit-learn, and umap-learn. It projects the
675 title-only reference vectors after background subtraction using UMAP, t-SNE,
and PCA. UMAP/t-SNE use seed 42; labels never enter the projection fit. All dots,
including outliers, share the same coordinate scale. Classification continues to
use the original 768-dimensional view, rather than the two-dimensional map.

```powershell
python tools/article_categories/generate_exploration.py EMBEDDINGS_JSON --output resources/src/commonMain/composeResources/files/article_category_exploration.json
```

Point comparisons include their own reference in the center, and illustrate the
headline view.
The asset records corpus/input/representation versions and projection libraries;
the UI rejects an incompatible representation.

## Bundled cutoff curve

The slider uses a fixed sample of 100 popular linked stories in
`corpus_coverage.json`, collected from HN's current top/best feeds on 2026-11-01.
The sample is uniform with seed 42 among 147 eligible unique articles with at least
100 points, excluding training URLs. Its stories never enter reference fitting.
The frozen snapshot retains the source feeds, candidate IDs, collection time,
points, titles, URLs, and extracted description provenance.

The shared Ksoup extractor found useful previews for 81 stories; the remaining
19 use title-only input. `generate_coverage.py` scores them with the matching
reference view and exports their best similarities to `CategoryCoverageReference.kt`.
Line height is the share of the complete sample above each cutoff, including
stories scoring below the slider minimum. It is not normalized to peak at 100%.
This is a fixed guide using the bundled categories, not an accuracy measure or a
prediction for personal categories, category selections, or edited examples.
The curve is available immediately without a model, network, or browsing history.

```powershell
python tools/article_categories/collect_coverage.py --output RAW_SAMPLE_JSON
./gradlew -p tools/article_categories run -PmainClass=RefreshDescriptions --args='RAW_SAMPLE_JSON SAMPLE_CORPUS_JSON'
./gradlew -p tools/article_categories run --args='MODEL SAMPLE_CORPUS_JSON SAMPLE_EMBEDDINGS_JSON'
python tools/article_categories/generate_coverage.py SAMPLE_EMBEDDINGS_JSON --sample SAMPLE_CORPUS_JSON --reference-embeddings EMBEDDINGS_JSON --kotlin ui/src/commonMain/kotlin/com/simon/harmonichackernews/ui/settings/CategoryCoverageReference.kt --report SCORE_REPORT_JSON
```

Use the absolute path to `corpus_coverage.json` for `SAMPLE_CORPUS_JSON`.
Keep raw vectors and score reports outside the repository. The generated file
records model, input, representation, reference corpus, and sample fingerprints.
