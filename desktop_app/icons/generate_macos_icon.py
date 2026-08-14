#!/usr/bin/env python3
"""Generate Harmonic's macOS-specific icon from the canonical store artwork.

The geometry follows https://icon.msgbyte.com/: 8% outer padding, a rounded
square whose radius is 22% of its side, a soft shadow, and a subtle border.
The site's optional glossy overlay is intentionally omitted so the original
brand colors and waveform remain unchanged apart from uniform scaling.
"""

from __future__ import annotations

import argparse
import io
from pathlib import Path
import struct

from PIL import Image, ImageDraw, ImageFilter


CANVAS_SIZE = 1024
SUPERSAMPLING = 4
ICNS_VARIANTS = (
    (b"icp4", 16),
    (b"ic11", 32),
    (b"icp5", 32),
    (b"ic12", 64),
    (b"ic07", 128),
    (b"ic13", 256),
    (b"ic08", 256),
    (b"ic14", 512),
    (b"ic09", 512),
    (b"ic10", 1024),
)


def generate(source_path: Path, output_path: Path) -> None:
    scale = SUPERSAMPLING
    canvas_size = CANVAS_SIZE * scale
    margin = round(0.08 * CANVAS_SIZE) * scale
    artwork_size = canvas_size - (2 * margin)
    corner_radius = round(0.22 * (artwork_size / scale)) * scale

    source = Image.open(source_path).convert("RGBA")
    artwork = source.resize((artwork_size, artwork_size), Image.Resampling.LANCZOS)

    shape_mask = Image.new("L", (canvas_size, canvas_size), 0)
    ImageDraw.Draw(shape_mask).rounded_rectangle(
        (margin, margin, margin + artwork_size, margin + artwork_size),
        radius=corner_radius,
        fill=255,
    )

    shadow_mask = Image.new("L", (canvas_size, canvas_size), 0)
    shadow_offset_y = round(0.02 * CANVAS_SIZE) * scale
    ImageDraw.Draw(shadow_mask).rounded_rectangle(
        (
            margin,
            margin + shadow_offset_y,
            margin + artwork_size,
            margin + artwork_size + shadow_offset_y,
        ),
        radius=corner_radius,
        fill=round(255 * 0.18),
    )
    shadow_mask = shadow_mask.filter(
        ImageFilter.GaussianBlur(radius=0.06 * CANVAS_SIZE * scale),
    )

    output = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    shadow_layer = Image.new("RGBA", output.size, (15, 23, 42, 255))
    shadow_layer.putalpha(shadow_mask)
    output.alpha_composite(shadow_layer)

    artwork_layer = Image.new("RGBA", output.size, (255, 255, 255, 0))
    artwork_layer.paste(artwork, (margin, margin), artwork)
    artwork_layer.putalpha(shape_mask)
    output.alpha_composite(artwork_layer)

    border_width = max(round(0.012 * CANVAS_SIZE * scale), scale)
    border = Image.new("RGBA", output.size, (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle(
        (margin, margin, margin + artwork_size, margin + artwork_size),
        radius=corner_radius,
        outline=(148, 163, 184, round(255 * 0.25)),
        width=border_width,
    )
    output.alpha_composite(border)

    output = output.resize((CANVAS_SIZE, CANVAS_SIZE), Image.Resampling.LANCZOS)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output.save(output_path, optimize=True)


def generate_icns(png_path: Path, output_path: Path) -> None:
    source = Image.open(png_path).convert("RGBA")
    chunks: list[bytes] = []
    for os_type, size in ICNS_VARIANTS:
        variant = source.resize((size, size), Image.Resampling.LANCZOS)
        encoded = io.BytesIO()
        variant.save(encoded, format="PNG", optimize=True)
        payload = encoded.getvalue()
        chunks.append(os_type + struct.pack(">I", len(payload) + 8) + payload)
    body = b"".join(chunks)
    output_path.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--icns", type=Path)
    args = parser.parse_args()
    generate(args.source, args.output)
    if args.icns is not None:
        generate_icns(args.output, args.icns)


if __name__ == "__main__":
    main()
