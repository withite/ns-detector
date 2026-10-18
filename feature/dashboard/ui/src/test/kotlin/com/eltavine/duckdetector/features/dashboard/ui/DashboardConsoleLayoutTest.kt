/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardConsoleLayoutTest {
    @Test
    fun `normal phone uses two readable metric columns`() {
        assertEquals(2, dashboardConsoleColumns(320f, 1f))
        assertEquals(2, dashboardConsoleColumns(360f, 1f))
    }

    @Test
    fun `narrow phone and large accessibility text use one column`() {
        assertEquals(1, dashboardConsoleColumns(260f, 1f))
        assertEquals(1, dashboardConsoleColumns(320f, 1.5f))
    }

    @Test
    fun `wide layouts add columns but retain font scaled minimum width`() {
        assertEquals(4, dashboardConsoleColumns(800f, 1f))
        assertEquals(3, dashboardConsoleColumns(800f, 1.5f))
        assertEquals(4, dashboardConsoleColumns(1600f, 1f))
    }
}
