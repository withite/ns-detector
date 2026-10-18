package com.eltavine.duckdetector.features.dashboard.presentation.export

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.report.*
import com.eltavine.duckdetector.features.dashboard.presentation.model.buildDashboardOverview
import org.junit.Assert.*
import org.junit.Test

class DashboardReportPrivacyTest {
    private val secret = "person@example.org"
    private val export = DashboardExport(
        ExportHeader("1.0", 1, "abcdef", "2026-10-17 03:04:05", "2026-10-17 03:04:05"),
        buildDashboardOverview(emptyList()), emptyList(),
        listOf(DetectorReport(
            "Example", "Observed", DetectionSeverity.INFO,
            listOf(ReportFact("Account", secret), ReportFact("Serial Number", "SN-private-42")),
            listOf(
                ReportBlock.Rows("Rows", listOf(ReportRow("Account", secret, "token=private123"))),
                ReportBlock.Bullets("Bullets", listOf(secret)),
                ReportBlock.Verbatim("Raw", listOf(secret, "Quotes \" and newline\n and tab\t")),
            ),
        )),
        DeviceReport(ReportDeviceIdentity("Brand", "Model", "16", "36"), listOf(ReportFact("Account", secret), ReportFact("Serial Number", "SN-private-42")), listOf(ReportBlock.Verbatim("Device", listOf(secret)))),
    )

    @Test
    fun `default text renderer redacts every block and device appendix`() {
        val report = DashboardReportRenderer.render(export)
        assertFalse(report.contains(secret))
        assertFalse(report.contains("private123"))
        assertFalse(report.contains("SN-private-42"))
        assertTrue(report.contains("[REDACTED_EMAIL]"))
        assertTrue(report.contains("NS DETECTOR"))
        assertTrue(report.contains("2026-10-17 03:04:05"))
    }

    @Test
    fun `json renderer redacts every block and escapes control characters`() {
        val report = DashboardJsonReportRenderer.render(export)
        assertFalse(report.contains(secret))
        assertFalse(report.contains("private123"))
        assertFalse(report.contains("SN-private-42"))
        assertTrue(report.contains("\"schemaVersion\": 1"))
        assertTrue(report.contains("\"redacted\": true"))
        assertTrue(report.contains("Quotes \\\" and newline\\n and tab\\t"))
        assertFalse(report.contains('\t'))
        assertTrue(report.trim().endsWith("}"))
    }
}
