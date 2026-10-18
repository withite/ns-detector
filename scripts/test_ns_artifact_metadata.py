#!/usr/bin/env python3
"""Guard against accepting a legacy scan total for newer built artifacts."""
# Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0
import unittest
from unittest.mock import patch

import ns_emulator_smoke as smoke


class ArtifactModuleCountTest(unittest.TestCase):
    def test_known_legacy_release_and_new_catalog_count(self):
        legacy = {"versionName": "1.0.0", "applicationId": "io.github.withite.nsdetector"}
        self.assertEqual(20, smoke.artifact_module_count(legacy))
        self.assertEqual(21, smoke.artifact_module_count({**legacy, "versionName": "1.0.1", "detectorCount": 21}))

    def test_missing_count_is_not_accepted_for_new_or_unidentified_artifacts(self):
        for metadata in ({}, {"versionName": "1.0.1", "applicationId": "io.github.withite.nsdetector"},
                         {"versionName": "1.0.0", "applicationId": "io.github.withite.nsdetector.debug"}):
            with self.subTest(metadata=metadata), self.assertRaisesRegex(RuntimeError, "Invalid artifact detectorCount"):
                smoke.artifact_module_count(metadata)

    def test_invalid_explicit_count_cannot_trigger_legacy_fallback(self):
        for value in (None, True, False, 0, -1, 1001, "21", 21.0, [], {}):
            metadata = {"versionName": "1.0.0", "applicationId": "io.github.withite.nsdetector", "detectorCount": value}
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, "Invalid artifact detectorCount"):
                smoke.artifact_module_count(metadata)
        with self.assertRaisesRegex(RuntimeError, "must be an object"):
            smoke.artifact_module_count([])

    def test_browser_counts_follow_current_artifact_and_filtered_result(self):
        with patch.object(smoke, "EXPECTED_MODULE_COUNT", 21):
            self.assertEqual(("Showing 1 of 21 modules", "显示 1 / 21 个模块"), smoke.browser_count(1))
            self.assertEqual(("Showing 21 of 21 modules", "显示 21 / 21 个模块"), smoke.browser_count(21))


if __name__ == "__main__":
    unittest.main()
