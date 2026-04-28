#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


DEFAULT_PACKAGE_NAME = "com.nullxoid.android"
DEFAULT_RELATION = "delegate_permission/common.get_login_creds"
FINGERPRINT_RE = re.compile(r"^[0-9a-f]{2}(:[0-9a-f]{2}){31}$", re.IGNORECASE)
APKSIGNER_SHA256_RE = re.compile(r"SHA-256 digest:\s*([0-9a-fA-F:]{64,95})")


def normalize_fingerprint(value: str) -> str:
    cleaned = value.strip().replace(" ", "").upper()
    if ":" not in cleaned:
        if len(cleaned) != 64 or not re.fullmatch(r"[0-9A-F]{64}", cleaned):
            raise ValueError("fingerprint must be a 32-byte SHA-256 hex digest")
        cleaned = ":".join(cleaned[index : index + 2] for index in range(0, len(cleaned), 2))
    if not FINGERPRINT_RE.fullmatch(cleaned):
        raise ValueError("fingerprint must be colon-separated SHA-256 hex")
    return cleaned


def fingerprint_from_apk(apk_path: Path, apksigner: str) -> str:
    completed = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk_path)],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stdout.strip() or "apksigner failed")
    match = APKSIGNER_SHA256_RE.search(completed.stdout)
    if not match:
        raise RuntimeError("apksigner output did not include a SHA-256 certificate digest")
    return normalize_fingerprint(match.group(1))


def build_statement(package_name: str, fingerprints: list[str]) -> list[dict[str, object]]:
    return [
        {
            "relation": [DEFAULT_RELATION],
            "target": {
                "namespace": "android_app",
                "package_name": package_name,
                "sha256_cert_fingerprints": fingerprints,
            },
        }
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate Digital Asset Links for Android passkey Credential Manager association."
    )
    parser.add_argument("--package-name", default=DEFAULT_PACKAGE_NAME)
    parser.add_argument("--fingerprint", action="append", default=[], help="Release signing SHA-256 fingerprint.")
    parser.add_argument("--apk", type=Path, help="APK to inspect with apksigner.")
    parser.add_argument("--apksigner", default="apksigner", help="Path to Android build-tools apksigner.")
    parser.add_argument("--output", type=Path, help="Write assetlinks JSON to this file.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    fingerprints = [normalize_fingerprint(value) for value in args.fingerprint]
    if args.apk:
        fingerprints.append(fingerprint_from_apk(args.apk, args.apksigner))
    fingerprints = sorted(set(fingerprints))
    if not fingerprints:
        print("Provide --fingerprint or --apk.", file=sys.stderr)
        return 2
    payload = build_statement(args.package_name, fingerprints)
    rendered = json.dumps(payload, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
