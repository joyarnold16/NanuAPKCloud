#!/usr/bin/env python3
"""Fail when a built Nanu Local AI APK/AAB contains trading implementation."""

from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile


BLOCKED_MARKERS = (
    "PaperTradingActivity",
    "TradingActivity",
    "TradingEngine",
    "activity_paper_trading",
    "activity_trading",
    "home_markets",
    "home_paper",
    "plus_trading",
    "position_size",
)


def verify(path: Path) -> None:
    if not path.is_file() or path.stat().st_size <= 1_000_000:
        raise SystemExit(f"Missing or suspiciously small Android artifact: {path}")

    try:
        with ZipFile(path) as archive:
            failures: list[str] = []
            for entry in archive.infolist():
                name = entry.filename
                encoded_name = name.encode("utf-8", errors="ignore")
                for marker in BLOCKED_MARKERS:
                    if marker.encode() in encoded_name:
                        failures.append(f"{marker!r} in entry name {name!r}")

                # AndroidManifest.xml, resources.arsc and DEX files contain the
                # package's reachable classes/resources. Scanning their decoded
                # ZIP payload catches both UTF-8 and Android UTF-16 string pools.
                leaf = name.rsplit("/", 1)[-1]
                if leaf not in {"AndroidManifest.xml", "resources.arsc", "resources.pb"} and not (
                    leaf.startswith("classes") and leaf.endswith(".dex")
                ):
                    continue
                payload = archive.read(entry)
                for marker in BLOCKED_MARKERS:
                    if marker.encode() in payload or marker.encode("utf-16le") in payload:
                        failures.append(f"{marker!r} in packaged payload {name!r}")

            if failures:
                details = "\n - ".join(sorted(set(failures)))
                raise SystemExit(f"Trading implementation leaked into {path}:\n - {details}")
    except BadZipFile as exc:
        raise SystemExit(f"Invalid Android artifact ZIP: {path}: {exc}") from exc

    print(f"No-trading artifact validation passed: {path}")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Usage: verify_no_trading_artifact.py <apk-or-aab> [...]")
    for value in sys.argv[1:]:
        verify(Path(value))


if __name__ == "__main__":
    main()
