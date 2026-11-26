"""Launch the app from the release DMG after copying it off the mounted volume."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile


def verify(dmg: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="harmonic-dmg-") as directory:
        root = Path(directory)
        volume = root / "volume"
        volume.mkdir()
        app = root / "Harmonic.app"
        subprocess.run(["hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", volume, dmg], check=True)
        try:
            subprocess.run(["ditto", volume / app.name, app], check=True)
        finally:
            subprocess.run(["hdiutil", "detach", volume], check=True)

        # On CI the original build paths still exist and could mask a broken archive.
        config = (app / "Contents/app/Harmonic.cfg").read_text()
        if "java-options=-XX:SharedArchiveFile=" in config:
            raise RuntimeError("The macOS launcher still selects a prebuilt AppCDS archive")

        # Training mode uses an in-memory profile and exits after showing the main window.
        # Run the native launcher with the JVM options and classpath shipped in the DMG.
        with tempfile.TemporaryFile(mode="w+") as log:
            try:
                result = subprocess.run(
                    [app / "Contents/MacOS/Harmonic"],
                    env={**os.environ, "JAVA_TOOL_OPTIONS": "-Dcompose.aot.training-run=true"},
                    stdout=log, stderr=subprocess.STDOUT, timeout=90,
                )
            finally:
                log.seek(0)
                output = log.read()
                print(output, end="", flush=True)
        result.check_returncode()
        if "Harmonic main window opened" not in output.splitlines():
            raise RuntimeError("The packaged app exited before showing its main window")


if __name__ == "__main__":
    [dmg] = sys.argv[1:]
    verify(Path(dmg))
