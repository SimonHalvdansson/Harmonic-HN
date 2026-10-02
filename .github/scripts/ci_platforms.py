"""Select platform jobs conservatively; shared or unknown inputs run every platform."""

import os
from pathlib import Path, PurePosixPath
import subprocess


PLATFORMS = frozenset({"android", "desktop", "ios"})


def platforms_for_paths(paths):
    selected = set()
    for path in paths:
        parts = PurePosixPath(path).parts
        name = parts[-1]
        # Build configuration can affect resolution/configuration on every host.
        if name.endswith((".gradle", ".gradle.kts")) or parts[0] in {".github", "gradle"}:
            return set(PLATFORMS)
        if parts[0] == "docs" or (len(parts) == 1 and name.endswith(".md")):
            continue
        if parts[0] in {"app", "benchmark"}:
            selected.add("android")
        elif parts[0] == "local_ai_runtime":
            selected.add("android")
            if path.startswith("local_ai_runtime/src/main/cpp/"):
                selected.add("desktop")
        elif parts[0] == "desktop_app":
            selected.add("desktop")
        elif parts[0] == "ios_app":
            selected.add("ios")
        elif len(parts) > 3 and parts[0] in {"core", "ui", "resources"} and parts[1] == "src":
            source_set = parts[2]
            if source_set.startswith("android"):
                selected.add("android")
            elif source_set.startswith("desktop"):
                selected.add("desktop")
            elif source_set.startswith("ios"):
                selected.add("ios")
            else:
                return set(PLATFORMS)
        else:
            return set(PLATFORMS)
    return selected


def select_platforms(event, base, head):
    if event == "workflow_dispatch" or not base or set(base) == {"0"}:
        return set(PLATFORMS)
    try:
        # Disabling rename detection includes both old and new paths. NUL
        # delimiters preserve filenames containing whitespace/newlines.
        result = subprocess.run(
            ["git", "diff", "--name-only", "--no-renames", "-z", base, head, "--"],
            check=True, capture_output=True,
        )
    except subprocess.CalledProcessError:
        # A force-push may make the previous SHA unavailable. Never skip work
        # because the comparison could not be computed.
        print("::warning::Changed-path comparison unavailable; running every platform")
        return set(PLATFORMS)
    return platforms_for_paths(os.fsdecode(p) for p in result.stdout.split(b"\0") if p)


if __name__ == "__main__":
    selected = select_platforms(
        os.environ["GITHUB_EVENT_NAME"], os.environ.get("BASE_SHA", ""), os.environ["HEAD_SHA"],
    )
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        for platform in sorted(PLATFORMS):
            print(f"{platform}={str(platform in selected).lower()}", file=output)
    print("Selected platforms: " + (", ".join(sorted(selected)) or "none"))
