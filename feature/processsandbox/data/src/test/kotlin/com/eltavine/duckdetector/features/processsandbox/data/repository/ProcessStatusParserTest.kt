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

package com.eltavine.duckdetector.features.processsandbox.data.repository

import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservationState
import org.junit.Assert.*
import org.junit.Test

class ProcessStatusParserTest {
    private val valid = """
        Name: harmless
        Uid: 10123 10123 10123 10123
        CapEff: 0000000000000000
        CapPrm: 0000001000000000
        CapAmb: 0000000000000000
        CapBnd: ffffffffffffffff
        NoNewPrivs: 0
        Seccomp: 2
    """.trimIndent()
    private fun parse(text: String) = ProcessStatusParser.parse(text.toByteArray())

    @Test fun `ACK fields retain exact unsigned values while bounding set is ignored`() {
        val snapshot = parse(valid)
        assertTrue(snapshot.states.all { it == SandboxObservationState.OBSERVED })
        assertEquals(10_123L, snapshot.uids.value!!.filesystem)
        assertEquals(0uL, snapshot.effectiveCapabilities.value)
        assertEquals(1uL shl 36, snapshot.permittedCapabilities.value)
        assertEquals(2, snapshot.seccomp.value)
    }

    @Test fun `all ones capability mask is not truncated to signed long`() {
        val snapshot = parse(valid.replace("CapEff: 0000000000000000", "CapEff: fFfFfFfFfFfFfFfF"))
        assertEquals(ULong.MAX_VALUE, snapshot.effectiveCapabilities.value)
    }

    @Test fun `missing optional kernel fields do not imply disabled or zero`() {
        val snapshot = parse("Uid: 10123\t10123\t10123\t10123\nCapEff: 0")
        assertEquals(SandboxObservationState.MISSING, snapshot.seccomp.state)
        assertEquals(SandboxObservationState.MISSING, snapshot.ambientCapabilities.state)
        assertNull(snapshot.seccomp.value)
        assertNull(snapshot.ambientCapabilities.value)
    }

    @Test fun `each malformed UID token and wrong tuple size is rejected`() {
        listOf("-1 2 3 4", "+1 2 3 4", "1 2 3", "1 2 3 4 5", "4294967296 2 3 4", "x 2 3 4").forEach { tuple ->
            val snapshot = parse(valid.replace("10123 10123 10123 10123", tuple))
            assertEquals(tuple, SandboxObservationState.MALFORMED, snapshot.uids.state)
            assertNull(snapshot.uids.value)
            assertEquals(SandboxObservationState.OBSERVED, snapshot.seccomp.state)
        }
    }

    @Test fun `full unsigned UID range can be observed without signed overflow`() {
        assertEquals(0xFFFF_FFFFL, parse(valid.replace("10123 10123 10123 10123", "4294967295 4294967295 4294967295 4294967295")).uids.value!!.real)
    }

    @Test fun `malformed hex fields preserve the other independent fields`() {
        listOf("0x1", "-1", "gg", "10000000000000000", "1 2", "").forEach { raw ->
            val snapshot = parse(valid.replace("CapEff: 0000000000000000", "CapEff: $raw"))
            assertEquals(raw, SandboxObservationState.MALFORMED, snapshot.effectiveCapabilities.state)
            assertEquals(SandboxObservationState.OBSERVED, snapshot.permittedCapabilities.state)
        }
    }

    @Test fun `duplicate recognized fields are ambiguous not first or last wins`() {
        assertEquals(SandboxObservationState.MALFORMED, parse(valid + "\nSeccomp: 0").seccomp.state)
        assertEquals(SandboxObservationState.MALFORMED, parse(valid + "\nCapEff: 0").effectiveCapabilities.state)
        assertEquals(SandboxObservationState.MALFORMED, parse(valid + "\nUid: 1 1 1 1").uids.state)
    }

    @Test fun `future Seccomp mode is retained for explicit unsupported interpretation`() {
        assertEquals(99, parse(valid.replace("Seccomp: 2", "Seccomp: 99")).seccomp.value)
    }

    @Test fun `negative signed overflowing and multi token Seccomp fields are malformed`() {
        listOf("-1", "+2", "2147483648", "2 2", "").forEach { raw ->
            assertEquals(SandboxObservationState.MALFORMED, parse(valid.replace("Seccomp: 2", "Seccomp: $raw")).seccomp.state)
        }
    }

    @Test fun `empty input stays missing while NUL and overlimit snapshots are malformed`() {
        assertTrue(parse("").states.all { it == SandboxObservationState.MISSING })
        assertTrue(parse(valid + "\u0000").states.all { it == SandboxObservationState.MALFORMED })
        assertTrue(ProcessStatusParser.parse(ByteArray(MAX_STATUS_BYTES + 1) { 65 }).states.all { it == SandboxObservationState.MALFORMED })
    }
}
