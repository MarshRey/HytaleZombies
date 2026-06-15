#!/usr/bin/env python3
"""End-to-end test for the schematic converter pipeline.

Creates a small in-memory test schematic, converts it to Hytale prefab,
and verifies the output is valid.
"""
import io
import json
import os
import sys
import struct
import zlib

# Add converter dir to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from schematic_converter import BlockMapper, convert_schematic_to_prefab

def create_test_schematic():
    """Create a minimal 3x3x3 schematic in memory (uncompressed NBT)."""
    try:
        import nbtlib
    except ImportError:
        print("SKIP: nbtlib not installed. Run: pip install nbtlib")
        sys.exit(0)

    # Build a 3x3x3 stone box with an air interior (like a tiny room)
    width, height, length = 5, 4, 5
    palette = {
        "minecraft:stone": 0,
        "minecraft:air": 1,
    }
    
    total = width * height * length
    block_data = bytearray(total)
    
    for y in range(height):
        for z in range(length):
            for x in range(width):
                idx = (y * length + z) * width + x
                # Floor (y=0) and walls (edges) are stone, interior is air
                if y == 0 or x == 0 or x == width - 1 or z == 0 or z == length - 1 or y == height - 1:
                    block_data[idx] = 0  # stone
                else:
                    block_data[idx] = 1  # air

    # Build schematic NBT
    schematic = nbtlib.tag.Compound({
        "Width": nbtlib.tag.Int(width),
        "Height": nbtlib.tag.Int(height),
        "Length": nbtlib.tag.Int(length),
        "Palette": nbtlib.tag.Compound({
            k: nbtlib.tag.Int(v) for k, v in palette.items()
        }),
        "BlockData": nbtlib.tag.ByteArray(block_data),
    })

    root = nbtlib.tag.Compound({"Schematic": schematic})
    nbt_file = nbtlib.File(root)
    
    # Write to bytes (uncompressed)
    buf = io.BytesIO()
    nbt_file.write(buf)
    return buf.getvalue()


def test_convert(tmpdir="test_output"):
    print("=== Schematic Converter Pipeline Test ===")
    
    # Create test schematic
    print("1. Creating test schematic (5x4x5 stone room)...")
    schematic_bytes = create_test_schematic()
    
    schematic_path = os.path.join(tmpdir, "test_room.schematic")
    os.makedirs(tmpdir, exist_ok=True)
    with open(schematic_path, "wb") as f:
        f.write(schematic_bytes)
    print(f"   Saved: {schematic_path} ({len(schematic_bytes)} bytes)")

    # Convert to prefab
    print("2. Converting to Hytale prefab...")
    output_path = os.path.join(tmpdir, "test_room.prefab.json")
    mapping_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "mappings", "default.json"
    )
    
    convert_schematic_to_prefab(schematic_path, output_path, mapping_path)

    # Verify output
    print("3. Verifying output...")
    with open(output_path, "r") as f:
        prefab = json.load(f)

    assert "version" in prefab, "Missing version"
    assert prefab["version"] == 8, f"Expected version 8, got {prefab['version']}"
    assert "blocks" in prefab, "Missing blocks array"
    
    blocks = prefab["blocks"]
    print(f"   Blocks in prefab: {len(blocks)}")
    
    # Count stone blocks (should be walls + floor + ceiling of a 5x4x5 room)
    # 5x5 floor + 5x5 ceiling + 4 walls * 3 tall * 5 wide - overlap = ~98 blocks
    stone_blocks = [b for b in blocks if b["name"] == "Rock_Stone"]
    print(f"   Stone blocks: {len(stone_blocks)}")
    assert len(stone_blocks) >= 50, f"Expected >=50 stone blocks, got {len(stone_blocks)}"
    
    # Verify block structure
    for block in blocks[:5]:
        assert "x" in block and "y" in block and "z" in block and "name" in block, \
            f"Malformed block: {block}"
        print(f"   Block: ({block['x']}, {block['y']}, {block['z']}) = {block['name']}")

    # Verify bounds
    if "bounds" in prefab:
        b = prefab["bounds"]
        print(f"   Bounds: {b['width']}x{b['height']}x{b['length']}")

    print("\n=== ALL TESTS PASSED ===")
    print(f"Output: {output_path}")
    return True


if __name__ == "__main__":
    test_convert()