"""Check the packaged AppCDS archive using its own runtime and launcher classpath."""

import os
from pathlib import Path
import subprocess
import sys


def verify(root: Path) -> None:
    archives = list(root.rglob("app.jsa"))
    if len(archives) != 1:
        raise RuntimeError(f"Expected one packaged app.jsa in {root}, found {archives}")
    archive = archives[0].resolve()
    app_dir = archive.parent
    configs = list(app_dir.glob("*.cfg"))
    if len(configs) != 1:
        raise RuntimeError(f"Expected one launcher configuration in {app_dir}")

    classpath = []
    archive_option = "-XX:SharedArchiveFile=$APPDIR/app.jsa"
    uses_archive = False
    for line in configs[0].read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if not separator:
            continue
        if key == "app.classpath":
            classpath.append(value.replace("$APPDIR", str(app_dir)))
        elif key == "java-options" and value == archive_option:
            uses_archive = True
    if not classpath or not uses_archive:
        raise RuntimeError("The packaged launcher must use its classpath and AppCDS archive")

    runtime = app_dir.parent / "runtime"
    java = next((path for path in (
        runtime / "Contents/Home/bin/java",
        runtime / "bin/java",
        runtime / "bin/java.exe",
    ) if path.is_file()), None)
    if java is None:
        raise RuntimeError(f"Missing packaged Java runtime in {runtime}")
    result = subprocess.run(
        [str(java), "-Xshare:on", f"-XX:SharedArchiveFile={archive}",
         "-cp", os.pathsep.join(classpath), "-version"],
        capture_output=True, text=True, timeout=60,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Packaged AppCDS archive could not be loaded:\n{result.stdout}{result.stderr}")
    print(f"Verified packaged AppCDS archive ({archive.stat().st_size / 1024 ** 2:.1f} MiB): {archive}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
