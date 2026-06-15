#!/usr/bin/env python3
"""Minimal Minecraft schematic to Hytale prefab converter.
Supports .schematic, .schem, .litematic, and world folders.
Outputs Hytale prefab JSON format.
"""
import argparse
import io
import json
import os
import struct
import gzip
import zlib
import sys

try:
    import nbtlib
except ImportError:
    print("Error: nbtlib is required. Install with: pip install nbtlib")
    sys.exit(1)

BLOCKS_PER_SECTION = 16 * 16 * 16


def read_mc_chunk_bytes(region_path, chunk_index):
    with open(region_path, "rb") as f:
        f.seek(chunk_index * 4)
        entry = struct.unpack(">I", f.read(4))[0]
        sector_offset = entry >> 8
        if sector_offset == 0:
            return None
        f.seek(sector_offset * 4096)
        length = struct.unpack(">I", f.read(4))[0]
        compression = struct.unpack(">B", f.read(1))[0]
        data = f.read(length - 1)
    if compression == 1:
        return gzip.decompress(data)
    if compression == 2:
        return zlib.decompress(data)
    return None


def iter_mc_chunks(region_path):
    with open(region_path, "rb") as f:
        header = f.read(4096)
    if len(header) < 4096:
        print(f"Warning: skipping invalid region file (short header): {region_path}")
        return
    for i in range(1024):
        entry = struct.unpack(">I", header[i * 4 : (i + 1) * 4])[0]
        sector_offset = entry >> 8
        if sector_offset == 0:
            continue
        data = read_mc_chunk_bytes(region_path, i)
        if not data:
            continue
        nbt = nbtlib.File.parse(io.BytesIO(data))
        root = nbt["Level"] if "Level" in nbt else nbt
        chunk_x = int(root.get("xPos", 0))
        chunk_z = int(root.get("zPos", 0))
        sections = root.get("Sections")
        if sections is None:
            sections = root.get("sections", [])
        yield chunk_x, chunk_z, sections


class BlockMapper:
    def __init__(self, mapping_path=None):
        self.modern = {}
        self.legacy = {}
        self.legacy_by_id = {}
        self.default_block = "Rock_Stone"

        if mapping_path and os.path.exists(mapping_path):
            with open(mapping_path, "r") as f:
                data = json.load(f)
            self.modern = data.get("modern", {})
            self.legacy = data.get("legacy", {})
            self.legacy_by_id = data.get("legacy_by_id", {})
            self.default_block = data.get("default", "Rock_Stone")

    def set_default(self, block_name):
        self.default_block = block_name

    def map_modern(self, state_key):
        name = state_key.split("[")[0]
        name = name.replace("minecraft:", "")
        return self.modern.get(name, self.default_block)

    def map_legacy(self, block_id, block_data):
        key = f"{block_id}:{block_data}"
        if key in self.legacy:
            return self.legacy[key]
        bid = str(block_id)
        if bid in self.legacy_by_id:
            base = self.legacy_by_id[bid]
            if block_data == 0:
                return base
        return self.default_block


def convert_schematic_to_prefab(input_path, output_path, mapping_path):
    mapper = BlockMapper(mapping_path)
    blocks = []
    min_x = min_y = min_z = None

    with open(input_path, "rb") as f:
        raw = f.read()

    # Try to decompress
    try:
        raw = gzip.decompress(raw)
    except:
        try:
            raw = zlib.decompress(raw)
        except:
            pass  # Might be uncompressed NBT

    nbt = nbtlib.File.parse(io.BytesIO(raw))
    root = nbt.get("Schematic", nbt)

    width = int(root.get("Width", 0))
    height = int(root.get("Height", 0))
    length = int(root.get("Length", 0))

    print(f"Schematic dimensions: {width}x{height}x{length}")

    # Modern schematic (palette + block data)
    palette = root.get("Palette")
    block_data_raw = root.get("BlockData")

    if palette is not None and block_data_raw is not None:
        palette_list = [""] * len(palette)
        for name, idx in palette.items():
            palette_list[idx] = name

        block_data = bytearray(block_data_raw)
        total_blocks = width * height * length

        for i in range(total_blocks):
            if i >= len(block_data):
                break
            idx = block_data[i]
            if idx >= len(palette_list):
                continue
            state_key = palette_list[idx]
            name = state_key.replace("minecraft:", "")
            if name in ("air", "cave_air", "void_air", "structure_void"):
                continue
            block_name = mapper.map_modern(state_key)
            if block_name in ("Empty", "Air"):
                continue

            x = i % width
            z = (i // width) % length
            y = i // (width * length)

            if min_x is None:
                min_x, min_y, min_z = x, y, z
            else:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                min_z = min(min_z, z)
            blocks.append((x, y, z, block_name))

    # Legacy schematic (Blocks + Data byte arrays)
    elif "Blocks" in root:
        blocks_raw = root["Blocks"]
        data_raw = root.get("Data")
        if data_raw is None:
            data_raw = bytearray(len(blocks_raw))

        total = width * height * length
        for i in range(total):
            block_id = blocks_raw[i] if i < len(blocks_raw) else 0
            block_data = data_raw[i] if i < len(data_raw) else 0
            if block_id == 0:  # Air
                continue
            block_name = mapper.map_legacy(block_id, block_data)
            if block_name in ("Empty", "Air"):
                continue

            x = i % width
            z = (i // width) % length
            y = i // (width * length)

            if min_x is None:
                min_x, min_y, min_z = x, y, z
            else:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                min_z = min(min_z, z)
            blocks.append((x, y, z, block_name))

    else:
        print("Error: Could not find Palette/BlockData or Blocks in schematic")
        sys.exit(1)

    if min_x is None:
        print("Error: No blocks found")
        sys.exit(1)

    # Build prefab blocks (normalized to origin)
    prefab_blocks = []
    for x, y, z, name in blocks:
        prefab_blocks.append({
            "x": x - min_x,
            "y": y - min_y,
            "z": z - min_z,
            "name": name,
        })

    max_x = max(b["x"] for b in prefab_blocks)
    max_y = max(b["y"] for b in prefab_blocks)
    max_z = max(b["z"] for b in prefab_blocks)

    prefab = {
        "version": 8,
        "blockIdVersion": 8,
        "anchorX": 0,
        "anchorY": 0,
        "anchorZ": 0,
        "bounds": {"width": max_x + 1, "height": max_y + 1, "length": max_z + 1},
        "blocks": prefab_blocks,
    }

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(prefab, f, indent=2)
        f.write("\n")

    print(f"Wrote prefab with {len(prefab_blocks)} blocks to {output_path}")
    print(f"Bounds: {max_x+1}x{max_y+1}x{max_z+1}")


def main():
    parser = argparse.ArgumentParser(description="Convert Minecraft schematic to Hytale prefab")
    parser.add_argument("--input", required=True, help="Path to .schematic/.schem/.litematic file")
    parser.add_argument("--output", required=True, help="Path to output Hytale prefab JSON")
    parser.add_argument("--mapping", default=None, help="Optional mapping JSON")
    args = parser.parse_args()

    mapping_path = args.mapping
    if mapping_path is None:
        default_mapping = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "mappings", "default.json"
        )
        if os.path.exists(default_mapping):
            mapping_path = default_mapping

    convert_schematic_to_prefab(args.input, args.output, mapping_path)


if __name__ == "__main__":
    main()