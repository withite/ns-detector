#!/usr/bin/env python3
"""Exercise documented FRAMESTATS variants and corrupt/incomplete observations."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import ns_emulator_motion as motion
from ns_emulator_motion import parse_framestats

# The Android 15 HWUI emitted FrameInfoNames header. Values represent
# controlled 10 ms and 40 ms frame intervals, not recorded device measurements.
ANDROID_15_HEADER = (
    "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,"
    "AnimationStart,PerformTraversalsStart,DrawStart,FrameDeadline,FrameInterval,"
    "FrameStartTime,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,"
    "FrameCompleted,DequeueBufferDuration,QueueBufferDuration,GpuCompleted,"
    "SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted,"
)
ANDROID_15_ROWS = (
    "0,1,1000000000,1000000000,0,1000000000,1000000000,1000000000,1000000000,"
    "1016666667,1000000000,16666667,1000000000,1000000000,1000000000,1000000000,"
    "1010000000,0,0,1010000000,1010000000,1016666667,1010000000,\n"
    "0,2,2000000000,2000000000,0,2000000000,2000000000,2000000000,2000000000,"
    "2016666667,2000000000,16666667,2000000000,2000000000,2000000000,2000000000,"
    "2040000000,0,0,2040000000,2040000000,2050000001,2040000000,"
)


def block(header, rows):
    return "---PROFILEDATA---\n" + header + "\n" + rows + "\n---PROFILEDATA---\n"


class FramestatsTest(unittest.TestCase):
    def test_android_15_buffer_and_system_summary_have_distinct_scope(self):
        raw = "Total frames rendered: 500\nJanky frames: 7 (1.40%)\n" + block(ANDROID_15_HEADER, ANDROID_15_ROWS)
        result = parse_framestats(raw)
        self.assertEqual(500, result["systemTotalFrames"])
        self.assertEqual(7, result["systemJankyFrames"])
        self.assertEqual(2, result["sampledFrames"])
        self.assertEqual({"p50": 10.0, "p90": 40.0, "p95": 40.0, "p99": 40.0, "max": 40.0}, result["sampleDurationMs"])
        self.assertEqual(2, result["deadlineKnown"])
        self.assertEqual(1, result["completionAfterDeadline"])

    def test_older_header_without_deadline_and_reordered_columns(self):
        raw = block("FrameCompleted,Flags,IntendedVsync,", "20000000,0,10000000,")
        result = parse_framestats(raw)
        self.assertEqual(1, result["sampledFrames"])
        self.assertEqual(0, result["deadlineKnown"])
        self.assertIsNone(result["sampleDeadlineBudgetMsP50"])

    def test_multiple_windows_keep_their_own_header(self):
        raw = block("Flags,IntendedVsync,FrameCompleted,", "0,100,200,")
        raw += block("FrameCompleted,IntendedVsync,Flags,", "500,100,0,")
        result = parse_framestats(raw)
        self.assertEqual(2, result["profileBlocks"])
        self.assertEqual(2, result["sampledFrames"])

    def test_unknown_deadline_is_not_a_known_frame_budget(self):
        raw = block("Flags,IntendedVsync,FrameCompleted,FrameDeadline,",
                    "0,10000000,30000000,9223372036854775807,\n0,40000000,50000000,-1,")
        result = parse_framestats(raw)
        self.assertEqual(2, result["sampledFrames"])
        self.assertEqual(0, result["deadlineKnown"])

    def test_visibility_flag_pending_frame_and_corrupt_csv_are_counted(self):
        raw = block("Flags,IntendedVsync,FrameCompleted,",
                    "1,100,200,\n0,0,200,\n0,100,0,\n0,200,100,\n"
                    "0,100,9223372036854775807,\n0,100,nope,\n0,100,")
        result = parse_framestats(raw)
        self.assertEqual(7, result["rows"])
        self.assertEqual(1, result["nonzeroFlags"])
        self.assertEqual(4, result["invalidTiming"])
        self.assertEqual(2, result["malformedRows"])
        self.assertEqual(0, result["sampledFrames"])

    def test_static_idle_without_recent_frames_is_not_a_fabricated_pass_rate(self):
        result = parse_framestats("No process found for package\n")
        self.assertEqual(0, result["sampledFrames"])
        self.assertIsNone(result["sampleDurationMs"]["p95"])
        self.assertIsNone(result["systemJankyFrames"])

    def test_unrecognized_duplicate_or_truncated_headers_are_rejected(self):
        for raw in (block("Flags,Vsync", "0,123"),
                    block("Flags,IntendedVsync,FrameCompleted,Flags", "0,1,2,0"),
                    "---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted,\n0,1,2,\n"):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                parse_framestats(raw)


class MotionProtocolTest(unittest.TestCase):
    def test_legacy_terminal_progress_cannot_complete_a_new_21_module_scan(self):
        helper = SimpleNamespace(window_text=lambda root: root,
                                 hierarchy=lambda **_kwargs: "20 / 20 modules returned")
        with patch.object(motion, "ui", helper, create=True), patch.object(motion, "top"):
            self.assertEqual(20, motion.wait_complete(20))
            with self.assertRaisesRegex(RuntimeError, "Expected 21 catalog modules, observed 20"):
                motion.wait_complete(21)
        helper.hierarchy = lambda **_kwargs: "21 / 21 modules returned"
        with patch.object(motion, "ui", helper, create=True), patch.object(motion, "top"):
            self.assertEqual(21, motion.wait_complete(21))

    def test_one_failed_setting_restore_does_not_skip_other_settings(self):
        previous = {"window_animation_scale": "0.5", "transition_animation_scale": "1", "animator_duration_scale": "null"}
        values, writes = {}, []
        def adb(_shell, _settings, operation, _global, key, *args):
            if operation != "get":
                writes.append(key)
                if key == "window_animation_scale":
                    raise TimeoutError("simulated failed setting write")
                values[key] = args[0] if operation == "put" else "null"
            return values.get(key, "1")
        helper = SimpleNamespace(adb=adb)
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(motion, "ui", helper, create=True):
                errors = motion.restore_settings(previous, Path(directory))
            evidence = json.loads((Path(directory) / "animation-restoration.json").read_text())
        self.assertEqual(list(previous), writes)
        self.assertEqual({"window_animation_scale": "TimeoutError"}, errors)
        self.assertEqual("null", evidence["restored"]["animator_duration_scale"])


if __name__ == "__main__":
    unittest.main()
