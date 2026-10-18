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

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvironmentReportStatusTest {
    @Test
    fun `every VPN proxy and DNS configuration remains informational`() {
        for (vpn in listOf(true, false)) {
            for (proxy in NetworkProxyMode.entries) {
                for (dns in NetworkPrivateDnsMode.entries) {
                    val report = NetworkEnvironmentReport(
                        stage = NetworkEnvironmentStage.READY,
                        network = NetworkDefaultSnapshot(
                            NetworkVpnObservation(NetworkObservationState.OBSERVED, vpn),
                            NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, dns),
                        ),
                        proxy = NetworkProxyObservation(NetworkObservationState.OBSERVED, proxy),
                        trustStore = NetworkTrustStoreObservation(TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(0, 144)),
                    )
                    assertEquals(DetectorStatus.info(InfoKind.CONTEXT), report.toDetectorStatus())
                    assertTrue(report.probed)
                    assertFalse(report.partiallyEvaluated)
                }
            }
        }
    }

    @Test
    fun `missing evidence never becomes a clean verdict`() {
        for (state in NetworkObservationState.entries.filter { it != NetworkObservationState.OBSERVED }) {
            val report = NetworkEnvironmentReport(
                stage = NetworkEnvironmentStage.READY,
                network = NetworkDefaultSnapshot.unavailable(state),
                proxy = NetworkProxyObservation(state),
            )
            assertEquals(DetectionSeverity.INFO, report.toDetectorStatus().severity)
            assertEquals(
                if (state == NetworkObservationState.FAILED) InfoKind.ERROR else InfoKind.SUPPORT,
                report.toDetectorStatus().infoKind,
            )
            assertFalse(report.probed)
        }
    }

    @Test
    fun `one missing source keeps an otherwise observed snapshot coverage limited`() {
        val observedNetwork = NetworkDefaultSnapshot(
            NetworkVpnObservation(NetworkObservationState.OBSERVED, false),
            NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, NetworkPrivateDnsMode.INACTIVE),
        )
        for (state in NetworkObservationState.entries.filter { it != NetworkObservationState.OBSERVED }) {
            val report = NetworkEnvironmentReport(
                stage = NetworkEnvironmentStage.READY,
                network = observedNetwork,
                proxy = NetworkProxyObservation(state),
            )
            assertTrue(report.partiallyEvaluated)
            assertEquals(
                if (state == NetworkObservationState.FAILED) InfoKind.ERROR else InfoKind.SUPPORT,
                report.toDetectorStatus().infoKind,
            )
        }
    }

    @Test
    fun `partial observations remain explicit and failures are error information`() {
        val report = NetworkEnvironmentReport(
            stage = NetworkEnvironmentStage.READY,
            network = NetworkDefaultSnapshot.unavailable(NetworkObservationState.NO_DEFAULT_NETWORK),
            proxy = NetworkProxyObservation(NetworkObservationState.OBSERVED, NetworkProxyMode.NONE),
        )
        assertTrue(report.probed)
        assertTrue(report.partiallyEvaluated)
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
        val failed = report.copy(network = NetworkDefaultSnapshot.unavailable(NetworkObservationState.FAILED))
        assertEquals(DetectorStatus.info(InfoKind.ERROR), failed.toDetectorStatus())
    }

    @Test
    fun `loading and failed scans never imply a configuration value`() {
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), NetworkEnvironmentReport.loading().toDetectorStatus())
        assertEquals(DetectorStatus.info(InfoKind.ERROR), NetworkEnvironmentReport.failed("IOException").toDetectorStatus())
    }

    @Test
    fun `an observed value is required and unavailable values cannot masquerade as observed`() {
        assertThrows(IllegalArgumentException::class.java) { NetworkVpnObservation(NetworkObservationState.OBSERVED) }
        assertThrows(IllegalArgumentException::class.java) { NetworkVpnObservation(NetworkObservationState.UNAVAILABLE, false) }
        assertThrows(IllegalArgumentException::class.java) { NetworkProxyObservation(NetworkObservationState.OBSERVED) }
        assertThrows(IllegalArgumentException::class.java) { NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED) }
    }
}
