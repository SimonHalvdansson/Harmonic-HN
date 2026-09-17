# iOS host integration and comments profiling

WebKit allocation is lazy: comments-only navigation does not construct a native
browser, and configured background preloading waits for the opening transition.
Initial comments loading waits for the first draw, without waiting an additional
frame. These are separate controls over browser creation and shared content loading.

The host connects browser history and navigation, link-preview sessions, archive
redirects, rendered DOM text for summaries, reading-position restoration, lifecycle
updates, and system/scheduled appearance changes. Reading restoration includes the
comment offset and collapsed comments; an explicit comment target takes priority.
Browser-first visits defer visual restoration until the comments sheet expands.

WebKit renderer tests run inside the XCTest application host. A standalone
Kotlin/Native test executable lacks UIApplication and cannot reliably start WebKit's
renderer; its tests cover allocation and policy instead.

## Profile comments opening

Run `HarmonicIosUITests/testProfileCommentsOpening` through Xcode on a dedicated
simulator. UI tests reset preferences, so avoid a simulator containing personal state.
The test uses the existing Settings → Debug → Link post fixture and counterbalances
`immediate` / `after-draw` / `after-draw` / `immediate` modes across launches.

The profiler activates only in Debug builds with `HARMONIC_PROFILE_COMMENTS` set.
It writes `comments-profile-*.json` into the application's Library/Caches directory;
ordinary launches do not collect timings. Copy results outside the repository and
stop other simulator work and builds while collecting samples.

Compare first draw, initial load request, cached-content readiness, and frame-clock
intervals using the same fixture and warmup policy. Measurements start when the
comments host begins composition and exclude earlier tap dispatch and navigation.
Compose frame-clock intervals are a scheduling proxy, not a display FPS measurement;
Debug simulator timings do not establish release-device performance.
