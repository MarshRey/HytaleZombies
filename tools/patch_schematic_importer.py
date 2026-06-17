#!/usr/bin/env python3
"""Patch SchematicImporter for Hytale 0.5.4 — fix checkcast Player -> PlayerRef."""
import struct
import zipfile
import os
import shutil
import subprocess
import sys

JAR_PATH = r"C:\Users\Marsh\Desktop\Code\Personal\HytaleZombies\run\mods\SchematicImporter-1.1.0.jar"
CLASS_PATH = "thirtyvirus/schem/helpers/HSICommand$SchematicCommand.class"

def parse_constant_pool(data):
    """Parse the constant pool and return list of entries with offsets."""
    cp_count = struct.unpack('>H', data[8:10])[0]
    entries = []  # (index, offset, tag, info)
    offset = 10
    for idx in range(1, cp_count):
        tag = data[offset]
        entry = {'idx': idx, 'offset': offset, 'tag': tag}

        if tag == 1:  # CONSTANT_Utf8
            length = struct.unpack('>H', data[offset+1:offset+3])[0]
            utf8 = data[offset+3:offset+3+length].decode('utf-8', errors='replace')
            entry['utf8'] = utf8
            entry['size'] = 3 + length
        elif tag == 7:  # CONSTANT_Class
            name_idx = struct.unpack('>H', data[offset+1:offset+3])[0]
            entry['name_idx'] = name_idx
            entry['size'] = 3
        elif tag in (3, 4):  # Integer, Float
            entry['size'] = 5
        elif tag in (5, 6):  # Long, Double
            entry['size'] = 9
        elif tag == 8:  # CONSTANT_String
            entry['size'] = 3
        elif tag in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            class_idx = struct.unpack('>H', data[offset+1:offset+3])[0]
            nt_idx = struct.unpack('>H', data[offset+3:offset+5])[0]
            entry['class_idx'] = class_idx
            entry['nt_idx'] = nt_idx
            entry['size'] = 5
        elif tag == 12:  # NameAndType
            entry['size'] = 5
        elif tag == 15:  # MethodHandle
            entry['size'] = 4
        elif tag == 16:  # MethodType
            entry['size'] = 3
        elif tag == 17:  # Dynamic
            entry['size'] = 5
        elif tag == 18:  # InvokeDynamic
            entry['size'] = 5
        else:
            entry['size'] = 3  # guess

        entries.append(entry)
        offset += entry['size']

    return entries

def resolve_class_name(entries, class_cp_idx):
    """Given a CONSTANT_Class CP index, return the UTF8 class name."""
    for e in entries:
        if e['idx'] == class_cp_idx:
            if e['tag'] != 7:
                return None
            name_idx = e['name_idx']
            for e2 in entries:
                if e2['idx'] == name_idx and e2['tag'] == 1:
                    return e2['utf8']
    return None

def main():
    # Backup
    backup = JAR_PATH + ".backup"
    if not os.path.exists(backup):
        shutil.copy2(JAR_PATH, backup)
        print(f"Backed up to {backup}")

    tmpdir = "jar_patch_tmp2"
    os.makedirs(tmpdir, exist_ok=True)

    with zipfile.ZipFile(JAR_PATH, 'r') as z:
        z.extract(CLASS_PATH, tmpdir)

    class_file = os.path.join(tmpdir, CLASS_PATH)
    with open(class_file, 'rb') as f:
        data = bytearray(f.read())

    entries = parse_constant_pool(data)

    # Find Player class CP index and PlayerRef class CP index
    player_cp_idx = None
    playerref_cp_idx = None
    for e in entries:
        if e['tag'] == 7:
            name = resolve_class_name(entries, e['idx'])
            if name == 'com/hypixel/hytale/server/core/entity/entities/Player':
                player_cp_idx = e['idx']
                print(f"Player class at CP[{e['idx']}] offset={e['offset']}")
            elif name == 'com/hypixel/hytale/server/core/universe/PlayerRef':
                playerref_cp_idx = e['idx']
                print(f"PlayerRef class at CP[{e['idx']}] offset={e['offset']}")

    if player_cp_idx is None:
        print("ERROR: Player class not found in CP")
        return 1
    if playerref_cp_idx is None:
        print("ERROR: PlayerRef class not found in CP")
        return 1

    # Now find all checkcast instructions (opcode 192 = 0xC0)
    # that reference the Player class CP index
    patches = 0
    pos = 0
    while pos < len(data) - 2:
        if data[pos] == 0xC0:  # checkcast
            cp_idx = struct.unpack('>H', data[pos+1:pos+3])[0]
            if cp_idx == player_cp_idx:
                print(f"Found checkcast Player at bytecode offset {pos}")
                # Replace with PlayerRef CP index
                data[pos+1:pos+3] = struct.pack('>H', playerref_cp_idx)
                patches += 1
        pos += 1

    if patches == 0:
        print("No checkcast Player found — searching for any Player CP reference...")
        # Search for the 2-byte CP index in the bytecode
        player_bytes = struct.pack('>H', player_cp_idx)
        pos = 0
        while pos < len(data) - 2:
            if data[pos:pos+2] == player_bytes:
                print(f"  CP ref to Player at offset {pos} (checking context...)")
                # Check if it's preceded by a checkcast, anew, instanceof, etc.
                if pos >= 1 and data[pos-1] in (0xC0, 0xBB, 0xC1):  # checkcast, new, instanceof
                    print(f"    -> Patching (opcode {hex(data[pos-1])})")
                    data[pos:pos+2] = struct.pack('>H', playerref_cp_idx)
                    patches += 1
            pos += 1

    print(f"Total patches: {patches}")

    if patches > 0:
        with open(class_file, 'wb') as f:
            f.write(data)
        print("Patched class file written")

        # Update JAR
        result = subprocess.run([
            "jar", "uf", JAR_PATH,
            "-C", tmpdir, CLASS_PATH.replace("/", os.sep)
        ], capture_output=True, text=True)
        if result.returncode != 0:
            print(f"jar error: {result.stderr}")
            return 1
        print(f"Updated {JAR_PATH}")
        print("\nRestart the server and try /hsi schematic again!")
    else:
        print("No patches needed or nothing found to patch.")

    return 0

if __name__ == "__main__":
    sys.exit(main())
