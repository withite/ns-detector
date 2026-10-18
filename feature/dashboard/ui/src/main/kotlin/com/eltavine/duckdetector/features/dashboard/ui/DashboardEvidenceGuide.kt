/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.DuckIcon
import com.eltavine.duckdetector.core.ui.components.WrapSafeText

/** Explains the scope of the report without adding or reinterpreting detector verdicts. */
@Composable
internal fun DashboardEvidenceGuide() {
    DuckPanel(contentPadding = PaddingValues(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DuckIcon(
                imageVector = Icons.Outlined.Policy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WrapSafeText(
                    text = stringResource(R.string.dashboard_scope_title),
                    modifier = Modifier.semantics { heading() },
                    style = DuckTypography.PanelTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WrapSafeText(
                    text = stringResource(R.string.dashboard_scope_detail),
                    style = DuckTypography.PanelSupporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DashboardSectionHeading(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WrapSafeText(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = DuckTypography.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        WrapSafeText(
            text = detail,
            style = DuckTypography.PanelCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
