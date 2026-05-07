#!/usr/bin/env python3
"""Regenerate the empty 5x5x5 GameTest structure NBT used by every dynamic recipe test.

Output path: src/main/resources/data/recipe_test/structure/empty5.nbt

The file is a Minecraft StructureTemplate with size [5,5,5], a single-entry palette of
minecraft:air, and no actual blocks placed. Every dynamic recipe test loads this template;
TestStructures.placeMachine then spawns the machine block at the centre.

The file is checked into the repo so production runs never require Python; this script exists
so the bytes are reproducible. Run from the worktree root:

    python3 tools/generate_empty_structure.py

DataVersion 3953 corresponds to Minecraft 1.21.1. Bump it alongside any future MC bump so the
data fixers don't try to migrate the structure on load.
"""

from __future__ import annotations

import gzip
import io
import struct
import sys
from pathlib import Path

DATA_VERSION = 3953  # 1.21.1
SIZE = (5, 5, 5)
TARGET = Path("src/main/resources/data/recipe_test/structure/empty5.nbt")


def write_short_string(buf: io.BytesIO, value: str) -> None:
    encoded = value.encode("utf-8")
    buf.write(struct.pack(">H", len(encoded)))
    buf.write(encoded)


def write_int_list(buf: io.BytesIO, name: str, ints: tuple[int, ...]) -> None:
    buf.write(b"\x09")  # TAG_List
    write_short_string(buf, name)
    buf.write(b"\x03")  # element type: TAG_Int
    buf.write(struct.pack(">i", len(ints)))
    for value in ints:
        buf.write(struct.pack(">i", value))


def write_empty_compound_list(buf: io.BytesIO, name: str) -> None:
    buf.write(b"\x09")  # TAG_List
    write_short_string(buf, name)
    buf.write(b"\x0A")  # element type: TAG_Compound
    buf.write(struct.pack(">i", 0))


def write_int(buf: io.BytesIO, name: str, value: int) -> None:
    buf.write(b"\x03")
    write_short_string(buf, name)
    buf.write(struct.pack(">i", value))


def build_palette_compound() -> bytes:
    inner = io.BytesIO()
    # TAG_String "Name" = "minecraft:air"
    inner.write(b"\x08")
    write_short_string(inner, "Name")
    write_short_string(inner, "minecraft:air")
    return inner.getvalue()


def build_nbt() -> bytes:
    buf = io.BytesIO()
    # Root compound, no name
    buf.write(b"\x0A")
    write_short_string(buf, "")

    write_int_list(buf, "size", SIZE)
    write_empty_compound_list(buf, "blocks")

    # palette: list with one compound entry
    palette_compound = build_palette_compound()
    buf.write(b"\x09")
    write_short_string(buf, "palette")
    buf.write(b"\x0A")
    buf.write(struct.pack(">i", 1))
    buf.write(palette_compound)
    buf.write(b"\x00")  # end of compound

    write_empty_compound_list(buf, "entities")
    write_int(buf, "DataVersion", DATA_VERSION)

    buf.write(b"\x00")  # end of root compound
    return buf.getvalue()


def main() -> int:
    raw = build_nbt()
    compressed = gzip.compress(raw, mtime=0)
    target = TARGET
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(compressed)
    print(f"wrote {target} ({len(compressed)} bytes, uncompressed {len(raw)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
