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

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.report.ReportBlock
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkDefaultSnapshot
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentReport
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentStage
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkObservationState
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkVpnObservation
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvironmentCardModelMapperTest {
    private val english = NetworkEnvironmentCardModelMapper(Locale.ENGLISH)

    @Test
    fun `VPN and PAC configuration displays retain information severity and precise scope`() {
        val model = english.map(observed())
        assertEquals("VPN transport reported", model.rows[0].value)
        assertEquals("Automatic proxy (PAC) configured", model.rows[1].value)
        assertEquals("In use · strict hostname mode", model.rows[2].value)
        assertEquals(DetectionSeverity.INFO, model.status.severity)
        assertEquals(InfoKind.CONTEXT, model.status.infoKind)
        assertTrue(model.rows.all { it.status.severity == DetectionSeverity.INFO })
        assertTrue(model.rows.all { it.status.infoKind == InfoKind.CONTEXT })
        assertTrue(model.rows[0].detail.orEmpty().contains("default network only"))
        assertTrue(model.rows[1].detail.orEmpty().contains("actual traffic routing is not tested"))
    }

    @Test
    fun `inaccessible default network does not display VPN disabled or DNS disabled`() {
        val model = english.map(observed().copy(
            network = NetworkDefaultSnapshot.unavailable(NetworkObservationState.NO_DEFAULT_NETWORK),
        ))
        assertEquals("No accessible default network", model.rows[0].value)
        assertEquals("No accessible default network", model.rows[2].value)
        assertEquals("Partially observed · information only", model.verdict)
        assertEquals(InfoKind.SUPPORT, model.status.infoKind)
        assertEquals(InfoKind.SUPPORT, model.rows[0].status.infoKind)
        assertEquals(InfoKind.CONTEXT, model.rows[1].status.infoKind)
        assertEquals(InfoKind.SUPPORT, model.rows[2].status.infoKind)
        assertTrue(model.rows[0].detail.orEmpty().contains("blocked for this app"))
    }

    @Test
    fun `inactive DNS is not presented as a disabled system setting`() {
        val report = observed().copy(network = NetworkDefaultSnapshot(
            NetworkVpnObservation(NetworkObservationState.OBSERVED, false),
            NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, NetworkPrivateDnsMode.INACTIVE),
        ))
        val row = english.map(report).rows[2]
        assertEquals("Not in use on this network", row.value)
        assertTrue(row.detail.orEmpty().contains("does not distinguish Off from Automatic"))
    }

    @Test
    fun `unavailable source states each have a distinct displayed value`() {
        val states = NetworkObservationState.entries.filter { it != NetworkObservationState.OBSERVED }
        val values = states.map { state ->
            val model = english.map(NetworkEnvironmentReport(
                stage = NetworkEnvironmentStage.READY,
                network = NetworkDefaultSnapshot.unavailable(state),
                proxy = NetworkProxyObservation(state),
            ))
            assertEquals("Not evaluated", model.verdict)
            val expectedKind = if (state == NetworkObservationState.FAILED) InfoKind.ERROR else InfoKind.SUPPORT
            assertEquals(expectedKind, model.status.infoKind)
            assertTrue(model.rows.all { it.status.infoKind == expectedKind })
            model.rows[0].value
        }
        assertEquals(states.size, values.distinct().size)
    }

    @Test
    fun `Chinese card and export share localized labels and informational interpretation`() {
        val model = NetworkEnvironmentCardModelMapper(Locale.SIMPLIFIED_CHINESE).map(observed())
        assertEquals("网络环境", model.title)
        assertEquals("已配置自动代理（PAC）", model.rows[1].value)
        assertEquals("网络快照 · 仅供参考", model.verdict)
        val export = model.toDetectorReport()
        assertEquals(DetectionSeverity.INFO, export.severity)
        assertEquals(InfoKind.CONTEXT, model.status.infoKind)
        assertEquals("网络配置观察", export.blocks[0].title)
        assertEquals("默认 HTTP 代理", (export.blocks[0] as ReportBlock.Rows).rows[1].label)
        assertTrue((export.blocks[1] as ReportBlock.Bullets).items.single().contains("不增加风险等级"))
    }

    @Test
    fun `loading and failure export no fabricated observations`() {
        for (report in listOf(NetworkEnvironmentReport.loading(), NetworkEnvironmentReport.failed("IOException"))) {
            val model = english.map(report)
            assertTrue(model.rows.isEmpty())
            assertEquals(DetectionSeverity.INFO, model.status.severity)
            assertFalse(model.verdict.contains("snapshot"))
        }
    }

    private fun observed() = NetworkEnvironmentReport(
        stage = NetworkEnvironmentStage.READY,
        network = NetworkDefaultSnapshot(
            NetworkVpnObservation(NetworkObservationState.OBSERVED, true),
            NetworkPrivateDnsObservation(NetworkObservationState.OBSERVED, NetworkPrivateDnsMode.ACTIVE_STRICT),
        ),
        proxy = NetworkProxyObservation(NetworkObservationState.OBSERVED, NetworkProxyMode.AUTOMATIC),
    )
}
