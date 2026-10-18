/* Modified for NS Detector: privacy-preserving local history and report exports. */
package com.eltavine.duckdetector.features.dashboard.presentation.history

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.features.dashboard.presentation.export.ReportRedactor
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState

/** A minimal local record, deliberately excluding raw report blocks, details and device identity. */
data class DashboardDetectorSnapshot(
    val detectorId: String,
    val status: DetectorStatus,
    val headline: String,
)

data class DashboardScanSnapshot(
    val completedAtEpochMillis: Long,
    val durationMillis: Long,
    val detectors: List<DashboardDetectorSnapshot>,
)

enum class DashboardHistoryChangeKind {
    NEW_FINDING,
    RESOLVED_FINDING,
    STATUS_CHANGED,
    MODULE_ADDED,
    MODULE_REMOVED,
}

data class DashboardHistoryChange(
    val detectorId: String,
    val kind: DashboardHistoryChangeKind,
    val previous: DashboardDetectorSnapshot?,
    val current: DashboardDetectorSnapshot?,
)

const val DASHBOARD_HISTORY_LIMIT = 20

/** Partial and cancelled scans must never replace a completed baseline. */
fun DashboardUiState.completedHistorySnapshot(): DashboardScanSnapshot? {
    val completedAt = overview.scanCompletedAtEpochMillis ?: return null
    val duration = overview.scanDurationMillis ?: return null
    if (isLoading || detectorSummaries.isEmpty() || detectorSummaries.any { !it.ready }) return null
    return DashboardScanSnapshot(
        completedAtEpochMillis = completedAt,
        durationMillis = duration,
        detectors = detectorSummaries.map {
            DashboardDetectorSnapshot(it.id.value, it.status, ReportRedactor.redact(it.headline).take(240))
        }.sortedBy { it.detectorId },
    )
}

/** Newest first; revisiting the screen or rotating it does not add duplicate scan records. */
fun appendDashboardHistory(
    history: List<DashboardScanSnapshot>,
    snapshot: DashboardScanSnapshot,
): List<DashboardScanSnapshot> = (listOf(snapshot) + history.filter {
    it.completedAtEpochMillis != snapshot.completedAtEpochMillis
}).sortedByDescending { it.completedAtEpochMillis }.take(DASHBOARD_HISTORY_LIMIT)

/**
 * Compare typed outcomes only. A disappeared module or lost visibility does not resolve a finding;
 * wording changes are not evidence changes. Detector additions/removals remain visible as coverage.
 */
fun compareDashboardScans(
    previous: DashboardScanSnapshot,
    current: DashboardScanSnapshot,
): List<DashboardHistoryChange> {
    val before = previous.detectors.associateBy { it.detectorId }
    val after = current.detectors.associateBy { it.detectorId }
    return (before.keys + after.keys).sorted().mapNotNull { id ->
        val old = before[id]
        val new = after[id]
        val kind = when {
            old == null -> if (new?.status?.isFinding == true) DashboardHistoryChangeKind.NEW_FINDING else DashboardHistoryChangeKind.MODULE_ADDED
            new == null -> DashboardHistoryChangeKind.MODULE_REMOVED
            old.status == new.status -> null
            !old.status.isFinding && new.status.isFinding -> DashboardHistoryChangeKind.NEW_FINDING
            old.status.isFinding && new.status.severity == DetectionSeverity.ALL_CLEAR -> DashboardHistoryChangeKind.RESOLVED_FINDING
            else -> DashboardHistoryChangeKind.STATUS_CHANGED
        }
        kind?.let { DashboardHistoryChange(id, it, old, new) }
    }
}

private val DetectorStatus.isFinding: Boolean
    get() = severity == DetectionSeverity.DANGER || severity == DetectionSeverity.WARNING
