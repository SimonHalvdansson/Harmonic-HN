"""Compile and exercise the real JNI boundary on macOS/Linux using JAVA_HOME or PATH."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent
repo = root.parents[4]
java_home = Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else Path(shutil.which("javac")).resolve().parents[1]
platform = "darwin" if sys.platform == "darwin" else "linux"
with tempfile.TemporaryDirectory(prefix="harmonic-jni-utf8-") as temporary:
    output = Path(temporary)
    library = output / ("libjni_utf8.dylib" if platform == "darwin" else "libjni_utf8.so")
    include = java_home / "include"
    if os.environ.get("JNI_HEADER"):
        include = output / "jni"
        include.mkdir()
        shutil.copyfile(os.environ["JNI_HEADER"], include / "jni.h")
    subprocess.run([
        os.environ.get("CXX", "c++"), "-std=c++17", "-dynamiclib" if platform == "darwin" else "-shared", "-fPIC",
        "-I" + str(include), "-I" + str(include / platform),
        "-I" + str(root / "include"), "-I" + str(repo / "local_ai_runtime/src/main/cpp"),
        str(repo / "local_ai_runtime/src/main/cpp/local_llama.cpp"), str(root / "engine_stub.cpp"), "-o", str(library),
    ], check=True)
    subprocess.run([str(java_home / "bin/javac"), "-d", str(output), str(root / "GgufInference.java")], check=True)
    subprocess.run([str(java_home / "bin/java"), "-Xcheck:jni", "-cp", str(output),
                    "com.simon.harmonichackernews.summary.local.GgufInference", str(library)], check=True)
