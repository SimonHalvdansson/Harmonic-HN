# Prepared comment cache: Pixel 11 Pro XL

Measured 2026-09-05 on the physical Pixel 11 Pro XL, Android 17 / API 37. The raw-JSON
baseline is commit `a7d8f8df780b6c8367992e1895ee0e85406d317c`, including the merged parser
optimizations and cached comment-order restoration.

## Cache read through initial snapshots

Two complete microbenchmark runs, with 100 timing samples and 10 allocation samples per
case pooled across the runs. Values below are medians. Allocations are **object counts**,
not allocated bytes or retained heap.

| Fixture | Raw JSON | Prepared JSON | Prepared ProtoBuf | ProtoBuf time change |
| --- | ---: | ---: | ---: | ---: |
| Medium, 699 comments | 10.54 ms | 5.39 ms | 3.01 ms | −71.5% |
| Large, 3,767 comments | 51.61 ms | 25.38 ms | 15.85 ms | −69.3% |

| Fixture | Raw allocations | Prepared JSON allocations | ProtoBuf allocations | ProtoBuf change |
| --- | ---: | ---: | ---: | ---: |
| Medium | 85,934 | 26,916 | 25,453 | −70.4% |
| Large | 465,249 | 144,225 | 136,370 | −70.7% |

Prepared JSON already saves substantial work. ProtoBuf improves decoding and size further;
the benefit comes from both preserving preparation and changing the storage encoding.
The large raw/ProtoBuf medians were 49.14/13.68 ms in run 1 and 52.70/17.08 ms in run 2.
The improvement held in both runs (3.1–3.6×), despite device-speed drift.

Each operation reads the actual production cache files, decodes, applies current filters
and ordering, and creates fresh Comments and initial immutable snapshots. ProtoBuf/JSON
prepared reads include checksum and structural/version validation. Filesystem pages are
warm; this is not a cold-storage test. Network, Compose rendering and final screen commit
are excluded. Both paths use the same APK and the original raw parser remains unchanged.

## Preparation and write cost

These cases start from an in-memory API response and include preparation, initial snapshots,
raw JSON and compact-summary writes, plus prepared encoding/write where applicable.

| Fixture | Raw only | With prepared JSON | With ProtoBuf | ProtoBuf additional cost |
| --- | ---: | ---: | ---: | ---: |
| Medium | 10.20 ms | 16.18 ms | 13.60 ms | +3.40 ms / +33.3% |
| Large | 44.29 ms | 87.35 ms | 68.41 ms | +24.13 ms / +54.5% |

Write-path allocation counts rise from 86,260 to 94,693 for medium (+9.8%), and from
465,587 to 509,300 for large (+9.4%). Prepared content is computed before user filtering,
so changing a blocked-user preference does not require replacing the cache. Normal network
results reuse their prepared model for presentation and writing. Background downloads also
prepare eagerly, without constructing an unnecessary presentation state just to store it.

| Fixture | Retained raw JSON | ProtoBuf sidecar | Raw + ProtoBuf | Prepared JSON sidecar |
| --- | ---: | ---: | ---: | ---: |
| Medium | 409,022 B | 292,307 B | 701,329 B | 373,012 B |
| Large | 2,019,072 B | 1,392,893 B | 3,411,965 B | 1,829,746 B |

Compact summaries and filesystem overhead are additional. Retaining raw JSON increases
total payload storage by about 69% for large threads. It preserves offline recovery when a
prepared entry is missing, corrupt or incompatible; the next successful open rebuilds it.
Both files use the existing atomic storage graph and removal/eviction lifecycle.

## Cached screen opening

Ten measured reopenings per fixture and APK, using the same corrected setup and the existing
required baseline profile. No new profile was needed for this comparison.

| Fixture | Baseline content ready | Prepared content ready | Change |
| --- | ---: | ---: | ---: |
| Medium | 72.21 ms | 58.29 ms | −19.3% |
| Large | 126.95 ms | 75.89 ms | −40.2% |

These are `CommentsOpen.contentReadyFirstMs` medians: the app's content-ready marker,
not time until the first presented frame or completion of the 450 ms navigation animation.
The benchmark still waits 550 ms and verifies comment rows. It excludes the unmeasured
initial seed/open, and does not wait for network refreshes to measure cached content.

The large baseline spends a median 24.21 ms reading JSON, 41.34 ms parsing it, and 17.53 ms
preparing initial state. The prepared app's read **includes decoding and validation** and
takes 25.59 ms; initial state preparation falls to 5.53 ms. Medians of individual stages
should not be added together to reconstruct the overall median. Medium state preparation
falls from 11.06 ms to 2.02 ms. Screen work, scheduling and existing presentation gates
explain why the full content-ready speedup is smaller than the isolated cache speedup.

## CPU profiling

Separate all-thread Perfetto CPU sampling captured the large raw and ProtoBuf workloads.
The raw stacks include API JSON decoding, `StoryTextProcessor.preprocessHtml` / `linkify`,
and `Comment.expandShortenedAnchorText` during snapshot creation. The prepared path instead
concentrates on ProtoBuf decoding, file reads, cache integrity validation and the remaining
snapshot/state construction. It restores the already-expanded HTML into fresh Comments.

The traces contain 1,822 raw and 1,939 prepared stack samples within the marked workload
windows. Kernel sampling reported 531 and 495 lost records respectively, so these profiles
are directional evidence, not precise CPU percentages. All reported latency and allocation
numbers come from separate AndroidX benchmark runs without this CPU sampler. Early recordings
that missed the workload or failed instrumentation were excluded entirely.

## Reproduction and validation

See [COMMENTS_PARSING.md](COMMENTS_PARSING.md) for commands and benchmark boundaries.
Microbenchmarks use the non-debuggable, unminified test APK with full AOT compilation.
Screen benchmarks use the non-debuggable, unminified application and the existing required
baseline profile. These are physical-device development measurements, not an R8 release-build
or cross-device performance guarantee. The device remained USB powered; thermal status was 0,
but its temperature and speed varied over the session. Individual run medians are retained.

The screen setup now closes the previous Comments screen before seeding. Previously its
existing title/rows could satisfy setup waits before the asynchronous seed write finished,
causing cache invalidation during a measured reopen. Both APKs must use this corrected journey.
The earlier screen results from that racy journey are excluded from the comparison.

Validation: 502 core desktop tests and 124 UI desktop tests pass, including real-fixture parity
for both encodings, blank/deleted parents, blocked descendants, ordering, counts, HTML,
sorting/collapse changes, mutable-object isolation, corrupt/version fallback, eager downloads,
offline migration and cache lifecycle failures. Common metadata compilation, Android
`assembleDebug` and `lintDebug` pass. Scroll restoration and visual preferences remain in their
existing presentation/settings paths rather than being serialized as mutable UI objects.

Raw evidence is saved in the task's `prepared-cache` artifact directory: `micro1/2.json`,
instrumentation logs, APKs and SHA-256 hashes, CPU traces and SQL/CSV summaries, screen JSON
and build/test logs. `baseline-fixed-screen.json` and `prepared-fixed-screen.json` are the
accepted screen results. Median-nearest prepared screen traces were also pulled; AndroidX
removed the earlier baseline screen traces when the next suite started. Their JSON metrics
and complete instrumentation logs were preserved first. The normal Harmonic app's data was
not cleared or replaced, and the original stay-awake setting was restored after testing.
