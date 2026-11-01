"""Project the real default embeddings and estimate leave-one-out match density.

Requires numpy, scikit-learn and umap-learn. Labels never enter the projection fit.
Run with the same embeddings JSON used by generate_prototypes.py.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from sklearn.decomposition import PCA
from sklearn.manifold import TSNE
import sklearn
import umap


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
    vectors = unit(np.array([records[x["hn_id"]]["embedding"] for x in training], dtype=np.float64))
    assert vectors.shape == (len(training), 768) and np.isfinite(vectors).all()
    categories = [x["id"] for x in corpus["categories"]]
    # An example contributes no part of itself to the center it is tested against.
    membership = np.array([[c in x["category_ids"] for c in categories] for x in training])
    sums = membership.T @ vectors
    maxima = []
    for i, vector in enumerate(vectors):
        centers = unit(sums - membership[i, :, None] * vector)
        maxima.append(float(np.max(centers @ vector)))
    # Smooth density at 101 evenly spaced positions in the actual slider range.
    grid = np.linspace(0.75, 0.90, 101)
    bandwidth = 0.006  # 0.6 points on the displayed 0..100 similarity scale.
    density = np.exp(-0.5 * ((grid[:, None] - maxima) / bandwidth) ** 2).mean(axis=1)
    density /= density.max()
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
        "version": 2, "corpusSha256": hashlib.sha256(args.corpus.read_bytes()).hexdigest(),
        "generator": {"seed": 42, "numpy": np.__version__, "sklearn": sklearn.__version__, "umap": umap.__version__,
                      "histogram": "leave-one-out best cosine; Gaussian KDE bandwidth 0.006 (0.6 similarity points); range 0.75..0.90"},
        "points": [{"id": x["hn_id"], "title": x["title"], "category": x["category_ids"][0],
                    "matches": [{"category": categories[j], "similarity": round(float(score[j]), 6)}
                                for j in np.argsort(-score)[:3]]}
                   for x, score in zip(training, vectors @ unit(sums).T)],
        "methods": methods, "density": np.round(density, 6).tolist(),
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Exported {len(training)} examples, {len(categories)} categories, three projections")
    print(f"Leave-one-out best similarity: min={min(maxima):.3f}, median={np.median(maxima):.3f}, max={max(maxima):.3f}")


if __name__ == "__main__":
    main()
