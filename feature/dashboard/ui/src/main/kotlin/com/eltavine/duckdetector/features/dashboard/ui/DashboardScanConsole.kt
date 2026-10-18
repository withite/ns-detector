/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.ui.components.WrapSafeText

/** Determinate acquisition progress. It never presents a security score or guessed elapsed progress. */
@Composable
internal fun DashboardScanConsole(model: DashboardConsoleModel, isLoading: Boolean) {
    val palette = DuckTheme.palette
    val scheme = MaterialTheme.colorScheme
    val progressLabel = stringResource(R.string.dashboard_console_progress, model.readyCount, model.totalCount)
    val colors = remember(model.segments, palette, scheme.primary) {
        model.segments.map { segment ->
            when (segment) {
                DashboardConsoleSegment.PENDING -> palette.groupedInset
                DashboardConsoleSegment.DANGER, DashboardConsoleSegment.ERROR -> palette.critical
                DashboardConsoleSegment.WARNING -> palette.caution
                DashboardConsoleSegment.CLEAR -> palette.positive
                DashboardConsoleSegment.CONTEXT -> scheme.primary.copy(alpha = 0.65f)
                DashboardConsoleSegment.LIMITED, DashboardConsoleSegment.OTHER_INFO -> palette.neutral
            }
        }
    }
    DuckPanel(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_eyebrow),
                    style = DuckTypography.PanelCaption,
                    color = scheme.onSurfaceVariant,
                )
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_title),
                    modifier = Modifier.semantics { heading() },
                    style = DuckTypography.StatusHeadline,
                    color = scheme.onSurface,
                )
            }
            ConsolePhase(isLoading = isLoading)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WrapSafeText(
                text = stringResource(R.string.dashboard_console_percentage, (model.completedFraction * 100).toInt()),
                style = DuckTypography.LargeTitle.copy(
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.onSurface,
            )
            Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                WrapSafeText(text = progressLabel, style = DuckTypography.PanelBody, color = scheme.onSurface)
                WrapSafeText(
                    text = stringResource(R.string.dashboard_console_progress_scope),
                    style = DuckTypography.PanelCaption,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        DashboardConsoleProgress(model.completedFraction, colors, progressLabel)
        ConsoleDistribution(model)
    }
}

@Composable
private fun ConsolePhase(isLoading: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.background(DuckTheme.palette.groupedInset, CircleShape).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(scheme.primary, CircleShape))
        WrapSafeText(
            text = stringResource(if (isLoading) R.string.dashboard_console_collecting else R.string.dashboard_console_returned),
            style = DuckTypography.PanelCaption,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun ConsoleDistribution(model: DashboardConsoleModel) {
    val palette = DuckTheme.palette
    val stats = listOf(
        ConsoleStat(R.string.dashboard_console_attention, model.attentionCount, palette.critical),
        ConsoleStat(R.string.dashboard_console_limited, model.limitedCount, palette.neutral),
        ConsoleStat(R.string.dashboard_console_errors, model.errorCount, palette.critical),
        ConsoleStat(R.string.dashboard_console_observed, model.otherCount + model.otherInfoCount, MaterialTheme.colorScheme.primary),
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = dashboardConsoleColumns(maxWidth.value, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { stat -> ConsoleDistributionStat(stat, Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class ConsoleStat(val label: Int, val count: Int, val color: Color)

@Composable
private fun ConsoleDistributionStat(stat: ConsoleStat, modifier: Modifier) {
    Column(
        modifier = modifier.background(DuckTheme.palette.groupedInset, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        WrapSafeText(text = stat.count.toString(), style = DuckTypography.MetricNumeral, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.dp).background(stat.color, CircleShape))
            androidx.compose.material3.Text(
                text = stringResource(stat.label),
                style = DuckTypography.PanelCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}
