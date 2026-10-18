// Modified 2026 by NS Detector contributors: independent report filenames.
package com.eltavine.duckdetector.features.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardScreenTest {

    @Test
    fun `export filename sanitizes model and uses deterministic timestamp`() {
        assertEquals(
            "ns_detector_report_Pixel_7_20240102_030405.txt",
            generateExportReportFileName(" Pixel 7 ", 1704164645000L),
        )
    }

    @Test
    fun `blank model falls back to unknown`() {
        assertEquals(
            "ns_detector_report_unknown_20240102_030405.txt",
            generateExportReportFileName("   ", 1704164645000L),
        )
    }

    @Test
    fun `json filename uses the json extension`() {
        assertEquals(
            "ns_detector_report_Pixel_7_20240102_030405.json",
            generateExportReportFileName("Pixel 7", 1704164645000L, json = true),
        )
    }
}
