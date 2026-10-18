/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashboardReportCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `prepared report survives a new cache instance and is removed after use`() {
        val folder = temporary.newFolder()
        val first = DashboardReportCache(folder)
        val name = first.newName(json = true)
        first.write(name, "{\"redacted\":true,\"evidence\":\"设备\"}")
        val restored = DashboardReportCache(folder)
        val output = ByteArrayOutputStream()
        restored.copyTo(name, output)
        assertEquals("{\"redacted\":true,\"evidence\":\"设备\"}", output.toString("UTF-8"))
        restored.delete(name)
        assertFalse(File(folder, name).exists())
        assertEquals(0, folder.listFiles().orEmpty().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `restored filename cannot escape the private cache`() {
        DashboardReportCache(temporary.newFolder()).copyTo("../sensitive.txt", ByteArrayOutputStream())
    }

    @Test
    fun `expiry removes abandoned owned files and preserves an active picker report`() {
        val folder = temporary.newFolder()
        val cache = DashboardReportCache(folder)
        val abandoned = cache.newName(json = false)
        val active = cache.newName(json = false)
        val fresh = cache.newName(json = true)
        cache.write(abandoned, "old redacted report")
        cache.write(active, "active redacted report")
        cache.write(fresh, "fresh redacted report")
        val unrelated = File(folder, "other_file").apply { writeText("retained") }
        val now = System.currentTimeMillis()
        assertTrue(File(folder, abandoned).setLastModified(now - 25L * 60 * 60 * 1_000))
        assertTrue(File(folder, active).setLastModified(now - 25L * 60 * 60 * 1_000))
        cache.cleanupExpired(now, active)
        assertFalse(File(folder, abandoned).exists())
        assertTrue(File(folder, active).exists())
        assertTrue(File(folder, fresh).exists())
        assertTrue(unrelated.exists())
    }
}
