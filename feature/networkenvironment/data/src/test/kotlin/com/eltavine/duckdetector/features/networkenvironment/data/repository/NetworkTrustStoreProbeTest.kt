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

import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreCounts
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import java.io.IOException
import java.util.Collections
import java.util.Enumeration
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTrustStoreProbeTest {
    @Test
    fun `provider aliases become counts without retaining aliases or certificates`() {
        val report = probe("system:12345678.0", "user:private-certificate.0", "system:abcdef12.0").read {}
        assertEquals(TrustStoreObservationState.OBSERVED, report.state)
        assertEquals(NetworkTrustStoreCounts(1, 2), report.counts)
        assertFalse(report.toString().contains("private-certificate"))
        assertFalse(report.toString().contains("12345678"))
    }

    @Test
    fun `empty enumeration is an observed zero and missing provider is unavailable`() {
        val empty = probe().read {}
        assertEquals(TrustStoreObservationState.OBSERVED, empty.state)
        assertEquals(NetworkTrustStoreCounts(0, 0), empty.counts)
        val missing = AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { null }).read {}
        assertEquals(TrustStoreObservationState.PROVIDER_UNAVAILABLE, missing.state)
        assertEquals(null, missing.counts)
    }

    @Test
    fun `unknown null empty overlong and incomplete aliases are not classified as system CAs`() {
        val report = probe(null, "", "user:", "system:", "vendor:private", "user:" + "x".repeat(256)).read {}
        assertEquals(TrustStoreObservationState.UNRECOGNIZED_ALIASES, report.state)
        assertEquals(NetworkTrustStoreCounts(0, 0, 6), report.counts)
        assertFalse(report.toString().contains("vendor:private"))
    }

    @Test
    fun `enumeration stops before reading any aliases beyond the processing bound`() {
        var reads = 0
        val endless = object : Enumeration<String> {
            override fun hasMoreElements() = true
            override fun nextElement(): String { reads++; return "user:private-$reads" }
        }
        val report = AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { endless }, maxAliases = 4).read {}
        assertEquals(4, reads)
        assertEquals(TrustStoreObservationState.TRUNCATED, report.state)
        assertEquals(NetworkTrustStoreCounts(4, 0), report.counts)
    }

    @Test
    fun `exactly full finite enumeration is complete and not truncated`() {
        val probe = AndroidNetworkTrustStoreProbe(
            TrustStoreAliasSource { Collections.enumeration(listOf("system:one", "user:two")) }, maxAliases = 2,
        )
        assertEquals(TrustStoreObservationState.OBSERVED, probe.read {}.state)
    }

    @Test
    fun `permission denial and provider failures preserve no misleading partial counts`() {
        val denied = AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { throw SecurityException("private alias") }).read {}
        assertEquals(TrustStoreObservationState.PERMISSION_REQUIRED, denied.state)
        assertEquals(null, denied.counts)
        val failed = AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { throw IOException("private subject") }).read {}
        assertEquals(TrustStoreObservationState.FAILED, failed.state)
        assertEquals("IOException", failed.failureName)
        assertFalse(failed.toString().contains("private subject"))
    }

    @Test
    fun `failure mid enumeration discards partial counts and retains only exception type`() {
        val aliases = object : Enumeration<String> {
            private var reads = 0
            override fun hasMoreElements() = true
            override fun nextElement(): String = if (++reads == 1) "system:private" else throw IOException("private")
        }
        val report = AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { aliases }).read {}
        assertEquals(TrustStoreObservationState.FAILED, report.state)
        assertEquals(null, report.counts)
        assertEquals("IOException", report.failureName)
    }

    @Test
    fun `cooperative cancellation stops between aliases and propagates provider cancellation`() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            probe("user:one", "system:two").read {
                if (++checks >= 4) throw CancellationException()
            }
        }
        assertTrue(checks >= 4)
        assertThrows(CancellationException::class.java) {
            AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { throw CancellationException() }).read {}
        }
    }

    @Test
    fun `invalid processing limits are rejected`() {
        for (limit in listOf(-1, 0, 4097, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                AndroidNetworkTrustStoreProbe(TrustStoreAliasSource { null }, limit)
            }
        }
    }

    private fun probe(vararg aliases: String?) = AndroidNetworkTrustStoreProbe(
        TrustStoreAliasSource { Collections.enumeration(aliases.toList()) },
    )
}
