"""Smoke-test the staged desktop library on both cache hits and fresh native builds."""

import ctypes
from pathlib import Path
import sys


def main():
    names = {
        "darwin": "libharmonic-local-ai.dylib",
        "win32": "harmonic-local-ai.dll",
        "linux": "libharmonic-local-ai.so",
    }
    path = Path(sys.argv[1], names[sys.platform]).resolve(strict=True)
    library = ctypes.CDLL(str(path))
    library.harmonic_llama_backend_initialize.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    library.harmonic_llama_backend_initialize.restype = None
    library.harmonic_llama_create.argtypes = []
    library.harmonic_llama_create.restype = ctypes.c_void_p
    library.harmonic_llama_destroy.argtypes = [ctypes.c_void_p]
    library.harmonic_llama_destroy.restype = None
    library.harmonic_llama_close.argtypes = [ctypes.c_void_p]
    library.harmonic_llama_close.restype = None

    library.harmonic_llama_backend_initialize(None, None)
    engine = library.harmonic_llama_create()
    if not engine:
        raise RuntimeError("Native inference session allocation failed")
    try:
        library.harmonic_llama_close(engine)
    finally:
        library.harmonic_llama_destroy(engine)
    print(f"Native library load and session lifecycle passed: {path}")


if __name__ == "__main__":
    main()
