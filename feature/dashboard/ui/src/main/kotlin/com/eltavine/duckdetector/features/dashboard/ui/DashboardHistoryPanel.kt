/* Modified for NS Detector: privacy-preserving local history and report exports. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.features.dashboard.presentation.history.DashboardHistoryChange
import com.eltavine.duckdetector.features.dashboard.presentation.history.DashboardHistoryChangeKind
import com.eltavine.duckdetector.features.dashboard.presentation.history.compareDashboardScans
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveTextButton
import io.github.xiaotong6666.uihelper.adaptive.adaptiveContainerContentColor
import io.github.xiaotong6666.uihelper.adaptive.adaptiveSecondaryTextColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Renders the controller state; storage is independent of this lazy item's composition. */
@Composable
internal fun DashboardHistoryPanel(state: DashboardHistoryState, modifier: Modifier = Modifier) {
    val history = state.scans
    var expanded by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<Long?>(null) }

    DuckPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WrapSafeText(
                    text = stringResource(R.string.dashboard_history_title),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = DuckTypography.PanelTitle,
                    color = adaptiveContainerContentColor(),
                )
                AdaptiveTextButton(
                    text = stringResource(R.string.dashboard_history_clear),
                    enabled = !state.clearing && (history.isNotEmpty() || state.storageFailed),
                    onClick = { selectedTime = null; state.onClear() },
                )
            }
            WrapSafeText(
                text = stringResource(R.string.dashboard_history_privacy),
                style = DuckTypography.PanelCaption,
                color = adaptiveSecondaryTextColor(),
            )
            if (state.storageFailed) {
                WrapSafeText(text = stringResource(R.string.dashboard_history_storage_failed), color = MaterialTheme.colorScheme.error)
            }
            if (history.isEmpty()) {
                WrapSafeText(text = stringResource(R.string.dashboard_history_empty), color = adaptiveSecondaryTextColor())
            } else {
                val selectedIndex = history.indexOfFirst { it.completedAtEpochMillis == selectedTime }.coerceAtLeast(0)
                val selected = history[selectedIndex]
                WrapSafeText(text = stringResource(R.string.dashboard_history_selected, formatHistoryTime(selected.completedAtEpochMillis)))
                if (selectedIndex + 1 < history.size) {
                    val changes = compareDashboardScans(history[selectedIndex + 1], selected)
                    DashboardHistoryChanges(changes)
                } else {
                    WrapSafeText(text = stringResource(R.string.dashboard_history_baseline), color = adaptiveSecondaryTextColor())
                }
                AdaptiveTextButton(
                    text = stringResource(if (expanded) R.string.dashboard_history_hide else R.string.dashboard_history_show, history.size),
                    onClick = { expanded = !expanded },
                )
                if (expanded) {
                    history.forEach { scan ->
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { selectedTime = scan.completedAtEpochMillis }.padding(vertical = 8.dp),
                        ) {
                            WrapSafeText(text = formatHistoryTime(scan.completedAtEpochMillis), style = DuckTypography.PanelBody)
                            WrapSafeText(
                                text = stringResource(R.string.dashboard_history_scan_details, scan.detectors.size, scan.durationMillis),
                                style = DuckTypography.PanelCaption,
                                color = adaptiveSecondaryTextColor(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHistoryChanges(changes: List<DashboardHistoryChange>) {
    if (changes.isEmpty()) {
        WrapSafeText(text = stringResource(R.string.dashboard_history_unchanged), color = adaptiveSecondaryTextColor())
    } else {
        changes.forEach { change ->
            val label = when (change.kind) {
                DashboardHistoryChangeKind.NEW_FINDING -> R.string.dashboard_history_new_finding
                DashboardHistoryChangeKind.RESOLVED_FINDING -> R.string.dashboard_history_resolved_finding
                DashboardHistoryChangeKind.STATUS_CHANGED -> R.string.dashboard_history_changed
                DashboardHistoryChangeKind.MODULE_ADDED -> R.string.dashboard_history_module_added
                DashboardHistoryChangeKind.MODULE_REMOVED -> R.string.dashboard_history_module_removed
            }
            WrapSafeText(
                text = stringResource(label, change.detectorId, change.current?.headline ?: change.previous?.headline.orEmpty()),
                style = DuckTypography.PanelCaption,
                color = adaptiveContainerContentColor(),
            )
        }
    }
}

private fun formatHistoryTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
