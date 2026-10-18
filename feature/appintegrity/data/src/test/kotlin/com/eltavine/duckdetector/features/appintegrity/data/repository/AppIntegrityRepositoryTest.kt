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

package com.eltavine.duckdetector.features.appintegrity.data.repository

import com.eltavine.duckdetector.features.appintegrity.domain.*
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

class AppIntegrityRepositoryTest {
    private open class Probe : AppIntegrityProbe {
        override fun build() = AppObservation.observed(AppBuildSnapshot("com.example.host", false, false))
        override fun signing() = AppObservation.observed(AppSigningSnapshot(setOf("A".repeat(64)), false, 1))
        override fun policy(): AppObservation<AppSigningPolicy> = AppObservation(AppObservationState.MISSING)
        override fun debugger() = AppObservation.observed(AppDebuggerSnapshot(false, false))
        override fun tracerPid() = AppObservation.observed(0)
    }

    @Test fun `signature failure does not discard runtime evidence or become matched`() {
        val report = AppIntegrityRepository(object : Probe() {
            override fun signing(): AppObservation<AppSigningSnapshot> = throw IOException("private filesystem path")
        }).collect()
        assertEquals(AppObservationState.FAILED, report.signing.state)
        assertEquals("IOException", report.signing.failureName)
        assertEquals(0, report.tracerPid.value)
        assertEquals(false, report.debugger.value?.attached)
        assertEquals(AppObservationState.MISSING, report.policy.state)
        assertEquals(AppSignatureAssessment.ERROR, report.signatureAssessment())
    }

    @Test fun `security denial yields unavailable rather than false debugger value`() {
        val report = AppIntegrityRepository(object : Probe() {
            override fun debugger(): AppObservation<AppDebuggerSnapshot> = throw SecurityException()
        }).collect()
        assertEquals(AppObservationState.UNAVAILABLE, report.debugger.state)
        assertNull(report.debugger.value)
        assertEquals(AppObservationState.OBSERVED, report.signing.state)
    }

    @Test fun `cancellation propagates without becoming an error report`() {
        assertThrows(CancellationException::class.java) {
            AppIntegrityRepository(object : Probe() {
                override fun tracerPid(): AppObservation<Int> = throw CancellationException("cancelled")
            }).collect()
        }
    }
}
