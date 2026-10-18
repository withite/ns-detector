/*
 * Copyright 2026 Duck Apps Contributor
 * If you have any questions, suggestions, or other inquiries, please email Eltavine <me@eltavine.com>.
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
 * Modified 2026 by NS Detector contributors: evidence guidance and independent report naming.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.StatusBarProtection
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.ui.components.LocalDetectorCardExpansionSignal
import com.eltavine.duckdetector.core.ui.detector.DetectorSession
import com.eltavine.duckdetector.core.ui.detector.DeviceProfileSession
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardFindingModel
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState
import io.github.xiaotong6666.uihelper.adaptive.adaptiveScrollableOverscrollEffect
import io.github.xiaotong6666.uihelper.adaptive.adaptiveValue
import io.github.xiaotong6666.uihelper.adaptive.adaptiveVerticalScrollFeedback
import io.github.xiaotong6666.uihelper.adaptive.adaptiveViewportOverscroll

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    detectors: List<DetectorSession>,
    deviceProfile: DeviceProfileSession,
    modifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues? = null,
    pageModifier: Modifier = Modifier,
) {
    val materialOverscrollEffect = rememberOverscrollEffect()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(DashboardConsoleFilter.ALL.name) }
    var compact by rememberSaveable { mutableStateOf(true) }
    var selectedDetectorId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedDetectorId by rememberSaveable { mutableStateOf<String?>(null) }
    var expansionCounter by rememberSaveable { mutableLongStateOf(0L) }
    val selectedFilter = DashboardConsoleFilter.entries.firstOrNull { it.name == filterName } ?: DashboardConsoleFilter.ALL
    val sessionsById = remember(detectors) { detectors.associateBy { it.id } }
    val summaries = remember(uiState.detectorSummaries, detectors) {
        val published = uiState.detectorSummaries.associateBy { it.id }
        // Use catalog order throughout a scan so arriving evidence never moves a module under a finger.
        detectors.map { published[it.id] ?: it.summary.value }
    }
    val summariesById = remember(summaries) { summaries.associateBy { it.id } }
    val console = remember(summaries, selectedFilter, query) {
        buildDashboardConsoleModel(summaries, DashboardConsoleSelection(selectedFilter, query))
    }
    val visibleDetectors = remember(console.visibleDetectorIds, sessionsById) {
        console.visibleDetectorIds.mapNotNull(sessionsById::get)
    }
    // Filters change browsing only; exports and history always consume the complete scan.
    val exportReport = rememberDashboardReportExporter(uiState, detectors, deviceProfile)
    val historyState = rememberDashboardHistory(uiState)
    val hostedHorizontalInset = adaptiveValue(material = 16.dp, miuix = 12.dp)
    val pageSpacing = adaptiveValue(material = 16.dp, miuix = 12.dp)
    val showStandaloneMaterialChrome = adaptiveValue(material = scaffoldPadding == null, miuix = false)
    val layoutDirection = LocalLayoutDirection.current
    val resetFilters: () -> Unit = { query = ""; filterName = DashboardConsoleFilter.ALL.name }
    val onFindingClick: (DashboardFindingModel) -> Unit = { finding ->
        val detectorId = finding.detectorId
        val detectorIndex = detectors.indexOfFirst { it.id == detectorId }
        if (detectorId != null && detectorIndex >= 0) {
            resetFilters()
            selectedDetectorId = detectorId.value
            requestedDetectorId = detectorId.value
            expansionCounter += 1L
            // requestScrollToItem applies to the next measurement, after clearing the filters.
            val moduleStartIndex = if (showStandaloneMaterialChrome) 4 else 3
            listState.requestScrollToItem(moduleStartIndex + detectorIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(DuckTheme.palette.groupedBackground)
            .adaptiveViewportOverscroll(materialOverscrollEffect),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().align(Alignment.TopCenter)
                .adaptiveVerticalScrollFeedback().then(pageModifier),
            overscrollEffect = adaptiveScrollableOverscrollEffect(materialOverscrollEffect),
            verticalArrangement = Arrangement.spacedBy(pageSpacing),
            contentPadding = if (scaffoldPadding != null) {
                PaddingValues(
                    start = scaffoldPadding.calculateStartPadding(layoutDirection) + hostedHorizontalInset,
                    top = scaffoldPadding.calculateTopPadding() + 12.dp,
                    end = scaffoldPadding.calculateEndPadding(layoutDirection) + hostedHorizontalInset,
                    bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
                )
            } else {
                WindowInsets.safeDrawing
                    .add(WindowInsets(left = 16.dp, top = 12.dp, right = 16.dp, bottom = 96.dp)).asPaddingValues()
            },
        ) {
            if (showStandaloneMaterialChrome) item(key = "brand") { BrandHeader() }
            item(key = "console") { DashboardScanConsole(console, uiState.isLoading) }
            item(key = "scan_outcome") {
                if (uiState.isLoading) DashboardCollectingNotice()
                else DashboardOverviewCard(uiState.overview, exportReport)
            }
            item(key = "browser") {
                DashboardBrowserControls(
                    query = query,
                    onQueryChange = { query = it },
                    filter = selectedFilter,
                    onFilterChange = { filterName = it.name },
                    visibleCount = visibleDetectors.size,
                    totalCount = console.totalCount,
                    compact = compact,
                    onCompactChange = { compact = it },
                )
            }
            if (visibleDetectors.isEmpty()) {
                item(key = "no_modules") { DashboardBrowserEmptyState(onReset = resetFilters) }
            }
            items(items = visibleDetectors, key = { "module:${it.id.value}" }, contentType = { "detector_module" }) { detector ->
                val expansionSignal = if (requestedDetectorId == detector.id.value) expansionCounter else 0L
                if (compact) {
                    DashboardCompactModule(
                        summary = requireNotNull(summariesById[detector.id]),
                        expanded = selectedDetectorId == detector.id.value,
                        onToggle = {
                            if (selectedDetectorId == detector.id.value) selectedDetectorId = null
                            else {
                                selectedDetectorId = detector.id.value
                                requestedDetectorId = detector.id.value
                                expansionCounter += 1L
                            }
                        },
                    ) { DashboardDetectorDetails(detector, expansionSignal) }
                } else {
                    DashboardDetectorDetails(detector, expansionSignal)
                }
            }
            item(key = "findings") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardSectionHeading(
                        title = stringResource(R.string.dashboard_findings_title),
                        detail = stringResource(R.string.dashboard_findings_hint),
                    )
                    DashboardFindingsCard(uiState.topFindings, onFindingClick)
                }
            }
            item(key = "history") { DashboardHistoryPanel(state = historyState) }
            item(key = "scope") { DashboardEvidenceGuide() }
            item(key = "device_profile") { deviceProfile.Card() }
        }
        if (showStandaloneMaterialChrome) StatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun DashboardDetectorDetails(detector: DetectorSession, expansionSignal: Long) {
    CompositionLocalProvider(LocalDetectorCardExpansionSignal provides expansionSignal) { detector.Card() }
}
