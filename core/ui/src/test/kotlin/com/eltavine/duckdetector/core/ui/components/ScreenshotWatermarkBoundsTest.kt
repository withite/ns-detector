/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotWatermarkBoundsTest {
    @Test
    fun `unrotated watermark covers only original viewport`() {
        assertEquals(ScreenshotWatermarkBounds(0f, 0f, 400f, 800f), screenshotWatermarkBounds(400f, 800f, 0f))
    }

    @Test
    fun `quarter turn swaps extents around original viewport center`() {
        assertEquals(ScreenshotWatermarkBounds(-200f, 200f, 600f, 600f), screenshotWatermarkBounds(400f, 800f, 90f))
    }

    @Test
    fun `negative rotation preserves same coverage as positive rotation`() {
        val positive = screenshotWatermarkBounds(400f, 800f, 30f)
        val negative = screenshotWatermarkBounds(400f, 800f, -30f)
        assertEquals(positive, negative)
        assertEquals(-173.20508f, negative.left, 0.001f)
        assertEquals(-46.41016f, negative.top, 0.001f)
        assertEquals(573.20508f, negative.right, 0.001f)
        assertEquals(846.41016f, negative.bottom, 0.001f)
    }
}
