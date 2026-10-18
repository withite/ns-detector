/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.DuckIcon
import com.eltavine.duckdetector.core.ui.components.WrapSafeText

/** Stable explanation beside determinate progress; no perpetual loading animation. */
@Composable
internal fun DashboardCollectingNotice() {
    DuckPanel(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DuckIcon(Icons.Outlined.Sensors, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_result_pending),
                    style = DuckTypography.PanelTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_result_pending_detail),
                    style = DuckTypography.PanelSupporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
