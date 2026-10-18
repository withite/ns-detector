package com.eltavine.duckdetector.features.dashboard.presentation.history

import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.scan.DetectorSummary
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState
import com.eltavine.duckdetector.features.dashboard.presentation.model.buildDashboardOverview
import org.junit.Assert.*
import org.junit.Test

class DashboardScanHistoryTest {
    @Test
    fun `history is bounded sorted and deduplicated by completion time`() {
        val history = (1L..25L).fold(emptyList<DashboardScanSnapshot>()) { records, time ->
            appendDashboardHistory(records, scan(time, DetectorStatus.allClear()))
        }
        assertEquals(20, history.size)
        assertEquals((25L downTo 6L).toList(), history.map { it.completedAtEpochMillis })
        assertEquals(history, appendDashboardHistory(history, history.first()))
    }

    @Test
    fun `snapshot excludes incomplete scans and redacts free text`() {
        val summary = DetectorSummary(DetectorId("network"), "title", DetectorStatus.allClear(), "IP 192.0.2.4", "raw private detail", true)
        fun state(summaries: List<DetectorSummary>, loading: Boolean = false) = DashboardUiState(
            overview = buildDashboardOverview(summaries, 120L, 1234L),
            topFindings = emptyList(), cardOrder = emptyList(), isLoading = loading,
            detectorSummaries = summaries,
        )
        assertNull(state(listOf(summary), loading = true).completedHistorySnapshot())
        assertNull(state(listOf(summary.copy(ready = false))).completedHistorySnapshot())
        assertNull(state(emptyList()).completedHistorySnapshot())
        val snapshot = requireNotNull(state(listOf(summary)).completedHistorySnapshot())
        assertEquals("IP [REDACTED_IP]", snapshot.detectors.single().headline)
        assertEquals("network", snapshot.detectors.single().detectorId)
    }

    @Test
    fun `lost visibility and removed modules do not resolve a warning`() {
        val before = scan(1, DetectorStatus.warning())
        val limited = scan(2, DetectorStatus.info(InfoKind.SUPPORT))
        assertEquals(DashboardHistoryChangeKind.STATUS_CHANGED, compareDashboardScans(before, limited).single().kind)
        assertEquals(DashboardHistoryChangeKind.MODULE_REMOVED, compareDashboardScans(before, limited.copy(detectors = emptyList())).single().kind)
        assertEquals(DashboardHistoryChangeKind.RESOLVED_FINDING, compareDashboardScans(before, scan(2, DetectorStatus.allClear())).single().kind)
    }

    @Test
    fun `new findings and typed outcome changes are distinguished from wording edits`() {
        val before = scan(1, DetectorStatus.allClear())
        val warning = scan(2, DetectorStatus.warning())
        assertEquals(DashboardHistoryChangeKind.NEW_FINDING, compareDashboardScans(before, warning).single().kind)
        assertEquals(DashboardHistoryChangeKind.STATUS_CHANGED, compareDashboardScans(warning, scan(3, DetectorStatus.danger())).single().kind)
        val reworded = before.copy(detectors = before.detectors.map { it.copy(headline = "New wording") })
        assertTrue(compareDashboardScans(before, reworded).isEmpty())
    }

    @Test
    fun `cleared scan ids suppress delayed writes without suppressing a clock rollback`() {
        val cleared = scan(2_000L, DetectorStatus.warning())
        val olderBaseline = scan(1_000L, DetectorStatus.allClear())
        val tombstones = setOf(cleared.completedAtEpochMillis)
        val retained = visibleDashboardHistory(listOf(cleared, olderBaseline), tombstones)
        assertEquals(listOf(olderBaseline), retained)
        assertEquals(retained, appendDashboardHistory(retained, cleared, tombstones))
        val afterClockRollback = scan(1_500L, DetectorStatus.allClear())
        assertEquals(listOf(afterClockRollback, olderBaseline), appendDashboardHistory(retained, afterClockRollback, tombstones))
    }

    private fun scan(time: Long, status: DetectorStatus) = DashboardScanSnapshot(time, 100, listOf(DashboardDetectorSnapshot("test", status, "headline")))
}
