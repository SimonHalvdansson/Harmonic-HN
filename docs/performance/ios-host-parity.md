**iOS host parity verification — 11 September 2026**

The iOS host now connects the shared behavior that was missing from its browser, comments, lifecycle, and appearance integration. WebKit allocation remains lazy: comments-only navigation does not construct a native browser, and configured background preloading waits until the opening transition finishes.

| Area | Result | Verification |
| --- | --- | --- |
| Browser Back | Far-left bottom-bar action traverses WebKit history. At the beginning of history it returns to stories on iPhone, or expands comments beside stories in two-pane layouts. The Android-specific “Go back to comments” preference is hidden on iOS. | Real WebKit history traversal; phone and tablet UI navigation tests. |
| Reading state | Reopening restores the comment and pixel offset, plus collapsed comments. An explicit comment target takes priority. Browser-first visits defer visual restoration until the comments sheet expands. Leaving before loading finishes preserves the previous state. | Three binding regression tests; phone close/reopen test with a settled scroll anchor. |
| Link previews | The iOS comments host owns a StoryLinkPreviewSession for network previews and browser DOM extraction. ID-only routes create the browser holder when their URL arrives. | GitHub fixture renders repository metadata and then opens its article. |
| Archive redirects | Browser loads and eligible main-frame navigations apply the shared archive-domain policy. | Native adapter test checks the resolved request URL. |
| Saved website copies | iOS does not supply an article snapshot store. Cache requests disable article snapshots, and the cache dialog omits website downloads. Comment caching remains available. | Capability/storage regression test; phone and tablet cache dialogs. |
| Summary page text | Summaries can use rendered DOM text, including dynamic content, with the shared timeout and size limit. An unrequested browser stays unallocated unless the summary explicitly retries with on-demand loading. | Actual WebKit renderer checks dynamic text, quotes/Unicode, and the 256 KiB limit; native no-allocation test. |
| Saved-comment HTML | iOS saved-comment cards use the shared annotated HTML renderer and link routing. | Shared renderer coverage and host integration inspection. |
| Lifecycle | UIKit foreground/resume events reach the stories and comments stores. Hidden/background story feeds stop preloading. Returning from Settings reconciles preferences. Tap to update has an explicit accessibility label on the FAB. | Phone Settings return, background/resume, and update-action UI test. |
| Appearance | The window observes the inherited system style. Appearance also refreshes on activation, significant time changes, and minute boundaries while active. | Live iPad light-to-dark change; scheduled nighttime transition at 13:57 while the app remained foreground. |
| Build version | Version code is derived from the integer CFBundleVersion used by the Xcode project, alongside its existing build-number string. | Xcode build and application launch; bundle/metadata integration inspection. |
| WebKit delegates | Navigation and UI delegates handle new-window links in the existing view, user-initiated external schemes, JS dialogs, load failures, unsupported responses, and bounded process recovery. Disposal detaches delegates and resolves pending dialogs. | Renderer tests for new windows and JS responses; failure/process-recovery callback and cleanup tests on phone and tablet. |
| Comments opening | Initial loading waits for the first draw. It does not wait for an additional frame. | Controlled simulator experiments below. |

Verification used dedicated iPhone 17 Pro and iPad Pro 13-inch M5 simulators running iOS 26.5, with a Debug build. The automated Kotlin suites passed: **556 core JVM tests, 152 UI JVM tests, and 157 UI iOS simulator tests**. Common metadata, iOS adapters, and the desktop host compiled. Android `assembleDebug` and `lintDebug` passed. No Android device was used.

The WebKit DOM tests run inside the XCTest application host. A standalone Kotlin/Native test executable has no UIApplication and cannot reliably start WebKit's renderer; its tests cover allocation and policy instead. Apple Intelligence generation was not exercised on the simulator: the browser text supplied to the summary pipeline was verified directly.

Representative evidence: [GitHub preview](../reports/ios-parity/github-preview.png), [reading before closing](../reports/ios-parity/reading-before.png), [restored reading position](../reports/ios-parity/reading-restored.png), and [iPad after browser Back](../reports/ios-parity/ipad-browser-back.png). Appearance evidence: [system light](../reports/ios-parity/ipad-system-light.png), [system dark](../reports/ios-parity/ipad-system-dark.png), [before scheduled nighttime](../reports/ios-parity/ipad-before-nighttime.png), and [after the boundary](../reports/ios-parity/ipad-after-nighttime.png).

**Opening experiment: an additional frame**

The first experiment opened the same cached thread (HN item 49656225) 32 times across four app launches. Each launch contained eight openings; the first two openings in each launch were excluded from the warm comparison. This leaves 12 samples per mode. The raw result calls the extra-frame mode `deferred`.

| Warm median, milliseconds | Immediate LaunchedEffect | First draw plus another frame |
| --- | ---: | ---: |
| First draw from comments composition | 2.56 | 2.57 |
| Cached thread ready | 7.39 | 128.01 |
| Worst Compose frame-clock interval per opening | 81.15 | 72.11 |

The additional frame delayed cached content by about 120.6 ms without improving first draw. The modest difference in the worst frame-clock interval does not justify that delay. The chosen behavior makes first-draw ordering explicit and starts loading immediately afterward. [Raw extra-frame results](ios-comments-next-frame.json).

**Final comparison: explicit first-draw gate**

The finished implementation was checked using the existing Debug → Link post fixture (HN item 47938725). This fixture currently opens a comment thread. The same 32-opening, four-launch procedure produced 12 warm samples per mode, after excluding the first two openings of each launch.

| Warm median, milliseconds | Immediate LaunchedEffect | Explicit first-draw gate |
| --- | ---: | ---: |
| First draw from comments composition | 3.60 | 3.54 |
| Initial load requested | 5.88 | 5.89 |
| Cached thread ready | 9.14 | 9.10 |
| Worst Compose frame-clock interval per opening | 116.67 | 116.67 |

Every sample in both modes began loading after its first draw. The explicit gate preserves this ordering without a measurable delay in this run. It does not establish an improvement in overall transition smoothness; the native browser deferral separately avoids unnecessary WebKit startup. The earlier and final experiments used different threads, so their absolute timings should not be compared across tables. [Raw final results](ios-comments-first-draw.json).

These are Debug simulator measurements, starting when the comments host begins composition. They exclude tap dispatch and navigation work before that point. Compose frame-clock intervals are a scheduling proxy, not a display FPS measurement, and these numbers should not be treated as release-device performance.

To reproduce the current comparison, run `HarmonicIosUITests/testProfileCommentsOpening` through Xcode on a dedicated simulator. It uses the existing Settings → Debug → Link post fixture, counterbalances `immediate` / `after-draw` / `after-draw` / `immediate`, and writes `comments-profile-*.json` into the application's Library/Caches directory. The profiler activates only in Debug builds with `HARMONIC_PROFILE_COMMENTS` set; ordinary app launches do not collect timings or write profiles. Stop other simulator work and builds while collecting samples. UI tests reset preferences, so use a dedicated test simulator.
