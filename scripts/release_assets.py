"""Prepare two signed APKs, optional deltas, and the VLoc release manifest."""

import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path


ABIS = {"arm64-v8a", "armeabi-v7a"}
PACKAGE = "app.locationlab"


def apk_info(path, sdk):
    tools = sdk / "build-tools" / "35.0.0"
    aapt = tools / ("aapt.exe" if (tools / "aapt.exe").is_file() else "aapt")
    apksigner = tools / ("apksigner.bat" if (tools / "apksigner.bat").is_file() else "apksigner")
    info = subprocess.check_output([str(aapt), "dump", "badging", str(path)], encoding="utf-8")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='(\d+\.\d+\.\d+)'", info, re.M)
    abi = re.search(r"^native-code:\s+'([^']+)'\s*$", info, re.M)
    if not package or package[1] != PACKAGE or not abi or abi[1] not in ABIS:
        raise ValueError(f"Invalid VLoc APK: {path}")
    cert = subprocess.check_output([str(apksigner), "verify", "--print-certs", str(path)], encoding="utf-8")
    signers = tuple(sorted(re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", cert, re.M)))
    if not signers:
        raise ValueError(f"Missing APK signer: {path}")
    return int(package[2]), package[3], abi[1], signers, "application-debuggable" in info


def compressed_ranges(path, data):
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            offset = entry.header_offset
            name_length, extra_length = struct.unpack_from("<HH", data, offset + 26)
            start = offset + 30 + name_length + extra_length
            yield entry.filename, start, entry.compress_size


def make_patch(old_apk, new_apk):
    old = memoryview(old_apk.read_bytes())
    new = memoryview(new_apk.read_bytes())
    entries = {name: (start, size) for name, start, size in compressed_ranges(old_apk, old)}
    commands = [struct.pack(">4sQ", b"VLD1", len(new))]
    cursor = 0
    for name, start, size in sorted(compressed_ranges(new_apk, new), key=lambda entry: entry[1]):
        previous = entries.get(name)
        if size < 32 or previous is None or size != previous[1]:
            continue
        old_start = previous[0]
        if new[start:start + size] != old[old_start:old_start + size]:
            continue
        if start > cursor:
            commands.extend([struct.pack(">BI", 1, start - cursor), new[cursor:start]])
        commands.append(struct.pack(">BQI", 0, old_start, size))
        cursor = start + size
    if cursor < len(new):
        commands.extend([struct.pack(">BI", 1, len(new) - cursor), new[cursor:]])
    commands.append(b"\xff")
    patch = b"".join(commands)
    if restore_patch(old, patch) != new:
        raise ValueError(f"Delta failed its round trip: {new_apk}")
    return patch


def restore_patch(old, patch):
    magic, length = struct.unpack_from(">4sQ", patch)
    if magic != b"VLD1":
        raise ValueError("Invalid delta")
    cursor, output = 12, bytearray()
    while patch[cursor] != 255:
        if patch[cursor] == 0:
            offset, count = struct.unpack_from(">QI", patch, cursor + 1)
            output.extend(old[offset:offset + count])
            cursor += 13
        elif patch[cursor] == 1:
            count, = struct.unpack_from(">I", patch, cursor + 1)
            cursor += 5
            output.extend(patch[cursor:cursor + count])
            cursor += count
        else:
            raise ValueError("Invalid delta command")
    if len(output) != length or cursor + 1 != len(patch):
        raise ValueError("Incomplete delta")
    return output


def asset(path):
    return {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "size": path.stat().st_size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", nargs=2, type=Path, help="signed APKs, one for each ABI")
    parser.add_argument("--previous-release", required=True, type=Path,
                        help="previous official APK used to verify signer and increasing versions")
    parser.add_argument("--base", action="append", default=[], type=Path, help="optional previous signed APK, repeatable")
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--allow-debug", action="store_true", help="for local update testing only")
    args = parser.parse_args()

    targets = [(path, apk_info(path, args.sdk)) for path in args.apks]
    code, version, _, signers, _ = targets[0][1]
    if {info[2] for _, info in targets} != ABIS or any(
        info[:2] != (code, version) or info[3] != signers or (info[4] and not args.allow_debug)
        for _, info in targets
    ):
        raise ValueError("Expected matching signed VLoc APKs for both ARM ABIs")
    previous_code, previous_version, _, previous_signers, _ = apk_info(args.previous_release, args.sdk)
    if signers != previous_signers:
        raise ValueError("Release must use the same signer as the previous official APK")
    if code <= previous_code or tuple(map(int, version.split('.'))) <= tuple(map(int, previous_version.split('.'))):
        raise ValueError("Release must have a higher versionCode and versionName than the previous official APK")
    bases = [(path, apk_info(path, args.sdk)) for path in args.base]
    for base, (old_code, _, _, old_signers, _) in bases:
        if old_code >= code or old_signers != signers:
            raise ValueError(f"Delta base needs a lower version and the same signer: {base}")
    args.output.mkdir(parents=True, exist_ok=True)
    packages = {}
    for path, (_, _, abi, _, _) in targets:
        target = args.output / f"VLoc-v{version}-{abi}.apk"
        if target.exists() and asset(target)["sha256"] != asset(path)["sha256"]:
            raise ValueError(f"Version already has a different APK: {target}")
        if target.resolve() != path.resolve():
            shutil.copyfile(path, target)
        record = asset(target)
        record["deltas"] = []
        for base, (old_code, old_version, old_abi, old_signers, _) in bases:
            if old_abi != abi:
                continue
            old_hash = asset(base)["sha256"]
            patch = make_patch(base, target)
            # APKs built with different compression levels often share few compressed bytes.
            if len(patch) * 100 > record["size"] * 95:
                continue
            output = args.output / f"VLoc-v{old_version}-to-v{version}-{abi}-{old_hash[:12]}.vld"
            output.write_bytes(patch)
            record["deltas"].append({"fromSha256": old_hash, **asset(output)})
        packages[abi] = record
    manifest = {"schema": 1, "versionName": version, "versionCode": code, "packages": packages}
    (args.output / "vloc-update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared VLoc v{version} release assets in {args.output}")


if __name__ == "__main__":
    main()
