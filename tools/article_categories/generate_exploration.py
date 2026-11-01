"""Project the title references for the category exploration map.

Requires numpy, scikit-learn and umap-learn. Labels never enter the projection fit.
Run with the same embeddings JSON used by generate_prototypes.py.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from sklearn.decomposition import PCA
from sklearn.manifold import TSNE
import sklearn
import umap
from category_model import corpus_fingerprint, fit, policy, project


def unit(x):
    return x / np.linalg.norm(x, axis=-1, keepdims=True)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("embeddings", type=Path)
    parser.add_argument("--corpus", type=Path, default=Path(__file__).with_name("corpus.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    records = {x["hn_id"]: x for x in json.loads(args.embeddings.read_text(encoding="utf-8"))}
    training = [x for x in corpus["examples"] if x["split"] == "train"]
    origin, _, raw = fit(corpus,records,title_only=True)
    vectors = np.array([project(raw[x['hn_id']],origin) for x in training],dtype=np.float64)
    assert vectors.shape == (len(training), 768) and np.isfinite(vectors).all()
    categories = [x["id"] for x in corpus["categories"]]
    membership = np.array([[c in x["category_ids"] for c in categories] for x in training])
    sums = membership.T @ vectors
    # PCA preserves overall linear variation; UMAP/t-SNE focus on neighborhoods.
    projections = {
        "pca": PCA(n_components=2, svd_solver="full").fit_transform(vectors),
        "umap": umap.UMAP(n_components=2, n_neighbors=20, min_dist=0.18,
                          metric="cosine", random_state=42, n_jobs=1).fit_transform(vectors),
        "tsne": TSNE(n_components=2, perplexity=30, metric="cosine", init="pca",
                     learning_rate="auto", max_iter=1500, random_state=42, n_jobs=1).fit_transform(vectors),
    }
    methods = []
    for key in ("umap", "tsne", "pca"):
        xy = projections[key]
        # Shared scale for both axes avoids distorting distances; leave all outliers visible.
        xy = (xy - (xy.min(axis=0) + xy.max(axis=0)) / 2) / np.ptp(xy, axis=0).max() + 0.5
        methods.append({"id": key, "positions": np.round(xy, 6).tolist()})
    payload = {
        "version": 4, "corpusSha256": corpus_fingerprint(corpus),
        "inputVersion":policy()['input_version'],"representationVersion":policy()['representation_version'],"inputView":"title_only",
        "generator": {"seed": 42, "numpy": np.__version__, "sklearn": sklearn.__version__, "umap": umap.__version__},
        "points": [{"id": x["hn_id"], "title": x["title"], "category": x["category_ids"][0],
                    "matches": [{"category": categories[j], "similarity": round(float(score[j]), 6)}
                                for j in np.argsort(-score)[:3]]}
                   for x, score in zip(training, vectors @ unit(sums).T)],
        "methods": methods,
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8",newline='\n')
    print(f"Exported {len(training)} examples, {len(categories)} categories, three projections")


if __name__ == "__main__":
    main()
