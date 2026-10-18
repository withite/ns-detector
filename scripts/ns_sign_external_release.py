#!/usr/bin/env python3
"""Privately sign a verified GitHub unsigned export; never upload a signing identity."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import quote
import zipfile


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sign(args):
    source = Path(args.input).resolve()
    info = json.loads((source / "build-info.json").read_text())
    manifest = json.loads((source / "unsigned-manifest.json").read_text())
    if info.get("signed") is not False or info.get("variant") != "release":
        raise SystemExit("Input must be an explicitly unsigned release export")
    if info.get("commit") != args.expected_commit or manifest["commit"]["sha"] != args.expected_commit:
        raise SystemExit("Export does not match the reviewed source commit")
    if info.get("applicationId") != "io.github.withite.nsdetector":
        raise SystemExit("Input has the wrong application identity")
    for line in (source / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split("  ", 1)
        path = source / name
        if path.parent != source or not path.is_file() or sha256(path) != digest:
            raise SystemExit("Unsigned export checksum verification failed")
    apk_name = manifest["apk"]["name"]
    apk = source / apk_name
    if apk.parent != source or sha256(apk) != manifest["apk"]["sha256"]:
        raise SystemExit("Unsigned APK does not match its source manifest")
    expected_abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
    with zipfile.ZipFile(apk) as archive:
        actual = {name.split("/")[1] for name in archive.namelist()
                  if re.fullmatch(r"lib/[^/]+/libduckdetector\.so", name)}
    if actual != expected_abis:
        raise SystemExit(f"Native library coverage mismatch: {sorted(actual)}")
    destination = Path(args.output).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    if any(destination.iterdir()):
        raise SystemExit("Output directory must be empty to avoid replacing a release")
    tools = Path(args.tools).resolve()
    version = info["versionName"]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise SystemExit("Expected a stable semantic version")
    target = destination / f"NS-Detector-{version}.apk"
    env = dict(os.environ)
    env["NS_STORE_PASSWORD"] = Path(args.password_file).read_text().strip()
    with tempfile.TemporaryDirectory(prefix="ns-align-") as temp:
        aligned = Path(temp) / "aligned.apk"
        subprocess.run([str(tools / "zipalign"), "-f", "-P", "16", "4", str(apk), str(aligned)], check=True)
        subprocess.run([str(tools / "apksigner"), "sign", "--ks", str(Path(args.keystore).resolve()),
                        "--ks-key-alias", args.alias, "--ks-pass", "env:NS_STORE_PASSWORD",
                        "--key-pass", "env:NS_STORE_PASSWORD", "--out", str(target), str(aligned)],
                       env=env, check=True)
    verification = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(target)], text=True)
    subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(target)], check=True)
    fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", verification)
    if not fingerprint:
        raise SystemExit("Could not verify the signing certificate")
    (destination / "apk-signature.txt").write_text(verification)
    info["signed"] = True
    info["abis"] = sorted(actual)
    info["signing"] = {"method": "private external signing after GitHub compilation",
                       "certificateSha256": fingerprint.group(1).lower()}
    (destination / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    manifest["channel"] = "stable"
    manifest["apk"] = {"name": target.name, "sizeBytes": target.stat().st_size, "sha256": sha256(target),
                       "downloadUrl": f"https://github.com/withite/ns-detector/releases/download/v{version}/{quote(target.name, safe='')}"}
    (destination / "update.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for aar in source.glob("*.aar"):
        shutil.copy2(aar, destination / aar.name)
    # The apksigner v4 sidecar is not required for normal package installation.
    for idsig in destination.glob("*.idsig"):
        idsig.unlink()
    (destination / "SHA256SUMS").write_text("\n".join(
        f"{sha256(path)}  {path.name}" for path in sorted(destination.iterdir()) if path.is_file()) + "\n")
    print(f"Signed and verified {target.name}; all four native ABIs retained.")
    print(f"Release certificate SHA-256: {fingerprint.group(1).lower()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("input", "output", "tools", "keystore", "password-file", "expected-commit"):
        parser.add_argument(f"--{name}", required=True)
    parser.add_argument("--alias", default="ns-detector")
    sign(parser.parse_args())
