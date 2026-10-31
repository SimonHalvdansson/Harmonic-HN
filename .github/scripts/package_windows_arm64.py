"""Package the native Windows ARM64 app image as a regular Inno Setup installer."""

import argparse
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess


def require_arm64_pe(path):
    with path.open("rb") as source:
        if source.read(2) != b"MZ":
            raise ValueError(f"Not a Windows executable: {path}")
        source.seek(0x3C)
        offset = struct.unpack("<I", source.read(4))[0]
        source.seek(offset)
        signature, machine = struct.unpack("<4sH", source.read(6))
    if signature != b"PE\0\0" or machine != 0xAA64:
        raise ValueError(f"Expected a native ARM64 executable, not machine {machine:#x}: {path}")


def verify_image(image):
    require_arm64_pe(image / "Harmonic.exe")
    require_arm64_pe(image / "runtime/bin/server/jvm.dll")
    # jlink omits command-line launchers unless AppCDS needs them during training.
    java = image / "runtime/bin/java.exe"
    if java.is_file():
        require_arm64_pe(java)
    config = image / "app/Harmonic.cfg"
    if not config.is_file():
        raise ValueError("The release image is missing its launcher configuration")
    if "-XX:SharedArchiveFile=" in config.read_text(encoding="utf-8"):
        raise ValueError("Windows ARM64 AppCDS archives do not relocate on JDK 21; "
                         "build with -PharmonicDesktopAppCds=false")


def find_compiler():
    compiler = shutil.which("ISCC.exe")
    if compiler:
        return compiler
    for variable in ("ProgramFiles(x86)", "ProgramFiles"):
        root = os.environ.get(variable)
        if root:
            candidate = Path(root) / "Inno Setup 6/ISCC.exe"
            if candidate.is_file():
                return str(candidate)
    raise FileNotFoundError("Install Inno Setup 6 or put ISCC.exe on PATH")


def package(image, metadata, destination, compiler=None):
    verify_image(image)
    properties = dict(line.split("=", 1) for line in metadata.read_text().splitlines()
                      if line and not line.startswith("#") and "=" in line)
    version = properties["versionName"]
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError(f"Invalid desktop release version: {version!r}")
    destination.mkdir(parents=True, exist_ok=True)
    script = Path(__file__).resolve().parents[2] / "desktop_app/native/windows/harmonic-arm64.iss"
    subprocess.run([compiler or find_compiler(), "/Qp", f"/DAppImage={image.resolve()}",
                    f"/DAppVersion={version}", f"/DInstallerOutput={destination.resolve()}",
                    str(script)], check=True)
    installer = destination / f"Harmonic-{version}-windows-arm64.exe"
    if not installer.is_file():
        raise FileNotFoundError(installer)
    print(f"Built Windows ARM64 installer: {installer}")
    return installer


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path,
                        default=Path("desktop_app/build/compose/binaries/main-release/app/Harmonic"))
    parser.add_argument("--metadata", type=Path,
                        default=Path("desktop_app/build/generated/desktopMetadata/harmonic-desktop.properties"))
    parser.add_argument("--destination", type=Path,
                        default=Path("desktop_app/build/compose/binaries/main-release/exe"))
    args = parser.parse_args()
    package(args.image, args.metadata, args.destination)
