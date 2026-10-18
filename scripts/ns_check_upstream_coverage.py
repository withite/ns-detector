#!/usr/bin/env python3
"""Fail a build if any detector, native unit or process entry from the pinned base disappears."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import re
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = "b1135897cff4ddf01797f042bdd15f7d7e364e0d"
CATALOG = "sdk/runtime/src/main/kotlin/com/eltavine/duckdetector/sdk/DetectorCatalog.kt"
CMAKE = "sdk/runtime/src/main/cpp/CMakeLists.txt"


def original(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT, text=True)


def detector_names(text):
    return set(re.findall(r"^\s+([A-Za-z]+Detector),\s*$", text, re.MULTILINE))


def native_units(text):
    table = text.split("set(DUCKDETECTOR_NATIVE_UNITS", 1)[1].split(")", 1)[0]
    return set(re.findall(r"^\s+(\w+)\s+((?:feature|core|capability)/[\w/]+)\s*$", table, re.MULTILINE))


def main():
    current = detector_names((ROOT / CATALOG).read_text())
    expected = detector_names(original(CATALOG))
    missing = expected - current
    if len(expected) != 18 or missing:
        raise SystemExit(f"Upstream detector retention failed: {sorted(missing)}")
    units = native_units((ROOT / CMAKE).read_text())
    absent_units = native_units(original(CMAKE)) - units
    if absent_units:
        raise SystemExit(f"Upstream native units missing: {sorted(absent_units)}")
    files = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", BASE, "feature", "capability"], cwd=ROOT, text=True).splitlines()
    service_count = 0
    for path in files:
        if not path.endswith("AndroidManifest.xml"):
            continue
        name_key = "{http://schemas.android.com/apk/res/android}name"
        original_root = ET.fromstring(original(path))
        expected_services = {node.attrib[name_key]: node.attrib for node in original_root.iter()
                             if node.tag in ("service", "provider")}
        if not expected_services:
            continue
        file = ROOT / path
        actual = {node.attrib[name_key]: node.attrib for node in ET.parse(file).iter()
                  if node.tag in ("service", "provider")} if file.is_file() else {}
        for name, attributes in expected_services.items():
            if actual.get(name) != attributes:
                raise SystemExit(f"Upstream process/provider declaration or isolation changed: {path}: {name}")
        service_count += len(expected_services)
    for name in sorted(expected):
        print(f"RETAINED {name}")
    print(f"PASS: all {len(expected)} upstream detectors, {len(units)} native units and {service_count} process/provider entries retained; {len(current) - len(expected)} added detector(s).")


if __name__ == "__main__":
    main()
