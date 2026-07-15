#!/usr/bin/env python3
"""Generate the original pixel textures used by the astral strengthening table."""

from __future__ import annotations

import binascii
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BLOCK_DIR = ROOT / "src/main/resources/assets/magic/textures/block"
ENTITY_DIR = ROOT / "src/main/resources/assets/magic/textures/entity"


class Canvas:
    def __init__(self, width: int, height: int, color: tuple[int, int, int, int]) -> None:
        self.width = width
        self.height = height
        self.pixels = [[color for _ in range(width)] for _ in range(height)]

    def set(self, x: int, y: int, color: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y][x] = color

    def fill(self, x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.pixels[y][x] = color

    def line(self, x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
        dx = abs(x1 - x0)
        sx = 1 if x0 < x1 else -1
        dy = -abs(y1 - y0)
        sy = 1 if y0 < y1 else -1
        error = dx + dy
        while True:
            self.set(x0, y0, color)
            if x0 == x1 and y0 == y1:
                return
            doubled = 2 * error
            if doubled >= dy:
                error += dy
                x0 += sx
            if doubled <= dx:
                error += dx
                y0 += sy

    def write(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytearray()
        for row in self.pixels:
            raw.append(0)
            for pixel in row:
                raw.extend(pixel)

        def chunk(kind: bytes, payload: bytes) -> bytes:
            return (
                struct.pack(">I", len(payload))
                + kind
                + payload
                + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)
            )

        png = bytearray(b"\x89PNG\r\n\x1a\n")
        png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0)))
        png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), level=9)))
        png.extend(chunk(b"IEND", b""))
        path.write_bytes(png)


def make_base() -> Canvas:
    canvas = Canvas(32, 32, (15, 20, 29, 255))
    for y in range(32):
        for x in range(32):
            tile = ((x // 4) + (y // 4)) & 1
            grain = (x * 17 + y * 11 + x * y * 3) % 9
            value = 17 + tile * 4 + grain
            canvas.set(x, y, (value, value + 5, value + 13, 255))
    for edge in (0, 7, 15, 23, 31):
        canvas.line(0, edge, 31, edge, (8, 12, 19, 255))
        canvas.line(edge, 0, edge, 31, (8, 12, 19, 255))
    canvas.line(1, 1, 30, 1, (49, 59, 72, 255))
    canvas.line(1, 1, 1, 30, (39, 49, 62, 255))
    return canvas


def make_trim() -> Canvas:
    canvas = Canvas(32, 32, (19, 25, 34, 255))
    silver = (114, 133, 150, 255)
    highlight = (203, 221, 228, 255)
    gold = (204, 148, 58, 255)
    shadow = (41, 49, 59, 255)
    for y in range(32):
        stripe = y % 8
        tone = 35 + (3 if stripe in (2, 3) else 0)
        canvas.fill(0, y, 32, y + 1, (tone, tone + 8, tone + 15, 255))
    for offset in (0, 16):
        canvas.fill(offset, 0, offset + 2, 32, shadow)
        canvas.fill(offset + 2, 0, offset + 3, 32, silver)
        canvas.fill(offset + 14, 0, offset + 15, 32, highlight)
        canvas.fill(offset + 15, 0, offset + 16, 32, shadow)
    for y in (6, 14, 22, 30):
        canvas.line(0, y, 31, y, gold)
    for x, y in ((7, 3), (23, 3), (7, 19), (23, 19)):
        canvas.fill(x - 1, y - 1, x + 2, y + 2, highlight)
        canvas.set(x, y, (90, 235, 255, 255))
    return canvas


def make_runes() -> Canvas:
    canvas = Canvas(32, 32, (8, 21, 32, 255))
    deep = (11, 34, 48, 255)
    glow = (70, 211, 255, 255)
    core = (202, 248, 255, 255)
    gold = (219, 164, 68, 255)
    for y in range(32):
        for x in range(32):
            if (x + y) % 7 == 0:
                canvas.set(x, y, deep)
    for center_x, center_y in ((8, 8), (24, 8), (8, 24), (24, 24)):
        canvas.line(center_x - 5, center_y, center_x, center_y - 5, glow)
        canvas.line(center_x, center_y - 5, center_x + 5, center_y, glow)
        canvas.line(center_x + 5, center_y, center_x, center_y + 5, glow)
        canvas.line(center_x, center_y + 5, center_x - 5, center_y, glow)
        canvas.fill(center_x - 1, center_y - 1, center_x + 2, center_y + 2, core)
    canvas.line(0, 15, 31, 15, gold)
    canvas.line(15, 0, 15, 31, gold)
    return canvas


def make_crystal() -> Canvas:
    canvas = Canvas(32, 32, (5, 13, 24, 220))
    for y in range(32):
        for x in range(32):
            distance = abs(x - 15.5) / 15.5
            red = int(28 + (1.0 - distance) * 38)
            green = int(105 + (1.0 - distance) * 88 + (31 - y) * 0.8)
            blue = int(158 + (1.0 - distance) * 82)
            alpha = int(150 + (1.0 - distance) * 80)
            canvas.set(x, y, (red, min(255, green), min(255, blue), alpha))
    canvas.line(4, 31, 15, 0, (225, 253, 255, 245))
    canvas.line(15, 0, 27, 31, (91, 218, 255, 235))
    canvas.line(15, 0, 15, 31, (179, 247, 255, 245))
    canvas.line(8, 23, 23, 23, (50, 174, 230, 225))
    return canvas


def make_ring() -> Canvas:
    canvas = Canvas(64, 16, (10, 25, 38, 128))
    edge = (232, 184, 79, 230)
    glow = (73, 213, 255, 238)
    core = (213, 252, 255, 255)
    canvas.fill(0, 0, 64, 2, edge)
    canvas.fill(0, 14, 64, 16, edge)
    canvas.fill(0, 3, 64, 13, (16, 69, 91, 190))
    for start in range(0, 64, 8):
        canvas.line(start, 8, start + 3, 4, glow)
        canvas.line(start + 3, 4, start + 6, 8, glow)
        canvas.line(start + 6, 8, start + 3, 12, glow)
        canvas.line(start + 3, 12, start, 8, glow)
        canvas.set(start + 3, 8, core)
    return canvas


def make_energy() -> Canvas:
    canvas = Canvas(16, 16, (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            distance = (dx * dx + dy * dy) ** 0.5
            if distance <= 7.5:
                factor = 1.0 - distance / 7.5
                canvas.set(
                    x,
                    y,
                    (120 + int(120 * factor), 211 + int(44 * factor), 255, int(220 * factor)),
                )
    canvas.fill(7, 2, 9, 14, (224, 252, 255, 245))
    canvas.fill(2, 7, 14, 9, (224, 252, 255, 245))
    return canvas


def main() -> None:
    textures = {
        BLOCK_DIR / "strengthening_table_base.png": make_base(),
        BLOCK_DIR / "strengthening_table_trim.png": make_trim(),
        BLOCK_DIR / "strengthening_table_runes.png": make_runes(),
        BLOCK_DIR / "strengthening_table_crystal.png": make_crystal(),
        ENTITY_DIR / "strengthening_table_ring.png": make_ring(),
        ENTITY_DIR / "strengthening_table_energy.png": make_energy(),
    }
    for path, canvas in textures.items():
        canvas.write(path)
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
