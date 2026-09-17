# Harmonic splash motion study

Three 600 ms construction animations, with **Gather** selected by default:

| Direction | Construction |
| --- | --- |
| Ink flow | The upper stroke draws left to right; the lower follows 75 ms behind. |
| Counterpoint | Two strokes draw from opposite ends and connect. |
| Gather | Two central fragments draw outward while turning and moving together. |

Open `index.html` directly, or serve the folder from the repository root:

```sh
python -m http.server 8765 --bind 127.0.0.1 --directory docs/design/splash-animation
```

Visit <http://127.0.0.1:8765>. No network assets or package installs are required.
Select a direction to replay it, or enable **Keep playhead when switching** to
compare a fixed moment. All options support scrubbing, native arrow-key stepping,
frame selection, slow motion, pause/resume, looping, light/dark backgrounds,
safe-area guides, and early-dismissal simulation. Initial load never autoplays;
reduced-motion preferences also disable automatic playback on selection.

## Continuous-stroke revision

The previous approach clipped polygonal drawing sweeps to a raster-derived
silhouette, then faded in the complete trace at the end. This produced clipped
notches at the joins and a late appearance/thickening of the upper wave.

This revision removes both the silhouette clips and the final trace overlay.
Two open cubic paths, each consisting of three cubic Bezier segments, draw
throughout and remain the final icon. They have a constant 44-unit stroke width,
round caps, and round joins. Short initial opacity ramps introduce the first
tips; opacity remains 1 thereafter. There is no terminal opacity handoff.

Partial curves use de Casteljau subdivision with an arc-length lookup. Unused
segments are degenerate at the visible endpoints, keeping the same path command
topology for Android morphing without stray lines. Browser and Android consume
31 matching poses at 20 ms intervals with the same interpolation.

Gather aligns the fragments by 420 ms and finishes drawing by 460 ms.
The entire pose, including path coordinates, width, and transforms, is identical at 480 and 520 ms.
The timeline then remains still through 600 ms. This is animation timing, not a
minimum splash-screen lifetime.

## Artwork

The original PNG remains unchanged at
`app/src/main/res/drawable-nodpi/ic_launcher_foreground_1024.png`. The new curves
follow its proportions, colors, and overall wave shape, intentionally favoring
smooth motion over a pixel-exact contour match. The comparison tab now compares
the actual final strokes with the PNG, including intentional geometry changes.
The old 99% trace-fidelity figure does not apply to these new strokes.

Foreground is `#341000`; background is `#FFDBC9`, sampled from the production
background PNG. `icon-geometry.json` retains the old trace as a reference, along
with source and color metadata; its contour is not used in the animation.

## Regenerate

```sh
node docs/design/splash-animation/export.cjs
```

This needs Node.js and no additional packages. It creates:

- `preview-data.js`: metadata and embedded reference PNG for offline preview.
- `export/harmonic-foreground.svg`: the final smooth, transparent stroke artwork.
- `export/ic_harmonic_splash_ink.xml`: Ink flow AVD.
- `export/ic_harmonic_splash_counterpoint.xml`: Counterpoint AVD.
- `export/ic_harmonic_splash_gather.xml`: Gather AVD.
- `export/ic_harmonic_splash.xml`: default alias of Gather.

The download link follows the selected animation. `trace_icon.py` (Pillow and
NumPy) can regenerate the archived reference trace, but is unnecessary for
editing the current motion or generating the outputs.

## Integration and verification

Gather is installed in the Android app for Android 12 and later. All four base
themes in `app/src/main/res/values-v31/themes.xml` select
`@drawable/ic_harmonic_splash_gather` as `android:windowSplashScreenAnimatedIcon`.
The generated drawable lives in `app/src/main/res/drawable-v31/`; its 600 ms
duration is generated in `app/src/main/res/values-v31/splash_animation.xml` and
referenced by `android:windowSplashScreenAnimationDuration`.

To iterate, edit `motion.js`, regenerate the preview, and review it in the browser.
Once satisfied, explicitly sync Gather and its duration into the app:

```sh
node docs/design/splash-animation/export.cjs --android
```

Plain export leaves app resources untouched. Keep this entire study directory,
including all three options, the reference PNG data, and the scrubber, for future
iterations. The app resource is generated; do not edit it by hand.

The integration changes only API 31 splash resources and themes. Startup logic,
launcher icons, theme backgrounds, and older Android versions retain their
existing behavior. The duration is animation metadata, not a minimum display
time: do not delay first draw, add a keep-on-screen condition, or wait for
animation completion. Let the system dismiss as soon as the app is ready.
Browser dismissal controls demonstrate the intended behavior; they do not
measure or guarantee device launch time.

The AVD has a 1024-unit viewport, 288 dp drawable, and centered 192 dp peach
circle. The browser approximates that with 192 CSS pixels. Platform launcher
masks and enter/exit transitions are not simulated.

Verified in Edge through Playwright at desktop and 390 px mobile widths:
all three options retain two strokes without clipping/overlay layers, keep
constant width and late opacity, reach the same final stroke geometry, and
support early dismissal. Inspected intermediate and late Gather frames,
including the formerly problematic 480-520 ms interval. No page errors were
observed. All three AVDs compiled and linked using AAPT2 36 / Android API 36,
minimum API 31. No emulator or device was used; native rendering is unverified.
Android integration verified on 2026-09-05 with `gradlew.bat assembleDebug lintDebug`
using Android Studio's bundled JBR; both passed. Device verification is left to
the app owner. Run build and lint again after syncing a revised animation into
the app.

