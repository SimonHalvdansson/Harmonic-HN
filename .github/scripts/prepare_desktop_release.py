"""Give tagged desktop installers consistent, collision-free public filenames."""

import glob
from pathlib import Path
import re
import shutil
import sys


EXTENSIONS = {"macos": ".dmg", "windows": ".msi", "windows-arm64": ".exe",
              "linux": ".deb", "linux-arm64": ".deb"}


def prepare(package_pattern, platform, metadata, destination):
    extension = EXTENSIONS[platform]
    packages = [Path(p) for p in glob.glob(package_pattern, recursive=True) if Path(p).is_file()]
    if len(packages) != 1 or packages[0].suffix != extension:
        raise ValueError(f"Expected one {extension} package, found {packages}")
    # Use the version actually embedded by Gradle, including any build-time override.
    properties = dict(line.split("=", 1) for line in metadata.read_text().splitlines()
                      if line and not line.startswith("#") and "=" in line)
    version = properties["versionName"]
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError(f"Invalid desktop release version: {version!r}")
    destination.mkdir(parents=True, exist_ok=True)
    output = destination / f"Harmonic-{version}-{platform}{extension}"
    if output.exists():
        raise FileExistsError(output)
    shutil.copy2(packages[0], output)
    print(output)
    return output


if __name__ == "__main__":
    prepare(sys.argv[1], sys.argv[2],
            Path("desktop_app/build/generated/desktopMetadata/harmonic-desktop.properties"),
            Path("desktop-release"))
