# Desktop app icons

`harmonic-macos.png` and `harmonic.icns` are deterministic derivatives of the
canonical artwork at
`fastlane/metadata/android/en-US/images/icon.png`. Regenerate both from the
repository root with:

```sh
python3 desktop_app/icons/generate_macos_icon.py \
  fastlane/metadata/android/en-US/images/icon.png \
  desktop_app/icons/harmonic-macos.png \
  --icns desktop_app/icons/harmonic.icns
```

The macOS geometry follows the local Canvas recipe published by
<https://icon.msgbyte.com/>. Its optional glossy highlight is omitted to keep
Harmonic's source artwork and brand colors intact. `harmonic.ico` is the
Windows package icon and continues to use the unmasked canonical artwork.
