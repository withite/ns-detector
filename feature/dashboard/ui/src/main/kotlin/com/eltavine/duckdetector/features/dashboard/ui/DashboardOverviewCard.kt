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
 * Modified 2026 by NS Detector contributors: responsive evidence metrics and accessibility.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.DuckIcon
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.core.ui.presentation.rememberStatusAppearance
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardOverviewModel
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveFilledIconButton

/** A concise completed-scan conclusion; the console owns acquisition progress and distribution. */
@Composable
internal fun DashboardOverviewCard(model: DashboardOverviewModel, onExportReport: () -> Unit) {
    val appearance = rememberStatusAppearance(model.status)
    val scheme = MaterialTheme.colorScheme
    DuckPanel(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(appearance.tintWash, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DuckIcon(appearance.icon, null, Modifier.size(24.dp), tint = appearance.iconTint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_result_title),
                    style = DuckTypography.PanelCaption,
                    color = scheme.onSurfaceVariant,
                )
                WrapSafeText(
                    text = dashboardOverviewHeadline(model),
                    modifier = Modifier.semantics { heading() },
                    style = DuckTypography.PanelTitle,
                    color = scheme.onSurface,
                )
            }
            AdaptiveFilledIconButton(
                onClick = onExportReport,
                materialContainerColor = DuckTheme.palette.groupedInset,
                materialContentColor = scheme.onSurface,
                miuixContainerColor = DuckTheme.palette.groupedInset,
                miuixContentColor = scheme.onSurface,
            ) {
                DuckIcon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = stringResource(R.string.dashboard_export_report_hint),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        WrapSafeText(
            text = dashboardOverviewSummary(model),
            style = DuckTypography.PanelSupporting,
            color = scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuckIcon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            WrapSafeText(
                text = dashboardOverviewTitle(model).replace('\n', ' '),
                style = DuckTypography.PanelCaption,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
