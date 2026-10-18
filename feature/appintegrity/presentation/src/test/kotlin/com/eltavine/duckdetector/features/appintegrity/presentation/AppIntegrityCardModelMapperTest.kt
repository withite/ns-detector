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

package com.eltavine.duckdetector.features.appintegrity.presentation

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.appintegrity.domain.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppIntegrityCardModelMapperTest {
    private fun ready() = AppIntegrityReport(
        stage = AppIntegrityStage.READY,
        build = AppObservation.observed(AppBuildSnapshot("com.example.host", false, false)),
        signing = AppObservation.observed(AppSigningSnapshot(setOf("A".repeat(64)), false, 1)),
        policy = AppObservation.observed(AppSigningPolicy("com.example.host", setOf("A".repeat(64)))),
        debugger = AppObservation.observed(AppDebuggerSnapshot(true, false)),
        tracerPid = AppObservation.observed(412),
    )
    private val english = AppIntegrityCardModelMapper(Locale.ENGLISH)

    @Test fun `match is context with scoped wording and debugger observation does not warn`() {
        val model = english.map(ready())
        assertEquals(InfoKind.CONTEXT, model.status.infoKind)
        assertEquals("Current certificate matches host policy", model.verdict)
        assertEquals("A".repeat(64), model.signalRows[1].value)
        assertEquals("Yes", model.scanRows[3].value)
        assertEquals("412", model.scanRows[5].value)
        assertTrue(model.scanRows.all { it.status.infoKind == InfoKind.CONTEXT })
        assertTrue(model.summary.contains("cannot prove device safety"))
        assertEquals(model.verdict, model.toDetectorReport().verdict)
    }

    @Test fun `mismatch affects signature policy row but not debugging rows`() {
        val model = english.map(ready().copy(policy = AppObservation.observed(
            AppSigningPolicy("com.example.host", setOf("B".repeat(64))),
        )))
        assertEquals(DetectionSeverity.WARNING, model.status.severity)
        assertEquals(DetectionSeverity.WARNING, model.signalRows[0].status.severity)
        assertTrue(model.scanRows.all { it.status.infoKind == InfoKind.CONTEXT })
    }

    @Test fun `missing policy unsupported and failed tracer never shown as zero`() {
        val model = english.map(ready().copy(
            policy = AppObservation(AppObservationState.MISSING),
            tracerPid = AppObservation(AppObservationState.FAILED, failureName = "IOException"),
        ))
        assertEquals(InfoKind.ERROR, model.status.infoKind)
        assertEquals(InfoKind.SUPPORT, model.signalRows[0].status.infoKind)
        assertEquals("Read failed · unknown", model.scanRows[5].value)
        assertEquals(InfoKind.ERROR, model.scanRows[5].status.infoKind)
        assertTrue(model.scanRows[5].detail.orEmpty().contains("IOException"))
    }

    @Test fun `Chinese UI and export share local diagnostic scope and headings`() {
        val model = AppIntegrityCardModelMapper(Locale.SIMPLIFIED_CHINESE).map(ready())
        assertEquals("应用完整性", model.title)
        assertEquals("当前证书符合宿主策略", model.verdict)
        assertEquals("签名策略", model.signatureTitle)
        assertEquals("应用运行状态", model.runtimeTitle)
        assertTrue(model.summary.contains("不能证明设备安全"))
        assertEquals(model.title, model.toDetectorReport().title)
    }

    @Test fun `loading and failure do not create invented rows`() {
        for (report in listOf(AppIntegrityReport.loading(), AppIntegrityReport.failed("IOException"))) {
            val model = english.map(report)
            assertTrue(model.signalRows.isEmpty())
            assertTrue(model.scanRows.isEmpty())
            assertEquals(DetectionSeverity.INFO, model.status.severity)
        }
    }
}
