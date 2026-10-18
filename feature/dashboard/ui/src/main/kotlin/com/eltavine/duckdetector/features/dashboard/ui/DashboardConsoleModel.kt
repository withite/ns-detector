/*
 * Copyright 2026 NS Detector contributors
 * Added 2026 by NS Detector contributors: typed scan progress and user-directed filtering.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.scan.DetectorSummary

internal enum class DashboardConsoleFilter {
    ALL,
    ATTENTION,
    LIMITED,
    ERRORS,
    OTHER,
}

internal data class DashboardConsoleSelection(
    val filter: DashboardConsoleFilter = DashboardConsoleFilter.ALL,
    val query: String = "",
)

/** Display categories preserve the published status and distinguish unfinished modules. */
internal enum class DashboardConsoleSegment {
    PENDING,
    DANGER,
    WARNING,
    LIMITED,
    ERROR,
    CONTEXT,
    CLEAR,
    OTHER_INFO,
}

internal data class DashboardConsoleModel(
    val totalCount: Int,
    val readyCount: Int,
    val pendingCount: Int,
    val dangerCount: Int,
    val warningCount: Int,
    val limitedCount: Int,
    val errorCount: Int,
    val contextCount: Int,
    val allClearCount: Int,
    val otherInfoCount: Int,
    val visibleDetectorIds: List<DetectorId>,
    /** One segment for each input summary, in the same order. */
    val segments: List<DashboardConsoleSegment>,
) {
    val completedFraction: Float
        get() = if (totalCount == 0) 0f else readyCount.toFloat() / totalCount

    val attentionCount: Int get() = dangerCount + warningCount
    val otherCount: Int get() = contextCount + allClearCount
}

/**
 * Projects scan state without changing detector conclusions. Counts describe all modules;
 * filtering and the user's search query affect only [DashboardConsoleModel.visibleDetectorIds].
 */
internal fun buildDashboardConsoleModel(
    summaries: List<DetectorSummary>,
    selection: DashboardConsoleSelection = DashboardConsoleSelection(),
): DashboardConsoleModel {
    val segments = summaries.map { it.consoleSegment() }
    val pendingCount = segments.count { it == DashboardConsoleSegment.PENDING }
    val query = selection.query.trim()
    val visibleDetectorIds = summaries.indices.mapNotNull { index ->
        val summary = summaries[index]
        val matchesFilter = segments[index].matchesFilter(selection.filter)
        // This search is explicitly user-directed; display text never supplies a status.
        val matchesQuery = query.isEmpty() || summary.title.contains(query, ignoreCase = true) ||
            summary.id.value.contains(query, ignoreCase = true)
        summary.id.takeIf { matchesFilter && matchesQuery }
    }
    return DashboardConsoleModel(
        totalCount = summaries.size,
        readyCount = summaries.size - pendingCount,
        pendingCount = pendingCount,
        dangerCount = segments.count { it == DashboardConsoleSegment.DANGER },
        warningCount = segments.count { it == DashboardConsoleSegment.WARNING },
        limitedCount = segments.count { it == DashboardConsoleSegment.LIMITED },
        errorCount = segments.count { it == DashboardConsoleSegment.ERROR },
        contextCount = segments.count { it == DashboardConsoleSegment.CONTEXT },
        allClearCount = segments.count { it == DashboardConsoleSegment.CLEAR },
        otherInfoCount = segments.count { it == DashboardConsoleSegment.OTHER_INFO },
        visibleDetectorIds = visibleDetectorIds,
        segments = segments,
    )
}

private fun DetectorSummary.consoleSegment(): DashboardConsoleSegment {
    if (!ready) return DashboardConsoleSegment.PENDING
    return when (status.severity) {
        DetectionSeverity.DANGER -> DashboardConsoleSegment.DANGER
        DetectionSeverity.WARNING -> DashboardConsoleSegment.WARNING
        DetectionSeverity.ALL_CLEAR -> DashboardConsoleSegment.CLEAR
        DetectionSeverity.INFO -> when (status.infoKind) {
            InfoKind.ERROR -> DashboardConsoleSegment.ERROR
            InfoKind.SUPPORT -> DashboardConsoleSegment.LIMITED
            InfoKind.CONTEXT -> DashboardConsoleSegment.CONTEXT
            null -> DashboardConsoleSegment.OTHER_INFO
        }
    }
}

private fun DashboardConsoleSegment.matchesFilter(filter: DashboardConsoleFilter): Boolean = when (filter) {
    DashboardConsoleFilter.ALL -> true
    DashboardConsoleFilter.ATTENTION -> this == DashboardConsoleSegment.DANGER ||
        this == DashboardConsoleSegment.WARNING
    DashboardConsoleFilter.LIMITED -> this == DashboardConsoleSegment.LIMITED
    DashboardConsoleFilter.ERRORS -> this == DashboardConsoleSegment.ERROR
    DashboardConsoleFilter.OTHER -> this == DashboardConsoleSegment.CONTEXT ||
        this == DashboardConsoleSegment.CLEAR
}
