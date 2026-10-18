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

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.processsandbox.domain.*
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ProcessSandboxRepositoryTest {
    private open class Probe : ProcessSandboxProbe {
        var statusReads = 0
        override fun host() = SandboxHost(10_123, false)
        override fun status(checkpoint: () -> Unit): ByteArray {
            statusReads++
            checkpoint()
            return "Uid: 10123 10123 10123 10123\nCapEff: 0\nCapPrm: 0\nCapAmb: 0\nSeccomp: 2".toByteArray()
        }
    }

    @Test fun `one status read serves all five observations`() {
        val probe = Probe()
        val report = ProcessSandboxRepository(probe).collect()
        assertEquals(1, probe.statusReads)
        assertTrue(report.observationStates.all { it == SandboxObservationState.OBSERVED })
    }

    @Test fun `status read failure preserves host with no raw exception text`() {
        val report = ProcessSandboxRepository(object : Probe() {
            override fun status(checkpoint: () -> Unit): ByteArray = throw IOException("private rawstatus path")
        }).collect()
        assertEquals(SandboxObservationState.OBSERVED, report.host.state)
        assertTrue(report.snapshot.states.all { it == SandboxObservationState.FAILED })
        assertEquals("IOException", report.snapshot.uids.failureName)
        assertFalse(report.toString().contains("private rawstatus"))
    }

    @Test fun `explicit permission denial is limited coverage rather than evidence absence`() {
        val report = ProcessSandboxRepository(object : Probe() {
            override fun status(checkpoint: () -> Unit): ByteArray = throw SecurityException()
        }).collect()
        assertTrue(report.snapshot.states.all { it == SandboxObservationState.PERMISSION_DENIED })
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
    }

    @Test fun `missing or denied FileInputStream path remains ambiguous unavailable`() {
        val report = ProcessSandboxRepository(object : Probe() {
            override fun status(checkpoint: () -> Unit): ByteArray = throw FileNotFoundException("Permission denied")
        }).collect()
        assertTrue(report.snapshot.states.all { it == SandboxObservationState.UNAVAILABLE })
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
    }

    @Test fun `host failure does not erase observed status or permit baseline warnings`() {
        val report = ProcessSandboxRepository(object : Probe() {
            override fun host(): SandboxHost = throw IOException()
        }).collect()
        assertEquals(SandboxObservationState.FAILED, report.host.state)
        assertTrue(report.snapshot.states.all { it == SandboxObservationState.OBSERVED })
        assertFalse(report.hasExpandedCapabilities())
    }

    @Test fun `before after UID change marks baseline inapplicable`() {
        var reads = 0
        val report = ProcessSandboxRepository(object : Probe() {
            override fun host() = SandboxHost(if (reads++ == 0) 10_123 else 10_124, false)
        }).collect()
        assertEquals(SandboxObservationState.IDENTITY_CHANGED, report.host.state)
        assertNull(report.host.value)
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
    }

    @Test fun `cancellation from status read propagates`() {
        assertThrows(CancellationException::class.java) {
            ProcessSandboxRepository(object : Probe() {
                override fun status(checkpoint: () -> Unit): ByteArray = throw CancellationException()
            }).collect()
        }
    }

    @Test fun `checkpoint cancellation cannot publish a finished snapshot`() {
        var checkpoints = 0
        assertThrows(CancellationException::class.java) {
            ProcessSandboxRepository(Probe()).collect { if (++checkpoints == 3) throw CancellationException() }
        }
        assertEquals(3, checkpoints)
    }

    @Test fun `bounded reader accepts exact limit and rejects one byte more`() {
        assertEquals(MAX_STATUS_BYTES, readBoundedStatus(ByteArrayInputStream(ByteArray(MAX_STATUS_BYTES))).size)
        assertThrows(StatusSnapshotTooLarge::class.java) {
            readBoundedStatus(ByteArrayInputStream(ByteArray(MAX_STATUS_BYTES + 1)))
        }
    }

    @Test fun `bounded read cooperates with cancellation between chunks`() {
        var calls = 0
        assertThrows(CancellationException::class.java) {
            readBoundedStatus(ByteArrayInputStream(ByteArray(MAX_STATUS_BYTES))) { if (++calls == 3) throw CancellationException() }
        }
        assertEquals(3, calls)
    }

    @Test fun `oversize status is an explicit malformed snapshot not clean zero masks`() {
        val report = ProcessSandboxRepository(object : Probe() {
            override fun status(checkpoint: () -> Unit): ByteArray = throw StatusSnapshotTooLarge()
        }).collect()
        assertTrue(report.snapshot.states.all { it == SandboxObservationState.MALFORMED })
        assertEquals(DetectorStatus.info(InfoKind.ERROR), report.toDetectorStatus())
    }
}
