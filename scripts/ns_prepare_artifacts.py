#!/usr/bin/env python3
"""Collect NS Detector outputs and record their actual APK metadata and hashes."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import quote


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def collect(variant, unsigned=False):
    root = Path(__file__).resolve().parents[1]
    os.chdir(root)
    metadata_files = list(Path(f"app/build/outputs/apk/{variant}").rglob("output-metadata.json"))
    if len(metadata_files) != 1:
        raise SystemExit(f"Expected one APK metadata file, found {len(metadata_files)}")
    metadata_path = metadata_files[0]
    metadata = json.loads(metadata_path.read_text())
    elements = metadata["elements"]
    if len(elements) != 1:
        raise SystemExit("Expected one universal APK")
    element = elements[0]
    apk = metadata_path.parent / element["outputFile"]
    if not apk.is_file():
        raise SystemExit("APK metadata does not point to a built APK")
    dist = Path("dist")
    dist.mkdir(exist_ok=True)
    for stale in dist.iterdir():
        if stale.is_file():
            stale.unlink()
    version = element["versionName"]
    if not re.fullmatch(r"[A-Za-z0-9._+-]+", version):
        raise SystemExit("Unsafe APK version name")
    suffix = "UNSIGNED" if unsigned else variant
    apk_name = f"NS-Detector-{version}-{suffix}.apk"
    target = dist / apk_name
    shutil.copy2(apk, target)
    sdk = Path(os.environ["ANDROID_HOME"])
    properties = dict(
        line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    tools = sdk / "build-tools" / properties["duckdetector.android.buildTools"]
    signature = "UNSIGNED export. Not installable or publishable until privately signed." if unsigned else command(
        str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(target))
    (dist / "apk-signature.txt").write_text(signature + "\n")
    command(str(tools / "zipalign"), "-c", "-P", "16", "4", str(target))
    for aar in Path("sdk/aar/build/repository").rglob("*.aar"):
        shutil.copy2(aar, dist / "NS-Detector-SDK.aar")
        break
    commit = command("git", "rev-parse", "HEAD")
    timestamp = command("git", "show", "-s", "--format=%cI", "HEAD")
    repository = os.environ.get("GITHUB_REPOSITORY", "withite/ns-detector")
    tag = os.environ.get("NS_RELEASE_TAG", f"preview-local-{commit[:8]}")
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    info = {
        "applicationId": metadata["applicationId"], "variant": variant, "signed": not unsigned,
        "versionName": version, "versionCode": element["versionCode"],
        "commit": commit, "upstreamCommit": "b1135897cff4ddf01797f042bdd15f7d7e364e0d",
        "buildUrl": f"https://github.com/{repository}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}",
        "toolchain": {key: value for key, value in properties.items() if key.startswith("duckdetector.android.")},
        "validation": "See Actions reports; cross-OEM physical device validation is pending.",
    }
    (dist / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    manifest = {
        "schemaVersion": 1, "channel": os.environ.get("NS_CHANNEL", "preview"), "branch": "main",
        "versionName": version, "versionCode": element["versionCode"],
        "commit": {"sha": commit, "subject": command("git", "show", "-s", "--format=%s", "HEAD"),
                   "body": command("git", "show", "-s", "--format=%b", "HEAD"),
                   "authorName": command("git", "show", "-s", "--format=%an", "HEAD"), "authoredAt": timestamp},
        "builtAtUtc": timestamp,
        "apk": {"name": apk_name, "downloadUrl": f"https://github.com/{repository}/releases/download/{quote(tag, safe='')}/{quote(apk_name, safe='')}",
                "sizeBytes": target.stat().st_size, "sha256": digest},
    }
    (dist / ("unsigned-manifest.json" if unsigned else "update.json")).write_text(json.dumps(manifest, indent=2) + "\n")
    checksums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}" for path in sorted(dist.iterdir()) if path.is_file()]
    (dist / "SHA256SUMS").write_text("\n".join(checksums) + "\n")
    print(f"Prepared {apk_name}: {target.stat().st_size} bytes, sha256:{digest}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=["debug", "release"], required=True)
    parser.add_argument("--unsigned", action="store_true")
    args = parser.parse_args()
    if args.unsigned and args.variant != "release":
        parser.error("Unsigned export is only valid for release builds")
    collect(args.variant, args.unsigned)
