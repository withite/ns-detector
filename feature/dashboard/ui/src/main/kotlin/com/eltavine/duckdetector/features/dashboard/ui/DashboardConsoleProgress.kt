/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme

/** Only the rail interpolates; percentages, semantics and module outcomes expose actual evidence. */
@Composable
internal fun DashboardConsoleProgress(fraction: Float, colors: List<Color>, progressLabel: String) {
    val progress = remember { Animatable(fraction) }
    val track = DuckTheme.palette.groupedInset
    val fill = MaterialTheme.colorScheme.primary
    LaunchedEffect(fraction) {
        // New scans reset immediately. Compose's animation clock honors system duration scale,
        // including disabled animations; there is no timer or fabricated acquisition progress.
        if (fraction < progress.value) progress.snapTo(fraction)
        else progress.animateTo(fraction, tween(240, easing = FastOutSlowInEasing))
    }
    Canvas(
        Modifier.fillMaxWidth().height(21.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            stateDescription = progressLabel
        },
    ) {
        val railHeight = 5.dp.toPx()
        val railRadius = CornerRadius(railHeight / 2)
        drawRoundRect(track, size = Size(size.width, railHeight), cornerRadius = railRadius)
        // Read animated state during drawing, so frames do not recompose or remeasure the list.
        val displayed = progress.value.coerceIn(0f, fraction)
        if (displayed > 0f) {
            drawRoundRect(fill, size = Size(size.width * displayed, railHeight), cornerRadius = railRadius)
        }
        if (colors.isNotEmpty()) {
            val gap = 3.dp.toPx().coerceAtMost(size.width / (colors.size * 3))
            val width = (size.width - gap * (colors.size - 1)) / colors.size
            colors.forEachIndexed { index, color ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * (width + gap), 11.dp.toPx()),
                    size = Size(width, 10.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/** Leave enough width for localized labels; phones normally use two columns. */
internal fun dashboardConsoleColumns(availableWidthDp: Float, fontScale: Float): Int =
    ((availableWidthDp + 8f) / (152f * fontScale.coerceAtLeast(1f) + 8f)).toInt().coerceIn(1, 4)
