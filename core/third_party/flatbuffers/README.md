# FlatBuffers Kotlin Multiplatform runtime

The seven runtime sources in this directory are from Google's FlatBuffers
**v25.2.10**, commit `1c514626e83c20fffa8557e75641848e1e15cd5e`, under the
Apache 2.0 license in [LICENSE](LICENSE). Copyright headers are retained.

Upstream source:
https://github.com/google/flatbuffers/tree/1c514626e83c20fffa8557e75641848e1e15cd5e/kotlin/flatbuffers-kotlin/src

The official Maven publication supplies the Java runtime, but not this KMP
runtime. Pinning the required source subset keeps normal builds offline-capable
and avoids JVM APIs in the shared cache codec. FlexBuffers and JSON conversion
sources are not needed and are not included.

- `commonMain/`: upstream `commonMain/kotlin/com/google/flatbuffers/kotlin/`
  (`Buffers.kt`, `ByteArray.kt`, `FlatBufferBuilder.kt`, `Flatbuffers.kt`, `Utf8.kt`).
- `jvmMain/ByteArray.kt`: upstream JVM implementation, used by Android and desktop.
- `nativeMain/ByteArray.kt`: upstream Native implementation, used by iOS ARM64
  and the ARM64 simulator (both little-endian).

Local adaptation: `nativeMain/ByteArray.kt` adds a file-level opt-in to
`kotlin.experimental.ExperimentalNativeApi`, required by the project's Kotlin
compiler for Native byte-array primitives. Runtime behavior is otherwise unchanged.

The shared license catalog credits FlatBuffers. Its bundled logo is rendered from
`docs/source/assets/flatbuffers_logo.svg` at the same upstream tag.

The runtime is compiled into `core`; generated types remain an implementation
detail of the prepared cache. The application's schema is
`core/src/commonMain/flatbuffers/prepared_comments.fbs`. Regenerate bindings with:

```sh
python3 tools/generate-prepared-comments --flatc /path/to/flatc
python3 tools/generate-prepared-comments --flatc /path/to/flatc --check
```

The script requires **flatc 25.2.10** and uses `--kotlin-kmp`. Generated sources
are checked in, so normal Gradle builds do not require `flatc`. Upgrade the
runtime and compiler together, rerun the common codec/compatibility tests on
JVM and iOS, and remeasure the prepared-cache benchmarks before changing this pin.
When the decoder starts reading additional schema fields, extend
`PreparedCommentFlatBufferVerifier` alongside the generated bindings.
