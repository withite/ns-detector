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
        val report = NetworkEnvironmentRepository(probe).collect()
        assertEquals(NetworkObservationState.FAILED, report.network.vpn.state)
        assertEquals(NetworkObservationState.FAILED, report.network.privateDns.state)
        assertEquals("IOException", report.network.vpn.failureName)
        assertEquals(NetworkProxyMode.NONE, report.proxy.mode)
        assertTrue(report.partiallyEvaluated)
    }

    @Test
    fun `proxy failure preserves VPN and private DNS observations`() {
        val probe = FakeProbe(proxyRead = { throw IllegalStateException("sensitive PAC URI") })
        val report = NetworkEnvironmentRepository(probe).collect()
        assertEquals(true, report.network.vpn.usesVpn)
        assertEquals(NetworkPrivateDnsMode.ACTIVE_STRICT, report.network.privateDns.mode)
        assertEquals(NetworkObservationState.FAILED, report.proxy.state)
        assertEquals("IllegalStateException", report.proxy.failureName)
    }

    @Test
    fun `permission denial is distinct from an inactive configuration`() {
        val report = NetworkEnvironmentRepository(FakeProbe(
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
        val report = NetworkEnvironmentRepository(FakeProbe(
            networkRead = { NetworkDefaultSnapshot.unavailable(NetworkObservationState.NETWORK_CHANGED) },
        )).collect()
        assertEquals(NetworkObservationState.NETWORK_CHANGED, report.network.vpn.state)
        assertEquals(null, report.network.vpn.usesVpn)
        assertTrue(report.partiallyEvaluated)
    }

    @Test
    fun `cancellation from either source propagates`() {
        assertThrows(CancellationException::class.java) {
            NetworkEnvironmentRepository(FakeProbe(networkRead = { throw CancellationException() })).collect()
        }
        assertThrows(CancellationException::class.java) {
            NetworkEnvironmentRepository(FakeProbe(proxyRead = { throw CancellationException() })).collect()
        }
    }

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
