#!/usr/bin/env python3
"""Collect paired emulator rendering evidence; never infer physical-device FPS.

Refs: developer.android.com/tools/dumpsys#gfxinfo and android15-release
frameworks/base/libs/hwui/{FrameInfo.h,FrameInfo.cpp,JankTracker.cpp}. FRAMESTATS contains
recent buffered frames, not every rendered frame. Keep the system summary too.
"""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import argparse
import csv
import hashlib
import json
import math
import re
import sys
import time
from pathlib import Path

UNKNOWN_TIME = (1 << 63) - 1
REFERENCES = [
    "https://developer.android.com/tools/dumpsys#gfxinfo",
    "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/libs/hwui/FrameInfo.h",
    "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/libs/hwui/FrameInfo.cpp",
    "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/libs/hwui/JankTracker.cpp",
]


def percentile(values, percentage):
    """Nearest-rank percentile; no interpolation inventing an unobserved frame."""
    return round(sorted(values)[max(0, math.ceil(len(values) * percentage / 100) - 1)], 3) if values else None


def parse_framestats(raw):
    """Parse by header names across API changes and multiple window blocks.

    Nonzero Flags are excluded conservatively and counted. FrameDeadline's
    INT64_MAX means unknown. Completion-after-deadline is an observation, not
    Android's jank classification (which includes GPU and buffer-stuffing logic).
    """
    durations, deadline_durations = [], []
    counts = {key: 0 for key in ("rows", "nonzeroFlags", "invalidTiming", "malformedRows", "deadlineKnown", "completionAfterDeadline")}
    columns, inside, blocks = None, False, 0
    for line in raw.splitlines():
        if line.strip() == "---PROFILEDATA---":
            inside = not inside
            columns = None
            if inside:
                blocks += 1
            continue
        if not inside or not line.strip():
            continue
        cells = next(csv.reader([line.strip()]))
        while cells and not cells[-1]:
            cells.pop()
        if columns is None:
            columns = [cell.strip() for cell in cells]
            if len(set(columns)) != len(columns) or not {"Flags", "IntendedVsync", "FrameCompleted"} <= set(columns):
                raise ValueError("Unsupported FRAMESTATS header; required columns are missing or duplicate")
            continue
        counts["rows"] += 1
        if len(cells) != len(columns):
            counts["malformedRows"] += 1
            continue
        try:
            frame = dict(zip(columns, map(int, cells)))
        except ValueError:
            counts["malformedRows"] += 1
            continue
        if frame["Flags"] != 0:
            counts["nonzeroFlags"] += 1
            continue
        start, end = frame["IntendedVsync"], frame["FrameCompleted"]
        if not 0 < start < end < UNKNOWN_TIME:
            counts["invalidTiming"] += 1
            continue
        durations.append((end - start) / 1_000_000)
        deadline = frame.get("FrameDeadline", UNKNOWN_TIME)
        if start < deadline < UNKNOWN_TIME:
            counts["deadlineKnown"] += 1
            counts["completionAfterDeadline"] += end > deadline
            deadline_durations.append((deadline - start) / 1_000_000)
    if inside:
        raise ValueError("Truncated FRAMESTATS block")
    totals = [int(n) for n in re.findall(r"^\s*Total frames rendered:\s*(\d+)", raw, re.MULTILINE)]
    janky = [int(n) for n in re.findall(r"^\s*Janky frames:\s*(\d+)", raw, re.MULTILINE)]
    return {"profileBlocks": blocks, "sampledFrames": len(durations), **counts,
            "sampleDurationMs": {"p50": percentile(durations, 50), "p90": percentile(durations, 90),
                                 "p95": percentile(durations, 95), "p99": percentile(durations, 99),
                                 "max": max(durations, default=None)},
            "sampleDeadlineBudgetMsP50": percentile(deadline_durations, 50),
            "systemTotalFrames": totals[0] if len(totals) == 1 else None,
            "systemJankyFrames": janky[0] if len(janky) == 1 else None,
            "scope": "Recent buffered Flags=0 HWUI frames; completion after deadline is not the system jank verdict."}


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def completed_progress(root):
    match = re.search(r"\b(\d+)\s*/\s*(\d+)\s*(?:modules returned|个模块已返回)", ui.window_text(root), re.IGNORECASE)
    return tuple(map(int, match.groups())) if match else None


def wait_complete(expected_modules):
    deadline = time.monotonic() + 180
    top()
    latest = None
    while time.monotonic() < deadline:
        latest = completed_progress(ui.hierarchy(deadline=deadline))
        if latest and latest[1] > 0 and latest[1] != expected_modules:
            raise RuntimeError(f"Expected {expected_modules} catalog modules, observed {latest[1]}")
        if latest == (expected_modules, expected_modules):
            return latest[1]
        if not ui.adb("shell", "pidof", ui.PACKAGE, deadline=deadline).strip():
            raise RuntimeError("Application exited before all modules returned")
        time.sleep(2)
    raise RuntimeError(f"Scan completion not observed within 180 seconds: {latest}")


