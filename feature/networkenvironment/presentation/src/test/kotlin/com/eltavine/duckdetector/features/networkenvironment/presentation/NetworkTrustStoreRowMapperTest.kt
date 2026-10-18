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

package com.eltavine.duckdetector.features.networkenvironment.presentation

import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreCounts
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTrustStoreRowMapperTest {
    @Test
    fun `visible positive and zero CA counts have context and explain actual TLS trust limitation`() {
        val mapper = NetworkTrustStoreRowMapper(false)
        for (user in listOf(0, 4)) {
            val row = mapper.map(NetworkTrustStoreObservation(
                TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(user, 150),
            ))
            assertEquals("User-added: $user · System: 150", row.value)
            assertEquals(InfoKind.CONTEXT, row.status.infoKind)
            assertTrue(row.detail.orEmpty().contains("this app's user/profile only"))
            assertTrue(row.detail.orEmpty().contains("do not establish this app's actual TLS trust"))
            assertTrue(row.detail.orEmpty().contains("inaccessible store is empty"))
            assertFalse(row.value.contains("safe"))
        }
    }

    @Test
    fun `partial enumeration counts never display as complete totals`() {
        for (state in listOf(TrustStoreObservationState.TRUNCATED, TrustStoreObservationState.UNRECOGNIZED_ALIASES)) {
            val row = NetworkTrustStoreRowMapper(false).map(NetworkTrustStoreObservation(
                state, NetworkTrustStoreCounts(1, 2, 1),
            ))
            assertEquals("Partial counts · User-added: 1 · System: 2 · Unclassified: 1", row.value)
            assertEquals(InfoKind.SUPPORT, row.status.infoKind)
        }
    }

    @Test
    fun `unread source states remain distinct and contain no invented zero counts`() {
        val states = listOf(TrustStoreObservationState.NOT_EVALUATED, TrustStoreObservationState.PROVIDER_UNAVAILABLE,
            TrustStoreObservationState.PERMISSION_REQUIRED, TrustStoreObservationState.FAILED)
        val values = states.map { state ->
            val row = NetworkTrustStoreRowMapper(false).map(NetworkTrustStoreObservation(state))
            assertFalse(row.value.contains("0"))
            val kind = if (state == TrustStoreObservationState.FAILED) InfoKind.ERROR else InfoKind.SUPPORT
            assertEquals(kind, row.status.infoKind)
            row.value
        }
        assertEquals(states.size, values.distinct().size)
    }

    @Test
    fun `Chinese projection keeps scope and certificate privacy`() {
        val row = NetworkTrustStoreRowMapper(true).map(NetworkTrustStoreObservation(
            TrustStoreObservationState.OBSERVED, NetworkTrustStoreCounts(0, 145),
        ))
        assertEquals("可见 CA 条目", row.label)
        assertEquals("用户添加：0 · 系统：145", row.value)
        assertTrue(row.detail.orEmpty().contains("当前用户或工作资料"))
        assertTrue(row.detail.orEmpty().contains("不保留别名或证书内容"))
    }
}
