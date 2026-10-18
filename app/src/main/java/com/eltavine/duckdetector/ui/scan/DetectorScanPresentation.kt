/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0.
 */

package com.eltavine.duckdetector.ui.scan

import com.eltavine.duckdetector.core.scan.ScanState
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState
import com.eltavine.duckdetector.features.dashboard.presentation.model.buildDashboardFindings
import com.eltavine.duckdetector.features.dashboard.presentation.model.buildDashboardOverview
import com.eltavine.duckdetector.features.dashboard.presentation.model.dashboardCardOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Progress, notifications and dashboard always render the same collected scan snapshot. */
internal data class DetectorScanPresentation(
    val scan: ScanState,
    val dashboard: DashboardUiState,
)

internal fun StateFlow<ScanState>.presentScans(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
): StateFlow<DetectorScanPresentation> = map { it.presentation() }
    // Sorting findings, building overview metrics and formatting completion times belong to the
    // scan owner, rather than the shell's main-thread composition. No debounce delays completion.
    .flowOn(dispatcher)
    .stateIn(scope, SharingStarted.Eagerly, value.presentation())

private fun ScanState.presentation(): DetectorScanPresentation = DetectorScanPresentation(
    scan = this,
    dashboard = DashboardUiState(
        overview = buildDashboardOverview(
            contributions = detectors,
            scanDurationMillis = timeline.durationMillis,
            scanCompletedAtEpochMillis = timeline.completedAtEpochMillis,
        ),
        topFindings = buildDashboardFindings(detectors),
        cardOrder = dashboardCardOrder(detectors),
        isLoading = isLoading,
        detectorSummaries = detectors,
    ),
)
