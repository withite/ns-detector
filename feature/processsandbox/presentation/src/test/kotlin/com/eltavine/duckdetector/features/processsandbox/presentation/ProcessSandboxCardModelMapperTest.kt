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

package com.eltavine.duckdetector.features.processsandbox.presentation

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.report.ReportBlock
import com.eltavine.duckdetector.features.processsandbox.domain.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ProcessSandboxCardModelMapperTest {
    private val mapper = ProcessSandboxCardModelMapper(Locale.ENGLISH)
    private fun ready(mode: Int = 2, system: Boolean = false) = ProcessSandboxReport(
        ProcessSandboxStage.READY, SandboxObservation.observed(SandboxHost(10_123, system)),
        SandboxStatusSnapshot(
            SandboxObservation.observed(SandboxUidQuartet(10_123, 10_123, 10_123, 10_123)),
            SandboxObservation.observed(0uL), SandboxObservation.observed(0uL), SandboxObservation.observed(0uL),
            SandboxObservation.observed(mode),
        ),
    )

    @Test fun `unobserved scan cannot read as a clean environment`() {
        val model = mapper.map(ProcessSandboxReport(ProcessSandboxStage.READY))
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), model.status)
        assertEquals("Not evaluated", model.verdict)
    }

    @Test fun `disabled Seccomp is descriptive contextual information with permissive caveat`() {
        val model = mapper.map(ready(mode = 0))
        val row = model.signalRows.last()
        assertEquals("Disabled for the sampling thread", row.value)
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), row.status)
        assertTrue(row.detail!!.contains("permissive"))
        assertTrue(model.summary.contains("sampling thread only"))
    }

    @Test fun `unknown mode retains unsupported row rather than reporting filter enabled`() {
        val row = mapper.map(ready(mode = 99)).signalRows.last()
        assertEquals("Unknown mode", row.value)
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), row.status)
    }

    @Test fun `UID values and raw proc content are omitted from exported rows`() {
        val model = mapper.map(ready())
        val export = model.toDetectorReport()
        val rows = export.blocks.filterIsInstance<ReportBlock.Rows>().flatMap { it.rows }
        assertEquals(7, rows.size)
        assertFalse(rows.toString().contains("10123"))
        assertEquals(model.verdict, export.verdict)
        assertEquals(model.status.severity, export.severity)
    }

    @Test fun `special host explains that ordinary baseline is not applied`() {
        val model = mapper.map(ready(system = true))
        assertEquals("Special or system host · context only", model.signalRows.first().value)
        assertEquals("Observed · baseline not applied", model.signalRows[1].value)
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), model.status)
    }

    @Test fun `Chinese UI and report titles use the same localized scope`() {
        val model = ProcessSandboxCardModelMapper(Locale.SIMPLIFIED_CHINESE).map(ready())
        assertEquals("进程沙箱", model.title)
        assertTrue(model.summary.contains("采样的线程"))
        assertEquals(listOf("内核状态观察", "范围与解释"), model.toDetectorReport().blocks.filterIsInstance<ReportBlock.Rows>().map { it.title })
    }

    @Test fun `partial errors retain independent mask values and diagnostic observation wording`() {
        val base = ready()
        val model = mapper.map(base.copy(snapshot = base.snapshot.copy(seccomp = SandboxObservation(SandboxObservationState.MALFORMED))))
        assertEquals(DetectorStatus.info(InfoKind.ERROR), model.status)
        assertEquals("Malformed snapshot field", model.signalRows.last().value)
        assertEquals("0x0000000000000000", model.signalRows[2].value)
    }
}
