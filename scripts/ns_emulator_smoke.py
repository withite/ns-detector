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
EMULATOR_RECOVERIES = []


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
    failure_phrases = (
        "keeps stopping", "isn't responding", "isn’t responding", "is not responding", "has stopped",
        "无响应", "未响应", "没有响应", "已停止运行", "屡次停止运行",
        "沒有回應", "無回應", "未回應", "已停止運作", "持續停止運作",
    )
    while True:
        adb("shell", "uiautomator", "dump", "/sdcard/ns-window.xml", deadline=deadline)
        raw = adb("shell", "cat", "/sdcard/ns-window.xml", deadline=deadline)
        (OUTPUT / "latest-window.xml").write_text(raw)
        root = ET.fromstring(raw)
        failures = [n for n in root.iter("node") if any(phrase in
                    (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).casefold()
                    for phrase in failure_phrases)]
        if not failures:
            return root
        title = failures[0].attrib
        quickstep = (len(failures) == 1 and title.get("package") == "android"
                     and title.get("resource-id") == "android:id/alertTitle"
                     and title.get("text") in ("Quickstep isn't responding", "Quickstep isn’t responding"))
        close = next((n for n in root.iter("node") if n.attrib.get("package") == "android"
                      and n.attrib.get("resource-id") == "android:id/aerr_close"
                      and n.attrib.get("text") == "Close app" and n.attrib.get("enabled") == "true"), None)
        if not quickstep or close is None or len(EMULATOR_RECOVERIES) >= 2:
            raise RuntimeError("Android reported a crash or ANR; see latest-window.xml")
        name = f"launcher-anr-recovery-{len(EMULATOR_RECOVERIES) + 1}"
        (OUTPUT / f"{name}.xml").write_text(raw)
        screenshot(name)
        if not tap(close):
            raise RuntimeError("Quickstep ANR close button is clipped; see latest-window.xml")
        EMULATOR_RECOVERIES.append({"dialogTitle": title["text"], "action": "Close app", "evidence": name})
        (OUTPUT / "emulator-recoveries.json").write_text(json.dumps(EMULATOR_RECOVERIES, indent=2))
        time.sleep(1)


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
    if len(bounds) == 4 and bounds[2] - bounds[0] >= 16 and bounds[3] - bounds[1] >= 16:
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        return True
    return False


