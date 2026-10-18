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
EXPECTED_MODULE_COUNT = 20


def window_text(root):
    return " ".join(node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")
                    for node in root.iter("node"))


def dashboard_visible(root):
    text = window_text(root).casefold()
    return any(word.casefold() in text for word in (
        "Device console", "设备环境控制台", "Environment overview", "Evidence overview",
        "证据总览", "环境总览", "Export diagnostic report", "Export report", "导出报告",
        "Local scan history", "扫描历史",
    ))


def adb(*args, binary=False, check=True, deadline=None):
    timeout = 40 if deadline is None else min(40, deadline - time.monotonic())
    if timeout <= 0:
        raise TimeoutError("ADB operation exceeded the polling deadline")
    return subprocess.run(["adb", *args], check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=not binary, timeout=timeout).stdout


def hierarchy(deadline=None):
    adb("shell", "uiautomator", "dump", "/sdcard/ns-window.xml", deadline=deadline)
    raw = adb("shell", "cat", "/sdcard/ns-window.xml", deadline=deadline)
    (OUTPUT / "latest-window.xml").write_text(raw)
    root = ET.fromstring(raw)
    text = window_text(root).casefold()
    if any(word in text for word in (
        "keeps stopping", "isn't responding", "isn’t responding", "is not responding", "has stopped",
        "无响应", "未响应", "没有响应", "已停止运行", "屡次停止运行",
        "沒有回應", "無回應", "未回應", "已停止運作", "持續停止運作",
    )):
        raise RuntimeError("Android reported a crash or ANR; see latest-window.xml")
    return root


def wait_for_completed_scan(timeout=180):
    if not 0 < timeout <= 180:
        raise ValueError("Scan completion polling must be bounded to at most 180 seconds")
    started = time.monotonic()
    deadline = started + timeout
    latest_progress = None
    try:
        # Startup cards can leave the list scrolled; expose the console before checking progress.
        adb("shell", "input", "swipe", "540", "700", "540", "1900", "350", deadline=deadline)
        while time.monotonic() < deadline:
            root = hierarchy(deadline=deadline)
            match = re.search(r"\b(\d+)\s*/\s*(\d+)\s*(?:modules returned|个模块已返回)",
                              window_text(root), flags=re.IGNORECASE)
            if match:
                latest_progress = tuple(int(value) for value in match.groups())
                if latest_progress == (EXPECTED_MODULE_COUNT, EXPECTED_MODULE_COUNT):
                    (OUTPUT / "completed-window.xml").write_text((OUTPUT / "latest-window.xml").read_text())
                    return latest_progress[0], round(time.monotonic() - started, 3)
            if not adb("shell", "pidof", PACKAGE, deadline=deadline).strip():
                raise RuntimeError("Application process exited before scan completion")
            time.sleep(min(3, max(0, deadline - time.monotonic())))
    except (TimeoutError, subprocess.TimeoutExpired):
        pass
    screenshot("scan-completion-timeout")
    raise RuntimeError(f"Scan did not visibly return {EXPECTED_MODULE_COUNT}/{EXPECTED_MODULE_COUNT} "
                       f"modules within {timeout}s; last progress={latest_progress}; see latest-window.xml")


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
        if dashboard_visible(root):
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
    # Observe real module completion. Emulator duration is not a physical-device performance metric.
    completed_count, completion_wait_seconds = wait_for_completed_scan()
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
    if not dashboard_visible(hierarchy()):
        raise RuntimeError("Application did not restore the dashboard after relaunch; see latest-window.xml")
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
              "completedModuleCount": completed_count, "expectedModuleCount": EXPECTED_MODULE_COUNT,
              "emulatorCompletionWaitSeconds": completion_wait_seconds,
              "scope": "Android API 35 x86_64 emulator installation, launcher, dashboard, full module return and restart; not physical-device accuracy or performance validation."}
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
