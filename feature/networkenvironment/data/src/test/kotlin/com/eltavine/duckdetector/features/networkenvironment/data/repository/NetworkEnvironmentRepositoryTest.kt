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

package com.eltavine.duckdetector.features.networkenvironment.data.repository

import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkDefaultSnapshot
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkObservationState
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkVpnObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreCounts
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvironmentRepositoryTest {
    @Test
    fun `network failure preserves independent proxy and removes exception identifiers`() {
        val probe = FakeProbe(networkRead = { throw IOException("private.example:443") })
        val report = repository(probe).collect()
        assertEquals(NetworkObservationState.FAILED, report.network.vpn.state)
        assertEquals(NetworkObservationState.FAILED, report.network.privateDns.state)
        assertEquals("IOException", report.network.vpn.failureName)
        assertEquals(NetworkProxyMode.NONE, report.proxy.mode)
        assertTrue(report.partiallyEvaluated)
    }

    @Test
    fun `proxy failure preserves VPN and private DNS observations`() {
        val probe = FakeProbe(proxyRead = { throw IllegalStateException("sensitive PAC URI") })
        val report = repository(probe).collect()
        assertEquals(true, report.network.vpn.usesVpn)
        assertEquals(NetworkPrivateDnsMode.ACTIVE_STRICT, report.network.privateDns.mode)
        assertEquals(NetworkObservationState.FAILED, report.proxy.state)
        assertEquals("IllegalStateException", report.proxy.failureName)
    }

    @Test
    fun `permission denial is distinct from an inactive configuration`() {
        val report = repository(FakeProbe(
            networkRead = { throw SecurityException("ACCESS_NETWORK_STATE") },
            proxyRead = { throw SecurityException("policy denied") },
        )).collect()
        assertEquals(NetworkObservationState.PERMISSION_REQUIRED, report.network.vpn.state)
        assertEquals(NetworkObservationState.PERMISSION_REQUIRED, report.network.privateDns.state)
        assertEquals(NetworkObservationState.PERMISSION_REQUIRED, report.proxy.state)
        assertFalse(report.probed)
    }

    @Test
    fun `a network transition remains unavailable rather than reporting no VPN`() {
        val report = repository(FakeProbe(
            networkRead = { NetworkDefaultSnapshot.unavailable(NetworkObservationState.NETWORK_CHANGED) },
        )).collect()
        assertEquals(NetworkObservationState.NETWORK_CHANGED, report.network.vpn.state)
        assertEquals(null, report.network.vpn.usesVpn)
        assertTrue(report.partiallyEvaluated)
    }

    @Test
    fun `cancellation from either source propagates`() {
        assertThrows(CancellationException::class.java) {
            repository(FakeProbe(networkRead = { throw CancellationException() })).collect()
        }
        assertThrows(CancellationException::class.java) {
            repository(FakeProbe(proxyRead = { throw CancellationException() })).collect()
        }
    }

    @Test
    fun `CA store remains independently observable when connectivity reads fail`() {
        val trustStore = NetworkTrustStoreObservation(TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(2, 140))
        val report = repository(FakeProbe(
            networkRead = { throw IOException("private.example") },
            proxyRead = { throw IOException("private.proxy") },
        ), NetworkTrustStoreProbe { trustStore }).collect()
        assertEquals(trustStore, report.trustStore)
        assertTrue(report.probed)
        assertTrue(report.partiallyEvaluated)
    }

    @Test
    fun `CA store failures preserve connectivity and discard exception identifiers`() {
        val report = repository(FakeProbe(), NetworkTrustStoreProbe { throw IOException("private certificate") }).collect()
        assertEquals(true, report.network.vpn.usesVpn)
        assertEquals(NetworkProxyMode.NONE, report.proxy.mode)
        assertEquals(TrustStoreObservationState.FAILED, report.trustStore.state)
        assertEquals(null, report.trustStore.counts)
        assertEquals("IOException", report.trustStore.failureName)
        assertFalse(report.toString().contains("private certificate"))
    }

    @Test
    fun `CA store permission denial is support and cancellation propagates`() {
        val report = repository(FakeProbe(), NetworkTrustStoreProbe { throw SecurityException() }).collect()
        assertEquals(TrustStoreObservationState.PERMISSION_REQUIRED, report.trustStore.state)
        assertThrows(CancellationException::class.java) {
            repository(FakeProbe(), NetworkTrustStoreProbe { throw CancellationException() }).collect()
        }
    }

    @Test
    fun `cancellation between independent sources prevents further provider reads`() {
        var trustReads = 0
        var checks = 0
        val repository = repository(FakeProbe(), NetworkTrustStoreProbe {
            trustReads++
            NetworkTrustStoreObservation(TrustStoreObservationState.PROVIDER_UNAVAILABLE)
        })
        assertThrows(CancellationException::class.java) {
            repository.collect { if (++checks == 3) throw CancellationException() }
        }
        assertEquals(0, trustReads)
    }

    private fun repository(
        probe: NetworkEnvironmentProbe,
        trustStoreProbe: NetworkTrustStoreProbe = NetworkTrustStoreProbe {
            NetworkTrustStoreObservation(TrustStoreObservationState.PROVIDER_UNAVAILABLE)
        },
    ) = NetworkEnvironmentRepository(probe, trustStoreProbe)

    private class FakeProbe(
        private val networkRead: () -> NetworkDefaultSnapshot = {
            NetworkDefaultSnapshot(
                NetworkVpnObservation(NetworkObservationState.OBSERVED, true),
                NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, NetworkPrivateDnsMode.ACTIVE_STRICT),
            )
        },
        private val proxyRead: () -> NetworkProxyObservation = {
            NetworkProxyObservation(NetworkObservationState.OBSERVED, NetworkProxyMode.NONE)
        },
    ) : NetworkEnvironmentProbe {
        override fun readDefaultNetwork(): NetworkDefaultSnapshot = networkRead()
        override fun readProxy(): NetworkProxyObservation = proxyRead()
    }
}