def consent():
    targets = ("Skip", "Not now", "Keep offline", "Built-in only", "Keep disabled", "Use offline snapshot",
               "Decline", "Don't allow", "Don’t allow", "Deny", "Disable", "Use regular", "Use regular notifications",
               "Continue anyway", "I Agree & Continue", "I agree and continue", "Agree and continue", "I agree",
               "Continue", "Done", "暂不", "跳过", "保持离线", "拒绝", "不允许", "禁用", "使用普通通知",
               "仍然继续", "同意并继续", "我已阅读并同意", "继续", "完成")
    actions, deadline = [], time.monotonic() + 180
    while time.monotonic() < deadline:
        root = ui.hierarchy(deadline=deadline)
        if ui.dashboard_visible(root):
            return actions
        node = ui.matching_node(root, targets)
        if node is not None and ui.tap(node):
            actions.append(node.attrib.get("text", ""))
        else:
            ui.adb("shell", "input", "swipe", "540", "1700", "540", "700", "350")
        time.sleep(1.5)
    raise RuntimeError("Dashboard was not reached through the real consent flow")


def top():
    for _ in range(3):
        ui.adb("shell", "input", "swipe", "540", "700", "540", "1900", "250")
    time.sleep(1)


def collect_phase(folder, name, operation):
    reset_started = time.monotonic_ns()
    reset = ui.adb("shell", "dumpsys", "gfxinfo", ui.PACKAGE, "reset")
    (folder / f"{name}-reset.txt").write_text(reset)
    events = []
    started = time.monotonic_ns()
    operation(events, started)
    ended = time.monotonic_ns()
    raw = ui.adb("shell", "dumpsys", "gfxinfo", ui.PACKAGE, "framestats")
    (folder / f"{name}-gfxinfo.txt").write_text(raw)
    result = {"phase": name, "resetHostMonotonicNs": reset_started,
              "startHostMonotonicNs": started, "endHostMonotonicNs": ended,
              "dumpFinishedHostMonotonicNs": time.monotonic_ns(), "inputs": events,
              "rendering": parse_framestats(raw)}
    write_json(folder / f"{name}.json", result)
    return result


