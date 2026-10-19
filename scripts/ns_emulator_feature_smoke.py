#!/usr/bin/env python3
"""Capture new diagnostic rows in the installed APK, after the general UI smoke test."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import json
import re
import time

import ns_emulator_smoke as ui

OUTPUT = Path("feature-output")
UNAVAILABLE = (r"Field missing|Malformed snapshot field|Read denied|"
               r"Snapshot unavailable or access denied|Unsupported|"
               r"UID changed during sampling|Read failed|Not evaluated")
PROCESS_ROWS = {
    "Host baseline": (r"Ordinary installed application|Special or system host · context only",
                      "ordinary multiuser application range"),
    "Credential views": (r"All four IDs match the sampled UID|Credential views differ|"
                         r"Observed · baseline not applied", "same sampling thread's public UID API"),
    "Effective capabilities (CapEff)": (r"0x[0-9a-f]{16}", "CAP_BLOCK_SUSPEND (bit 36)"),
    "Permitted capabilities (CapPrm)": (r"0x[0-9a-f]{16}", "CAP_BLOCK_SUSPEND (bit 36)"),
    "Ambient capabilities (CapAmb)": (r"0x[0-9a-f]{16}", "CAP_BLOCK_SUSPEND (bit 36)"),
    "Seccomp": (r"Disabled for the sampling thread|Strict mode|Filter mode|Unknown mode",
                "Mode 0 is informational"),
    "Collection": (r"One bounded proc snapshot", "/proc/thread-self/status"),
}
CA_VALUE = (r"(?:Partial counts · )?User-added: \d+ · System: \d+"
            r"(?: · Unclassified: \d+)?|AndroidCAStore provider unavailable|"
            r"CA-store access denied|CA-store read failed|Not evaluated")
ROW_LABELS = set(PROCESS_ROWS) | {"VPN", "Default HTTP proxy", "Private DNS", "Visible CA entries"}


def texts(root):
    return [value for node in root.iter("node") for key in ("text", "content-desc")
            if (value := node.attrib.get(key, "").strip())]


def row_segment(root, label):
    """Keep text between diagnostic row labels, including a row continued above the viewport."""
    values = texts(root)
    label_visible = label in values
    if label_visible:
        start = values.index(label) + 1
    else:
        start = 0
    stop = next((index for index in range(start, len(values)) if values[index] in ROW_LABELS), len(values))
    return values[start:stop], label_visible, stop < len(values)


def swipe_up(root, fraction=0.12):
    bounds = [list(map(int, re.findall(r"\d+", node.attrib.get("bounds", ""))))
              for node in root.iter("node")]
    width = max((b[2] for b in bounds if len(b) == 4), default=1080)
    height = max((b[3] for b in bounds if len(b) == 4), default=2400)
    ui.adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.75)),
           str(width // 2), str(int(height * (0.75 - fraction))), "350")
    time.sleep(0.5)


def capture_row(label, value_pattern, scope, name):
    root, node = ui.locate((label,))
    # A label clipped at the bottom does not establish that its value or explanation rendered.
    bounds = list(map(int, re.findall(r"\d+", node.attrib.get("bounds", ""))))
    height = max((int(v) for n in root.iter("node")
                  for v in re.findall(r"\d+", n.attrib.get("bounds", ""))[3:4]), default=2400)
    if len(bounds) == 4 and bounds[1] > height * 0.55:
        swipe_up(root)
        root = ui.hierarchy()
    observed_value = None
    observed_detail = None
    evidence = []
    for attempt in range(8):
        snapshot = f"{name}-{attempt + 1:02d}"
        ui.preserve(snapshot, root)
        evidence.append(snapshot)
        segment, label_visible, next_row_visible = row_segment(root, label)
        # Identical capability values/explanations occur in adjacent rows. Establish the
        # value beside this exact label before accepting any continued explanation.
        if label_visible:
            current_value = next((v for v in segment if re.fullmatch(value_pattern, v)), None)
            if current_value is not None:
                if observed_value is not None and observed_value != current_value:
                    raise RuntimeError(f"Rendered value changed while capturing {label}")
                observed_value = current_value
        elif observed_value is None:
            raise RuntimeError(f"Lost {label} before observing its label and value together")
        if observed_value is not None:
            observed_detail = next((v for v in segment if scope in v), observed_detail)
        if observed_value is not None and observed_detail is not None:
            return {"label": label, "value": observed_value, "detail": observed_detail,
                    "evidence": evidence, "renderedAvailability": rendered_availability(observed_value)}
        if next_row_visible:
            raise RuntimeError(f"Reached the next row without capturing {label}'s own value and scope")
        if attempt < 7:
            swipe_up(root)
            root = ui.hierarchy()
    raise RuntimeError(f"Could not capture value and scope for {label}; see {OUTPUT}/{name}-*.xml")


def rendered_availability(value):
    if value == "Not evaluated":
        return "not_evaluated"
    if value in ("Read failed", "Malformed snapshot field", "CA-store read failed"):
        return "collection_error"
    if re.fullmatch(UNAVAILABLE, value) or value in (
            "AndroidCAStore provider unavailable", "CA-store access denied"):
        return "limited_or_unavailable"
    if value.startswith("Partial counts · "):
        return "partial_observation"
    return "value_rendered"


def open_module(module_id, title, section):
    root, _ = ui.locate(("Search module names or IDs",), forward=False)
    if ui.matching_node(root, ("Clear search",)) is not None:
        ui.click_label(("Clear search",), forward=False)
    ui.search_modules(module_id)
    ui.locate(ui.browser_count(1))
    ui.click_label((title,))
    root, _ = ui.locate((section,))
    ui.preserve(f"{module_id}-section-collapsed", root)
    ui.click_label((section,))


def main():
    OUTPUT.mkdir(exist_ok=True)
    ui.OUTPUT = OUTPUT  # Keep general smoke evidence immutable.
    metadata = json.loads(Path("dist/build-info.json").read_text())
    ui.EXPECTED_MODULE_COUNT = ui.artifact_module_count(metadata)
    if ui.EXPECTED_MODULE_COUNT != 21:
        raise RuntimeError("This feature exercise targets the 21-module release")
    ui.adb("shell", "cmd", "locale", "set-app-locales", ui.PACKAGE,
           "--user", "0", "--locales", "en-US")
    ui.adb("shell", "am", "force-stop", ui.PACKAGE)
    ui.launch()
    ui.wait_window(ui.dashboard_active, "English dashboard", timeout=45)
    completed, wait_seconds = ui.wait_for_completed_scan()
    ui.preserve("01-completed-dashboard")

    open_module("process_sandbox", "Process Sandbox", "Kernel status observations")
    process_rows = []
    for index, (label, (pattern, scope)) in enumerate(PROCESS_ROWS.items(), 1):
        if label == "Collection":
            ui.click_label(("Scope and interpretation",))
        process_rows.append(capture_row(label, f"(?:{pattern})|(?:{UNAVAILABLE})", scope,
                                        f"process-{index:02d}"))
    seccomp_detail = next(row["detail"] for row in process_rows if row["label"] == "Seccomp")
    if "Mode 2 does not verify filter contents, all threads or whole-device policy" not in seccomp_detail:
        raise RuntimeError("Seccomp result lost its scope qualification")

    open_module("network_environment", "Network Environment", "Connectivity observations")
    ca = capture_row("Visible CA entries", CA_VALUE, "visible to this app's user/profile only", "network-ca")
    if "do not establish this app's actual TLS trust" not in ca["detail"]:
        raise RuntimeError("CA-store result lost its trust qualification")
    counts = re.search(r"User-added: (\d+) · System: (\d+)(?: · Unclassified: (\d+))?", ca["value"])
    ca["visibleCounts"] = ({"userAdded": int(counts[1]), "system": int(counts[2]),
                            "unclassified": int(counts[3] or 0)} if counts else None)
    ui.click_label(("Clear search",), forward=False)
    ui.locate(ui.browser_count(ui.EXPECTED_MODULE_COUNT), forward=False)
    ui.preserve("99-search-cleared")
    logs = ui.adb("logcat", "-d")
    if any("AndroidRuntime" in line and f"Process: {ui.PACKAGE}, PID:" in line for line in logs.splitlines()):
        raise RuntimeError("Runtime log contains a fatal exception in the main app process")
    result = {"status": "passed", "package": ui.PACKAGE, "sourceCommit": metadata.get("commit"),
              "completedModuleCount": completed, "expectedModuleCount": ui.EXPECTED_MODULE_COUNT,
              "emulatorCompletionWaitSeconds": wait_seconds, "processSandboxRows": process_rows,
              "visibleCaEntries": ca, "emulatorRecoveries": ui.EMULATOR_RECOVERIES,
              "scope": "Installed signed APK rendered the new rows and their scope/unavailability wording on an API 35 emulator. No fixed UID, capability, Seccomp or CA totals are assumed; missing data is not a negative detection. Row icons do not expose typed Context/Support enums to UI automation, so this check does not independently verify those enums or physical-device detection accuracy."}
    (OUTPUT / "feature-result.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == "__main__":
    try:
        main()
    finally:
        OUTPUT.mkdir(exist_ok=True)
        try:
            (OUTPUT / "logcat.txt").write_text(ui.adb("logcat", "-d"))
            ui.screenshot("last-screen")
        except Exception as error:
            print(f"Could not collect final feature evidence: {type(error).__name__}")
