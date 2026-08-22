# Desktop app icons

`harmonic-macos.png`, `harmonic-windows.png`, `harmonic.icns`, and
`harmonic.ico` are deterministic derivatives of the canonical artwork at
`fastlane/metadata/android/en-US/images/icon.png`. Regenerate all of them from the
repository root with:

```sh
python3 desktop_app/icons/generate_macos_icon.py \
  fastlane/metadata/android/en-US/images/icon.png \
  desktop_app/icons/harmonic-macos.png \
  --icns desktop_app/icons/harmonic.icns \
  --windows desktop_app/icons/harmonic-windows.png \
  --ico desktop_app/icons/harmonic.ico
```

The macOS geometry follows the local Canvas recipe published by
<https://icon.msgbyte.com/>. Its optional glossy highlight is omitted to keep
Harmonic's source artwork and brand colors intact. The Windows version uses
tighter outer padding so it remains legible at taskbar sizes while retaining
transparent, rounded corners.
