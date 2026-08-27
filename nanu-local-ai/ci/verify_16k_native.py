#!/usr/bin/env python3
"""Verify ELF LOAD segments in an APK/AAB are compatible with 16 KB pages.

Google Play requires 16 KB page-size support for new apps/updates targeting
Android 15+ when native code is shipped. This script extracts every packaged
.so and fails if any ELF LOAD segment has p_align below 0x4000.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

MIN_ALIGN = 0x4000


def readelf_path() -> str:
    tool = shutil.which("readelf")
    if not tool:
        raise SystemExit("readelf is required for 16 KB native validation")
    return tool


def load_alignments(so_path: Path, readelf: str) -> list[int]:
    result = subprocess.run(
        [readelf, "-lW", str(so_path)],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    aligns: list[int] = []
    for line in result.stdout.splitlines():
        if not re.match(r"\s*LOAD\s", line):
            continue
        fields = line.split()
        if not fields:
            continue
        try:
            aligns.append(int(fields[-1], 0))
        except ValueError as exc:
            raise SystemExit(f"Could not parse LOAD alignment in {so_path}: {line}") from exc
    if not aligns:
        raise SystemExit(f"No ELF LOAD segments found in {so_path}")
    return aligns


def verify_bundle(bundle: Path) -> None:
    if not bundle.exists() or bundle.stat().st_size == 0:
        raise SystemExit(f"Missing bundle: {bundle}")
    if bundle.suffix.lower() not in {".apk", ".aab"}:
        raise SystemExit("Expected an .apk or .aab")

    readelf = readelf_path()
    failures: list[str] = []
    checked = 0

    with tempfile.TemporaryDirectory(prefix="nanu-16k-") as temp_dir:
        root = Path(temp_dir)
        with zipfile.ZipFile(bundle) as archive:
            so_names = sorted(name for name in archive.namelist() if name.endswith(".so"))
            if not so_names:
                raise SystemExit(f"No native shared libraries found in {bundle}")
            for index, name in enumerate(so_names):
                target = root / f"{index}.so"
                with archive.open(name) as src, target.open("wb") as dst:
                    shutil.copyfileobj(src, dst)
                aligns = load_alignments(target, readelf)
                checked += 1
                minimum = min(aligns)
                print(f"{name}: min LOAD alignment 0x{minimum:x}")
                if minimum < MIN_ALIGN:
                    failures.append(f"{name}: 0x{minimum:x} < 0x{MIN_ALIGN:x}")

    if failures:
        print("16 KB native validation FAILED")
        for failure in failures:
            print(" -", failure)
        raise SystemExit(1)

    print(f"16 KB native validation passed for {checked} shared libraries in {bundle.name}.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path, help="APK or AAB to validate")
    args = parser.parse_args()
    verify_bundle(args.bundle)


if __name__ == "__main__":
    main()