def screenshot(name):
    (OUTPUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", f"{PACKAGE}/android.app.NativeActivity")


def matching_node(root, labels):
    return next((node for node in root.iter("node") if node.attrib.get("enabled") != "false"
                 and len(b := re.findall(r"\d+", node.attrib.get("bounds", ""))) == 4
                 and int(b[2]) - int(b[0]) >= 16 and int(b[3]) - int(b[1]) >= 16
                 and any(node.attrib.get(key, "").strip() in labels
                         for key in ("text", "content-desc"))), None)


def wait_window(predicate, description, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = hierarchy(deadline=deadline)
        if predicate(root):
            return root
        time.sleep(min(1, max(0, deadline - time.monotonic())))
    raise RuntimeError(f"Timed out waiting for {description}; see latest-window.xml")


def locate(labels, forward=True, scrolls=8):
    # Scan in both directions: the persisted list position can put controls above or below us.
    for direction, limit in ((forward, scrolls), (not forward, scrolls * 2)):
        for attempt in range(limit + 1):
            root = hierarchy()
            node = matching_node(root, labels)
            if node is not None:
                return root, node
            if attempt < limit:
                bounds = [list(map(int, re.findall(r"\d+", n.attrib.get("bounds", ""))))
                          for n in root.iter("node")]
                width = max((b[2] for b in bounds if len(b) == 4), default=1080)
                height = max((b[3] for b in bounds if len(b) == 4), default=2400)
                start, end = (0.78, 0.35) if direction else (0.35, 0.78)
                adb("shell", "input", "swipe", str(width // 2), str(int(height * start)),
                    str(width // 2), str(int(height * end)), "400")
                time.sleep(0.5)
    raise RuntimeError(f"Could not find visible control {labels}; see latest-window.xml")


def click_label(labels, forward=True):
    _, node = locate(labels, forward=forward)
    if not tap(node):
        raise RuntimeError(f"Control has no tappable bounds: {labels}")
    time.sleep(0.5)


def preserve(name, root=None):
    root = root if root is not None else hierarchy()
    (OUTPUT / f"{name}.xml").write_text(ET.tostring(root, encoding="unicode"))
    screenshot(name)


def browser_count(count):
    return (f"Showing {count} of {EXPECTED_MODULE_COUNT} modules",
            f"显示 {count} / {EXPECTED_MODULE_COUNT} 个模块")


def selection_owner(root, labels):
    return next((node for node in root.iter("node")
                 if (node.attrib.get("class") == "android.widget.RadioButton"
                     or node.attrib.get("checkable") == "true")
                 and matching_node(node, labels) is not None), None)


def search_modules(query):
    click_label(("Search module names or IDs", "搜索模块名称或标识"), forward=False)
    adb("shell", "input", "text", query)
    # Hardware-keyboard emulator settings may suppress the IME; BACK must not close the app.
    if re.search(r"\b(?:mInputShown|inputShown)=true\b", adb("shell", "dumpsys", "input_method")):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.5)


def exercise_module_browser():
    search_labels = ("Search module names or IDs", "搜索模块名称或标识")
    locate(search_labels)
    search_modules("network_environment")
    root, _ = locate(browser_count(1))
    preserve("06-search-one-module", root)
    click_label(("Clear search", "清除搜索"), forward=False)
    root, _ = locate(browser_count(EXPECTED_MODULE_COUNT))
    attention = ("Needs attention", "需要关注")
    owner = selection_owner(root, attention)
    selection_verified = owner is not None
    if selection_verified:
        click_label(attention, forward=False)
        root = wait_window(lambda r: (n := selection_owner(r, attention)) is not None and
                           any(n.attrib.get(key) == "true" for key in ("selected", "checked")),
                           "selected attention filter")
        preserve("07-attention-filter", root)
    search_modules("zznsnomatchzz")
    root, _ = locate(("No matching modules", "没有匹配的模块"))
    preserve("08-no-matching-modules", root)
    click_label(("Reset search and filters", "重置搜索与筛选"))
    root, _ = locate(browser_count(EXPECTED_MODULE_COUNT), forward=False)
    all_owner = selection_owner(root, ("All modules", "全部模块"))
    if all_owner is not None and not any(all_owner.attrib.get(key) == "true"
                                          for key in ("selected", "checked")):
        raise RuntimeError("Reset did not select All modules")
    preserve("09-browser-reset", root)
    return {"moduleIdSearch": True, "emptySearchAndReset": True,
            "attentionSelectionSemanticsVerified": selection_verified}


def saf_filename(root, extension):
    return next((node.attrib.get("text", "") for node in root.iter("node")
                 if node.attrib.get("resource-id") == "android:id/title"
                 and node.attrib.get("class") == "android.widget.EditText"
                 and node.attrib.get("package", "").endswith(".documentsui")
                 and node.attrib.get("text", "").startswith("ns_detector_report_")
                 and node.attrib.get("text", "").endswith(extension)), None)


def cancel_to_dashboard():
    for _ in range(4):
        root = hierarchy()
        if dashboard_active(root):
            return root
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
    raise RuntimeError("Cancelling the document picker did not return to the dashboard")


def dashboard_active(root):
    return (dashboard_visible(root)
            and matching_node(root, ("Choose report format", "选择报告格式")) is None
            and not any(n.attrib.get("package", "").endswith(".documentsui") for n in root.iter("node"))
            and any(n.attrib.get("package") == PACKAGE for n in root.iter("node")))


def open_export_format(labels):
    click_label(("Export diagnostic report", "导出诊断报告"), forward=False)
    root = wait_window(lambda r: matching_node(r, ("Choose report format", "选择报告格式")) is not None,
                       "report format dialog")
    if labels:
        click_label(labels)
    return root


def exercise_export_picker():
    preserve("10-export-format", open_export_format(None))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    preserve("11-format-cancelled", wait_window(dashboard_active, "dashboard after dialog cancellation"))
    open_export_format(("JSON report", "JSON 报告"))
    root = wait_window(lambda r: saf_filename(r, ".json"), "JSON system document picker")
    filename = saf_filename(root, ".json")
    preserve("12-json-picker", root)
    try:
        adb("shell", "wm", "user-rotation", "lock", "1")
        root = wait_window(lambda r: r.attrib.get("rotation") in ("1", "3") and
                           saf_filename(r, ".json") == filename, "rotated picker with original filename")
        preserve("13-json-picker-rotated", root)
    finally:
        adb("shell", "wm", "user-rotation", "lock", "0")
    root = wait_window(lambda r: r.attrib.get("rotation") == "0" and
                       saf_filename(r, ".json") == filename, "restored portrait picker")
    preserve("14-json-picker-restored", root)
    preserve("15-json-cancelled", cancel_to_dashboard())
    open_export_format(("Text report", "文本报告"))
    root = wait_window(lambda r: saf_filename(r, ".txt"), "text picker after JSON cancellation")
    preserve("16-text-picker-recovery", root)
    preserve("17-text-cancelled", cancel_to_dashboard())
    return {"formatDialogCancel": True, "jsonPickerRotationAndCancel": True,
            "textPickerRecoveryAndCancel": True, "jsonPickerFilename": filename,
            "scope": "Picker interaction and filename restoration only; frozen bytes and cache cleanup not verified."}


def artifact_module_count(metadata):
    if not isinstance(metadata, dict):
        raise RuntimeError("Artifact metadata must be an object")
    expected = metadata.get("detectorCount")
    if ("detectorCount" not in metadata and metadata.get("versionName") == "1.0.0"
            and metadata.get("applicationId") == "io.github.withite.nsdetector"):
        expected = 20  # Only the known legacy release predates the catalog count field.
    if type(expected) is not int or not 1 <= expected <= 1000:
        raise RuntimeError("Invalid artifact detectorCount")
    return expected


def main():
    global EXPECTED_MODULE_COUNT
    EXPECTED_MODULE_COUNT = artifact_module_count(json.loads(Path("dist/build-info.json").read_text()))
    apks = list(Path("dist").glob("*.apk"))
    if len(apks) != 1:
        raise RuntimeError("Expected exactly one APK for installation smoke testing")
    apk = apks[0]
    # Let the AOSP launcher settle after emulator boot before installing the application.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(3)
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
        targets = ("Skip", "Not now", "Keep offline", "Built-in only", "Keep disabled", "Use offline snapshot", "Decline", "Don't allow", "Don’t allow", "Deny", "Disable", "Use regular", "Use regular notifications",
                   "Continue anyway", "I Agree & Continue", "I agree and continue", "Agree and continue", "I agree", "Continue", "Done",
                   "暂不", "跳过", "保持离线", "拒绝", "不允许", "禁用", "使用普通通知", "仍然继续", "同意并继续", "我已阅读并同意", "继续", "完成")
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
    browser_interactions = exercise_module_browser()
    export_interactions = exercise_export_picker()
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
              "browserInteractions": browser_interactions, "exportInteractions": export_interactions,
              "emulatorRecoveryCount": len(EMULATOR_RECOVERIES), "emulatorRecoveries": EMULATOR_RECOVERIES,
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
