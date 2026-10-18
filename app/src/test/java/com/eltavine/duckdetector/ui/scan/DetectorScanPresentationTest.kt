/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0.
 */

package com.eltavine.duckdetector.ui.scan

import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.scan.DetectorSummary
import com.eltavine.duckdetector.core.scan.ScanState
import com.eltavine.duckdetector.core.scan.ScanTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectorScanPresentationTest {
    @Test
    fun `subsequent projection runs on the supplied dispatcher and keeps completion atomic`() =
        runTest(UnconfinedTestDispatcher()) {
            val initial = scan(ready = false)
            val scans = MutableStateFlow(initial)
            val presentation = scans.presentScans(backgroundScope, StandardTestDispatcher(testScheduler))
            val completed = scan(ready = true)

            assertSame(initial, presentation.value.scan)
            scans.value = completed
            // The collecting scope is immediate; projection must still await its worker dispatcher.
            assertSame(initial, presentation.value.scan)
            runCurrent()

            val rendered = presentation.value
            assertSame(completed, rendered.scan)
            assertSame(completed.detectors, rendered.dashboard.detectorSummaries)
            assertEquals(rendered.scan.isLoading, rendered.dashboard.isLoading)
            assertFalse(rendered.dashboard.isLoading)
            assertEquals(500L, rendered.dashboard.overview.scanDurationMillis)
            assertEquals(1_700_000_000_000L, rendered.dashboard.overview.scanCompletedAtEpochMillis)
            assertTrue(rendered.dashboard.overview.titleDescribesCompletedScan)
        }

    @Test
    fun `a burst retains final evidence and stable detector identities`() = runTest(UnconfinedTestDispatcher()) {
        val scans = MutableStateFlow(scan(ready = false))
        val presentation = scans.presentScans(backgroundScope, StandardTestDispatcher(testScheduler))
        repeat(100) { index -> scans.value = scan(ready = false, headline = "Stage $index") }
        val completed = scan(ready = true, headline = "Final evidence")
        scans.value = completed

        runCurrent()

        assertEquals(completed, presentation.value.scan)
        assertEquals(listOf(DetectorId("probe")), presentation.value.dashboard.cardOrder)
        assertEquals("Final evidence", presentation.value.dashboard.detectorSummaries.single().headline)
    }

    @Test
    fun `ending the presentation owner stops collection`() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val scans = MutableStateFlow(scan(ready = false))
        val presentation = scans.presentScans(scope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val previous = presentation.value

        scope.cancel()
        runCurrent()
        scans.value = scan(ready = true)
        runCurrent()

        assertSame(previous, presentation.value)
    }

    private fun scan(ready: Boolean, headline: String = "Evidence") = ScanState(
        detectors = listOf(
            DetectorSummary(
                id = DetectorId("probe"),
                title = "Probe",
                status = DetectorStatus.allClear(),
                headline = headline,
                summary = "Details",
                ready = ready,
            ),
        ),
        timeline = ScanTimeline(
            session = 1,
            startedAtElapsedMillis = 100L,
            finishedAtElapsedMillis = if (ready) 600L else null,
            completedAtEpochMillis = if (ready) 1_700_000_000_000L else null,
        ),
    )
}
