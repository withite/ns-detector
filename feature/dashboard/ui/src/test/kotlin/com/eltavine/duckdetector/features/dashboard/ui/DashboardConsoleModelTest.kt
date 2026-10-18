/*
 * Copyright 2026 NS Detector contributors
 * Added 2026 by NS Detector contributors: console projection regression coverage.
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

package com.eltavine.duckdetector.features.dashboard.ui

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.scan.DetectorSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardConsoleModelTest {

    @Test
    fun `empty scan has zero progress and no returned modules`() {
        val model = buildDashboardConsoleModel(emptyList())

        assertEquals(0, model.totalCount)
        assertEquals(0, model.readyCount)
        assertEquals(0, model.pendingCount)
        assertEquals(0f, model.completedFraction, 0f)
        assertEquals(emptyList<DetectorId>(), model.visibleDetectorIds)
        assertEquals(emptyList<DashboardConsoleSegment>(), model.segments)
    }

    @Test
    fun `completion counts returned modules rather than all clear modules`() {
        val summaries = listOf(
            summary("first", DetectorStatus.danger()),
            summary("second", DetectorStatus.info(InfoKind.ERROR)),
            summary("third", DetectorStatus.info(InfoKind.SUPPORT), ready = false),
            summary("fourth", DetectorStatus.allClear(), ready = false),
        )

        val model = buildDashboardConsoleModel(summaries)

        assertEquals(4, model.totalCount)
        assertEquals(2, model.readyCount)
        assertEquals(2, model.pendingCount)
        assertEquals(0.5f, model.completedFraction, 0f)
        assertEquals(1, model.dangerCount)
        assertEquals(1, model.errorCount)
        assertEquals(0, model.limitedCount)
        assertEquals(0, model.allClearCount)
    }

    @Test
    fun `returned status distribution preserves distinct info kinds`() {
        val model = buildDashboardConsoleModel(allReturnedStatuses())

        assertEquals(7, model.totalCount)
        assertEquals(7, model.readyCount)
        assertEquals(0, model.pendingCount)
        assertEquals(1f, model.completedFraction, 0f)
        assertEquals(1, model.dangerCount)
        assertEquals(1, model.warningCount)
        assertEquals(1, model.limitedCount)
        assertEquals(1, model.errorCount)
        assertEquals(1, model.contextCount)
        assertEquals(1, model.allClearCount)
        assertEquals(1, model.otherInfoCount)
        assertEquals(2, model.attentionCount)
        assertEquals(2, model.otherCount)
    }

    @Test
    fun `each filter selects only its published ready status categories`() {
        val summaries = allReturnedStatuses() +
            summary("pending", DetectorStatus.info(InfoKind.SUPPORT), ready = false)
        val expected = mapOf(
            DashboardConsoleFilter.ALL to summaries.map { it.id },
            DashboardConsoleFilter.ATTENTION to listOf(DetectorId("danger"), DetectorId("warning")),
            DashboardConsoleFilter.LIMITED to listOf(DetectorId("limited")),
            DashboardConsoleFilter.ERRORS to listOf(DetectorId("error")),
            DashboardConsoleFilter.OTHER to listOf(DetectorId("context"), DetectorId("clear")),
        )

        expected.forEach { (filter, ids) ->
            val model = buildDashboardConsoleModel(summaries, DashboardConsoleSelection(filter))

            assertEquals(filter.name, ids, model.visibleDetectorIds)
            assertEquals(8, model.totalCount)
            assertEquals(7, model.readyCount)
            assertEquals(1, model.pendingCount)
        }
    }

    @Test
    fun `pending status never becomes a completed category even with danger status`() {
        val summaries = listOf(summary("pending", DetectorStatus.danger(), ready = false))

        val model = buildDashboardConsoleModel(
            summaries,
            DashboardConsoleSelection(DashboardConsoleFilter.ATTENTION),
        )

        assertEquals(0, model.dangerCount)
        assertEquals(0, model.readyCount)
        assertEquals(1, model.pendingCount)
        assertEquals(0f, model.completedFraction, 0f)
        assertEquals(listOf(DashboardConsoleSegment.PENDING), model.segments)
        assertEquals(emptyList<DetectorId>(), model.visibleDetectorIds)
    }

    @Test
    fun `title and evidence text never determine the status filter`() {
        val summaries = listOf(
            summary("observed", DetectorStatus.allClear(), title = "Danger warning error"),
            summary("actual", DetectorStatus.danger(), title = "All clear"),
        )

        val model = buildDashboardConsoleModel(
            summaries,
            DashboardConsoleSelection(DashboardConsoleFilter.ATTENTION),
        )

        assertEquals(listOf(DetectorId("actual")), model.visibleDetectorIds)
        assertEquals(1, model.allClearCount)
    }

    @Test
    fun `search matches title and stable id ignoring case and outer whitespace`() {
        val summaries = listOf(
            summary("alpha", DetectorStatus.allClear(), title = "Hardware Key Store"),
            summary("hardware_beta", DetectorStatus.allClear(), title = "System"),
            summary("gamma", DetectorStatus.allClear(), title = "Network"),
        )

        val model = buildDashboardConsoleModel(
            summaries,
            DashboardConsoleSelection(query = "  HARDWARE  "),
        )

        assertEquals(listOf(DetectorId("alpha"), DetectorId("hardware_beta")), model.visibleDetectorIds)
        assertEquals(3, model.totalCount)
        assertEquals(3, model.readyCount)
        assertEquals(1f, model.completedFraction, 0f)
    }

    @Test
    fun `search excludes headline summary and detail`() {
        val summaries = listOf(
            summary("alpha", DetectorStatus.danger()).copy(
                headline = "Needle",
                summary = "Needle",
                findingDetail = "Needle",
            ),
        )

        val model = buildDashboardConsoleModel(summaries, DashboardConsoleSelection(query = "needle"))

        assertEquals(emptyList<DetectorId>(), model.visibleDetectorIds)
        assertEquals(1, model.dangerCount)
    }

    @Test
    fun `search intersects with selected filter`() {
        val summaries = listOf(
            summary("alpha", DetectorStatus.warning(), title = "Hardware A"),
            summary("beta", DetectorStatus.allClear(), title = "Hardware B"),
            summary("gamma", DetectorStatus.danger(), title = "Network"),
        )

        val model = buildDashboardConsoleModel(
            summaries,
            DashboardConsoleSelection(DashboardConsoleFilter.ATTENTION, query = "Hardware"),
        )

        assertEquals(listOf(DetectorId("alpha")), model.visibleDetectorIds)
        assertEquals(2, model.attentionCount)
        assertEquals(3, model.readyCount)
    }

    @Test
    fun `filtering preserves source order including pending entries`() {
        val summaries = listOf(
            summary("zulu", DetectorStatus.info(InfoKind.SUPPORT), ready = false),
            summary("alpha", DetectorStatus.allClear()),
            summary("mike", DetectorStatus.danger()),
        )

        val model = buildDashboardConsoleModel(summaries, DashboardConsoleSelection(query = "module"))

        assertEquals(summaries.map { it.id }, model.visibleDetectorIds)
        assertEquals(
            listOf(DashboardConsoleSegment.PENDING, DashboardConsoleSegment.CLEAR, DashboardConsoleSegment.DANGER),
            model.segments,
        )
    }

    @Test
    fun `info without a kind remains unclassified instead of becoming context or support`() {
        val summaries = listOf(summary("unknown", DetectorStatus(DetectionSeverity.INFO)))

        val model = buildDashboardConsoleModel(
            summaries,
            DashboardConsoleSelection(DashboardConsoleFilter.OTHER),
        )

        assertEquals(1, model.otherInfoCount)
        assertEquals(0, model.otherCount)
        assertEquals(0, model.limitedCount)
        assertEquals(listOf(DashboardConsoleSegment.OTHER_INFO), model.segments)
        assertEquals(emptyList<DetectorId>(), model.visibleDetectorIds)
    }

    private fun allReturnedStatuses(): List<DetectorSummary> = listOf(
        summary("danger", DetectorStatus.danger()),
        summary("warning", DetectorStatus.warning()),
        summary("limited", DetectorStatus.info(InfoKind.SUPPORT)),
        summary("error", DetectorStatus.info(InfoKind.ERROR)),
        summary("context", DetectorStatus.info(InfoKind.CONTEXT)),
        summary("clear", DetectorStatus.allClear()),
        summary("other", DetectorStatus(DetectionSeverity.INFO)),
    )

    private fun summary(
        id: String,
        status: DetectorStatus,
        ready: Boolean = true,
        title: String = "Module $id",
    ): DetectorSummary = DetectorSummary(
        id = DetectorId(id),
        title = title,
        status = status,
        headline = "Local result",
        summary = "Observed local evidence",
        ready = ready,
    )
}
