#!/usr/bin/env python3
"""Exercise the actual launcher, consent screens, dashboard and process restart."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = os.environ.get("NS_SMOKE_PACKAGE", "io.github.withite.nsdetector.debug")
OUTPUT = Path("smoke-output")
OUTPUT.mkdir(exist_ok=True)


def adb(*args, binary=False, check=True):
    return subprocess.run(["adb", *args], check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=not binary, timeout=40).stdout


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/ns-window.xml")
    raw = adb("shell", "cat", "/sdcard/ns-window.xml")
    (OUTPUT / "latest-window.xml").write_text(raw)
    return ET.fromstring(raw)


def tap(node):
    bounds = [int(number) for number in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    if len(bounds) == 4:
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        return True
    return False


def screenshot(name):
    (OUTPUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", f"{PACKAGE}/android.app.NativeActivity")


def main():
    apks = list(Path("dist").glob("*.apk"))
    if len(apks) != 1:
        raise RuntimeError("Expected exactly one APK for installation smoke testing")
    apk = apks[0]
    adb("install", "-r", str(apk))
    adb("logcat", "-c")
    launch()
    screenshot("01-first-launch")
    started = time.monotonic()
    reached_dashboard = False
    actions = []
    while time.monotonic() - started < 180:
        root = hierarchy()
        nodes = list(root.iter("node"))
        all_text = " ".join(node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "") for node in nodes)
        if any(word in all_text.lower() for word in ("keeps stopping", "isn't responding", "has stopped")):
            raise RuntimeError("Android reported a crash or ANR")
        if any(word.casefold() in all_text.casefold() for word in (
            "Environment overview", "Evidence overview", "证据总览", "环境总览",
            "Export diagnostic report", "Export report", "导出报告", "Local scan history", "扫描历史",
        )):
            reached_dashboard = True
            break
        # Prefer offline and low-impact choices; never opt into an experiment or network access.
        targets = ("Skip", "Not now", "Keep offline", "Built-in only", "Keep disabled", "Use offline snapshot", "Decline", "Disable", "Use regular", "Use regular notifications",
                   "Continue anyway", "I Agree & Continue", "I agree and continue", "Agree and continue", "I agree", "Continue", "Done",
                   "暂不", "跳过", "保持离线", "拒绝", "禁用", "使用普通通知", "仍然继续", "同意并继续", "我已阅读并同意", "继续", "完成")
        clicked = False
        for label in targets:
            for node in nodes:
                text = node.attrib.get("text", "").strip()
                if text.casefold() == label.casefold() and node.attrib.get("enabled") == "true":
                    clicked = tap(node)
                    if clicked:
                        actions.append(label)
                        break
            if clicked:
                break
        if not clicked:
            adb("shell", "input", "swipe", "540", "1700", "540", "700", "350")
        time.sleep(1.5)
    if not reached_dashboard:
        raise RuntimeError("Dashboard was not reached through the real consent flow")
    # Give the real detectors time to settle, without treating emulator verdicts as truth.
    for _ in range(12):
        time.sleep(3)
        hierarchy()
    screenshot("02-dashboard")
    if not adb("shell", "pidof", PACKAGE).strip():
        raise RuntimeError("Application process exited during scan")
    adb("shell", "input", "swipe", "540", "1700", "540", "650", "500")
    time.sleep(1)
    screenshot("03-evidence-and-history")
    # An installed app must survive a subsequent launcher entry with persisted preferences.
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    time.sleep(8)
    if not adb("shell", "pidof", PACKAGE).strip():
        raise RuntimeError("Application did not survive relaunch")
    screenshot("04-relaunch")
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", "zh-CN")
    time.sleep(8)
    chinese = hierarchy()
    chinese_text = " ".join(node.attrib.get("text", "") for node in chinese.iter("node"))
    if not any(word in chinese_text for word in ("扫描", "检测", "环境", "设置", "证据")):
        raise RuntimeError("Chinese application locale did not render localized UI")
    screenshot("05-chinese")
    logs = adb("logcat", "-d")
    if any("AndroidRuntime" in line and f"Process: {PACKAGE}, PID:" in line for line in logs.splitlines()):
        raise RuntimeError("Runtime log contains a fatal exception in the main app process")
    result = {"status": "passed", "package": PACKAGE, "consentActions": actions,
              "scope": "Android API 35 x86_64 emulator installation, launcher, dashboard, scan and restart; not physical-device accuracy validation."}
    (OUTPUT / "smoke-result.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == "__main__":
    try:
        main()
    finally:
        for name, args in {"logcat.txt": ("logcat", "-d"), "processes.txt": ("shell", "ps", "-A")}.items():
            try:
                (OUTPUT / name).write_text(adb(*args))
            except Exception as error:
                print(f"Could not collect {name}: {type(error).__name__}")
        try:
            screenshot("last-screen")
        except Exception:
            pass
