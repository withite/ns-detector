/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.core.ui.components

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class ScreenshotWatermarkBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Bounds of the viewport before the watermark's center rotation, without diagonal overscan. */
internal fun screenshotWatermarkBounds(width: Float, height: Float, rotationDegrees: Float): ScreenshotWatermarkBounds {
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val rotatedWidth = (width * abs(cos(radians)) + height * abs(sin(radians))).toFloat()
    val rotatedHeight = (width * abs(sin(radians)) + height * abs(cos(radians))).toFloat()
    return ScreenshotWatermarkBounds(
        left = (width - rotatedWidth) / 2,
        top = (height - rotatedHeight) / 2,
        right = (width + rotatedWidth) / 2,
        bottom = (height + rotatedHeight) / 2,
    )
}
