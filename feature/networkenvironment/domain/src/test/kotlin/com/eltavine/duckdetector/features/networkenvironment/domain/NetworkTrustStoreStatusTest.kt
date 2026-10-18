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

package com.eltavine.duckdetector.features.networkenvironment.domain

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTrustStoreStatusTest {
    @Test
    fun `positive and zero user or system counts are neutral context`() {
        for (user in listOf(0, 1, 25)) for (system in listOf(0, 1, 150)) {
            val report = observed(NetworkTrustStoreObservation(
                TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(user, system),
            ))
            assertEquals(DetectorStatus.info(InfoKind.CONTEXT), report.toDetectorStatus())
            assertTrue(report.probed)
            assertFalse(report.partiallyEvaluated)
        }
    }

    @Test
    fun `incomplete store enumeration is support and failure is error information`() {
        for (state in TrustStoreObservationState.entries.filter { it != TrustStoreObservationState.OBSERVED }) {
            val counts = when (state) {
                TrustStoreObservationState.TRUNCATED -> NetworkTrustStoreCounts(2, 4)
                TrustStoreObservationState.UNRECOGNIZED_ALIASES -> NetworkTrustStoreCounts(2, 4, 1)
                else -> null
            }
            val report = observed(NetworkTrustStoreObservation(state, counts))
            val kind = if (state == TrustStoreObservationState.FAILED) InfoKind.ERROR else InfoKind.SUPPORT
            assertEquals(DetectorStatus.info(kind), report.toDetectorStatus())
            assertTrue(report.partiallyEvaluated)
        }
    }

    @Test
    fun `counts require a matching observation state and valid bounded arithmetic`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustStoreObservation(TrustStoreObservationState.OBSERVED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustStoreObservation(TrustStoreObservationState.PROVIDER_UNAVAILABLE, NetworkTrustStoreCounts(0, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustStoreObservation(TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(0, 1, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustStoreObservation(TrustStoreObservationState.UNRECOGNIZED_ALIASES, NetworkTrustStoreCounts(0, 1))
        }
        assertThrows(IllegalArgumentException::class.java) { NetworkTrustStoreCounts(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { NetworkTrustStoreCounts(Int.MAX_VALUE, 1) }
    }

    private fun observed(trustStore: NetworkTrustStoreObservation) = NetworkEnvironmentReport(
        NetworkEnvironmentStage.READY,
        NetworkDefaultSnapshot(
            NetworkVpnObservation(NetworkObservationState.OBSERVED, false),
            NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, NetworkPrivateDnsMode.INACTIVE),
        ),
        NetworkProxyObservation(NetworkObservationState.OBSERVED, NetworkProxyMode.NONE),
        trustStore = trustStore,
    )
}
