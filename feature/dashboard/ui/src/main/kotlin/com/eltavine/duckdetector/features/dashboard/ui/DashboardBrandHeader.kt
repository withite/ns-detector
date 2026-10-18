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
 * Modified 2026 by NS Detector contributors: independent branding and project navigation.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.eltavine.duckdetector.core.ui.components.DuckIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.core.ui.openExternalUri
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveContent
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveFilledIconButton
import io.github.xiaotong6666.uihelper.adaptive.adaptiveOnSurfaceColor
import io.github.xiaotong6666.uihelper.adaptive.adaptiveValue
import androidx.compose.ui.graphics.Color
import com.eltavine.duckdetector.core.ui.R as CoreUiR

/** Standalone dashboard fallback; hosted pages place branding in their native top bars. */
@Composable
internal fun BrandHeader(showTitle: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialAppIcon()
            Spacer(modifier = Modifier.weight(1f))
            DashboardProjectAction()
        }
        if (showTitle) {
            WrapSafeText(
                text = stringResource(CoreUiR.string.app_name),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .semantics { heading() },
                style = DuckTypography.PageTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// A single-color mark remains legible in both native theme implementations.
@Composable
private fun MaterialAppIcon(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(size)
            // Preserve the original 56dp/14dp tile proportions in the compact bar.
            .background(color = DuckTheme.palette.groupedSurface, shape = RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        DuckIcon(
            painter = painterResource(R.drawable.ic_ns_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * (34f / 56f)),
        )
    }
}

/** App-owned top bar mark with a compact glyph for MIUIX. */
@Composable
public fun DashboardTopBarBrandIcon() {
    AdaptiveContent(
        miuix = {
            DuckIcon(
                painter = painterResource(R.drawable.ic_ns_mark),
                contentDescription = null,
                tint = adaptiveOnSurfaceColor(),
                modifier = Modifier.size(30.dp),
            )
        },
        material = { MaterialAppIcon(size = 40.dp) },
    )
}

/** Kept for older shell integrations; the independent project action opens this fork. */
@Composable
public fun DashboardTelegramAction() = DashboardProjectAction()

@Composable
public fun DashboardProjectAction() {
    val context = LocalContext.current
    val onClick: () -> Unit = { openExternalUri(context, "https://github.com/withite/ns-detector"); Unit }
    AdaptiveFilledIconButton(
        onClick = onClick,
        materialContainerColor = DuckTheme.palette.groupedSurface,
        materialContentColor = MaterialTheme.colorScheme.primary,
        miuixContainerColor = Color.Transparent,
        miuixContentColor = adaptiveOnSurfaceColor(),
    ) {
        DuckIcon(
            imageVector = Icons.Rounded.Code,
            contentDescription = stringResource(R.string.dashboard_project_source),
            modifier = Modifier.size(adaptiveValue(material = 20.dp, miuix = 26.dp)),
        )
    }
}
