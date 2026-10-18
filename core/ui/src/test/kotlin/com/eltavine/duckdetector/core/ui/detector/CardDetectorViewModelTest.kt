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
 */

package com.eltavine.duckdetector.core.ui.detector

import android.content.Context
import com.eltavine.duckdetector.core.detector.Detector
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.report.DetectorHeadline
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.core.scan.DetectorSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CancellationException
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetectorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the loading card until the first scan finishes`() = runTest(dispatcher) {
        val scanner = GatedScanner()
        val viewModel = CardDetectorViewModel(ProbeDetector, scanner, dispatcher)

        assertEquals(CardDetectorState(ready = false, cardModel = Card(0)), viewModel.state.value)
        assertEquals(summary(verdict = "report 0", ready = false), viewModel.summary.value)

        scanner.finish(7)

        assertEquals(CardDetectorState(ready = true, cardModel = Card(7)), viewModel.state.value)
        assertEquals(summary(verdict = "report 7", ready = true), viewModel.summary.value)
    }

    @Test
    fun `a rescan shows the loading card again and then publishes the new report`() = runTest(dispatcher) {
        val scanner = GatedScanner()
        val viewModel = CardDetectorViewModel(ProbeDetector, scanner, dispatcher)
        scanner.finish(1)

        viewModel.rescan()
        assertEquals(CardDetectorState(ready = false, cardModel = Card(0)), viewModel.state.value)

        scanner.finish(2)
        assertEquals(CardDetectorState(ready = true, cardModel = Card(2)), viewModel.state.value)
        assertEquals(2, scanner.scans)
    }

    @Test
    fun `collection failure is a finished error and retry can recover`() = runTest(dispatcher) {
        var fail = true
        val vm = CardDetectorViewModel(ProbeDetector, DetectorScanner {
            if (fail) error("unavailable platform service") else 9
        }, dispatcher)
        assertEquals(CardScanStage.COLLECTION, vm.state.value.failure?.stage)
        assertEquals(DetectorStatus.info(InfoKind.ERROR), vm.summary.value.status)
        assertTrue(vm.summary.value.ready)
        assertEquals("Scan failed", vm.report().verdict)
        fail = false
        vm.rescan()
        assertNull(vm.state.value.failure)
        assertEquals(Card(9), vm.state.value.cardModel)
    }

    @Test
    fun `presentation failure never exposes the loading model as a clean report`() = runTest(dispatcher) {
        val detector = object : Detector<Int, Card> by ProbeDetector {
            override fun describe(report: Int): Card {
                if (report != 0) throw IllegalArgumentException("invalid payload")
                return Card(0)
            }
        }
        val vm = CardDetectorViewModel(detector, DetectorScanner { 1 }, dispatcher)
        assertEquals(CardScanStage.PRESENTATION, vm.state.value.failure?.stage)
        assertTrue(vm.state.value.ready)
        assertEquals(DetectorStatus.info(InfoKind.ERROR), vm.summary.value.status)
        assertEquals("Scan failed", vm.report().verdict)
    }

    @Test
    fun `owner cancellation is not turned into an execution error`() = runTest(dispatcher) {
        val vm = CardDetectorViewModel(ProbeDetector, DetectorScanner { throw CancellationException() }, dispatcher)
        assertFalse(vm.state.value.ready)
        assertNull(vm.state.value.failure)
    }

    private fun summary(verdict: String, ready: Boolean) = DetectorSummary(
        id = DetectorId("probe"),
        title = "Probe",
        status = DetectorStatus.info(InfoKind.SUPPORT),
        headline = verdict,
        summary = "summary",
        ready = ready,
    )

    private data class Card(val report: Int) : DetectorHeadline {
        override val title = "Probe"
        override val status = DetectorStatus.info(InfoKind.SUPPORT)
        override val verdict = "report $report"
        override val summary = "summary"
    }

    private object ProbeDetector : Detector<Int, Card> {
        override val id = DetectorId("probe")

        override fun createScanner(context: Context): DetectorScanner<Int> = error("tests pass the scanner")

        override fun loadingReport(): Int = 0

        override fun describe(report: Int): Card = Card(report)

        override fun export(model: Card): DetectorReport = error("not exported here")
    }

    /** A scanner whose scans finish only when the test says so. */
    private class GatedScanner : DetectorScanner<Int> {
        private var pending = CompletableDeferred<Int>()
        var scans = 0

        override suspend fun scan(): Int {
            scans++
            return pending.await().also { pending = CompletableDeferred() }
        }

        fun finish(report: Int) {
            pending.complete(report)
        }
    }
}