def scroll_operation(width, height):
    def scroll(events, started):
        for start, end in ((0.78, 0.35), (0.35, 0.78)):
            args = ["shell", "input", "swipe", str(width // 2), str(int(height * start)),
                    str(width // 2), str(int(height * end)), "400"]
            events.append({"relativeHostMs": round((time.monotonic_ns() - started) / 1_000_000, 3), "adbArguments": args})
            ui.adb(*args)
            time.sleep(0.1)
    return scroll


def run_case(name, apk, expected_hash, source, expected_modules, output, repeats, width, height):
    folder = output / name
    folder.mkdir()
    ui.OUTPUT, ui.EMULATOR_RECOVERIES = folder, []
    ui.EXPECTED_MODULE_COUNT = expected_modules
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if digest != expected_hash:
        raise ValueError(f"Reviewed APK hash mismatch: {name}")
    ui.adb("uninstall", ui.PACKAGE, check=False)
    ui.adb("install", str(apk))
    ui.adb("logcat", "-c")
    identity = {"case": name, "apkName": apk.name, "apkSha256": digest, "sourceCommit": source,
                "package": ui.PACKAGE, "freshInstall": True, "expectedModuleCount": expected_modules,
                "preferences": "Fresh install defaults; offline consent choices only; always-enabled default watermark retained.",
                "phases": []}
    write_json(folder / "identity.json", identity)
    (folder / "package.txt").write_text(ui.adb("shell", "dumpsys", "package", ui.PACKAGE))
    ui.launch()
    identity["consentActions"] = consent()
    identity["moduleCount"] = wait_complete(expected_modules)
    time.sleep(2)
    ui.preserve("dashboard-before")
    for index in range(repeats):
        top()
        identity["phases"].append(collect_phase(folder, f"idle-{index + 1}", lambda _e, _s: time.sleep(1)))
        phase = collect_phase(folder, f"scroll-{index + 1}", scroll_operation(width, height))
        identity["phases"].append(phase)
        if phase["rendering"]["sampledFrames"] == 0:
            raise RuntimeError("Controlled scroll produced no usable FRAMESTATS samples")
    # No all-module rescan control exists: relaunch starts a genuine new scan.
    # A fresh process has new counters; the first reset precedes its launch.
    ui.adb("shell", "am", "force-stop", ui.PACKAGE)
    def launch_scan(events, started):
        events.append({"relativeHostMs": 0, "action": "launcher entry; includes startup and scan"})
        ui.launch()
        time.sleep(1)
    identity["phases"].append(collect_phase(folder, "relaunch-scan-1", launch_scan))
    for index in range(2, 7):
        identity["phases"].append(collect_phase(folder, f"relaunch-scan-{index}", lambda _e, _s: time.sleep(1)))
    completion_started = time.monotonic_ns()
    identity["relaunchModuleCount"] = wait_complete(expected_modules)
    identity["postSampleCompletionWaitMs"] = round((time.monotonic_ns() - completion_started) / 1_000_000, 3)
    ui.preserve("dashboard-after")
    logs = ui.adb("logcat", "-d")
    (folder / "logcat.txt").write_text(logs)
    (folder / "emulator-recoveries.json").write_text(json.dumps(ui.EMULATOR_RECOVERIES, indent=2))
    if any("AndroidRuntime" in line and f"Process: {ui.PACKAGE}, PID:" in line for line in logs.splitlines()):
        raise RuntimeError(f"Main application process crashed in case {name}")
    write_json(folder / "result.json", identity)
    return identity


def restore_settings(previous, output):
    restored, errors = {}, {}
    for key, value in previous.items():
        try:
            args = ("delete", "global", key) if value == "null" else ("put", "global", key, value)
            ui.adb("shell", "settings", *args)
            restored[key] = ui.adb("shell", "settings", "get", "global", key).strip()
            if restored[key] != value:
                raise RuntimeError("Restored setting did not match its original value")
        except Exception as error:
            errors[key] = type(error).__name__
    write_json(output / "animation-restoration.json", {"previous": previous, "restored": restored, "errors": errors})
    return errors


def main():
    global ui
    import ns_emulator_smoke as ui
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline", "candidate"):
        parser.add_argument(f"--{name}", type=Path, required=True)
        parser.add_argument(f"--{name}-sha256", required=True)
        parser.add_argument(f"--{name}-source", required=True)
    parser.add_argument("--baseline-modules", type=int, default=20)
    parser.add_argument("--candidate-modules", type=int, required=True)
    parser.add_argument("--package", default="io.github.withite.nsdetector")
    parser.add_argument("--output", type=Path, default=Path("motion-output"))
    parser.add_argument("--repeats", type=int, choices=range(1, 6), default=3)
    args = parser.parse_args()
    for name in ("baseline", "candidate"):
        if not re.fullmatch("[0-9a-f]{64}", getattr(args, name + "_sha256")):
            parser.error(f"Invalid {name} SHA-256")
        if not re.fullmatch("[0-9a-f]{40}", getattr(args, name + "_source")):
            parser.error(f"Invalid {name} source commit")
        if getattr(args, name + "_modules") <= 0:
            parser.error(f"Expected {name} module count must be positive")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package):
        parser.error("Invalid Android package")
    args.output.mkdir(parents=True, exist_ok=False)
    ui.PACKAGE = args.package
    settings = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    previous = {key: ui.adb("shell", "settings", "get", "global", key).strip() for key in settings}
    try:
        for key in settings:
            ui.adb("shell", "settings", "put", "global", key, "1")
        effective = {key: ui.adb("shell", "settings", "get", "global", key).strip() for key in settings}
        if any(float(value) != 1 for value in effective.values()):
            raise RuntimeError("Animation scales could not be enabled")
        display = ui.adb("shell", "wm", "size")
        width, height = map(int, re.findall(r"(\d+)x(\d+)", display)[-1])
        for name, command in {"display.txt": ("shell", "dumpsys", "display"),
                              "surfaceflinger.txt": ("shell", "dumpsys", "SurfaceFlinger"),
                              "device-properties.txt": ("shell", "getprop"),
                              "adb-version.txt": ("version",)}.items():
            (args.output / name).write_text(ui.adb(*command))
        results = []
        write_json(args.output / "protocol.json", {
            "order": ["baseline", "candidate"], "sameEmulator": True, "samePackage": args.package,
            "animations": effective, "repeats": args.repeats, "viewportPixels": [width, height],
            "expectedModules": {name: getattr(args, name + "_modules") for name in ("baseline", "candidate")},
            "preferencePolicy": "Fresh installation for each version, default appearance and enabled watermark; no benchmark-only app flags.",
            "workloadNotice": "Module counts and detector work may differ; scan-phase metrics do not isolate animation changes.",
            "references": REFERENCES,
            "scope": "Fixed-order paired emulator experiment. Host load, order, compilation, and instrumentation affect results. No physical-device FPS claim or regression threshold. FRAMESTATS is a recent-frame buffer; dump/reset overhead is outside each operation but may affect scheduling.",
        })
        for name in ("baseline", "candidate"):
            results.append(run_case(name, getattr(args, name), getattr(args, name + "_sha256"),
                                    getattr(args, name + "_source"), getattr(args, name + "_modules"),
                                    args.output, args.repeats, width, height))
        write_json(args.output / "comparison.json", {"status": "collected", "cases": results,
                   "sameConsentActions": results[0]["consentActions"] == results[1]["consentActions"]})
        print("Collected paired motion evidence; inspect raw frames, timing inputs and protocol.json.")
    finally:
        active_exception = sys.exc_info()[0] is not None
        restoration_errors = restore_settings(previous, args.output)
        ui.OUTPUT = args.output
        try:
            ui.screenshot("last-screen")
            (args.output / "last-logcat.txt").write_text(ui.adb("logcat", "-d"))
        except Exception as error:
            print(f"Final diagnostic collection failed: {type(error).__name__}")
        if restoration_errors and not active_exception:
            raise RuntimeError("Animation settings restoration failed; see animation-restoration.json")


if __name__ == "__main__":
    main()
