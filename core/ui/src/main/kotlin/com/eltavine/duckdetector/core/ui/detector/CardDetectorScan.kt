/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0.
 */

package com.eltavine.duckdetector.core.ui.detector

import com.eltavine.duckdetector.core.detector.Detector
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.report.DetectorHeadline
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.core.report.ReportFact
import com.eltavine.duckdetector.core.scan.DetectorSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal enum class CardScanStage { COLLECTION, PRESENTATION }

/** An infrastructure failure, never evidence that the inspected environment is dangerous. */
internal data class CardScanFailure(val stage: CardScanStage, val name: String) {
    fun summary(id: DetectorId, title: String) = DetectorSummary(
        id = id,
        title = title,
        status = DetectorStatus.info(InfoKind.ERROR),
        headline = "Scan failed",
        summary = "This module could not complete its scan. Retry before interpreting its evidence.",
        ready = true,
        findingDetail = "${stage.label}: $name. No security conclusion is available for this scan.",
    )

    fun report(title: String) = DetectorReport(
        title = title,
        verdict = "Scan failed",
        severity = DetectionSeverity.INFO,
        quickFacts = listOf(
            ReportFact("Outcome", "Execution failed; no security conclusion is available"),
            ReportFact("Stage", stage.label),
            ReportFact("Failure", name),
        ),
        blocks = emptyList(),
    )
}

private val CardScanStage.label: String
    get() = when (this) {
        CardScanStage.COLLECTION -> "Evidence collection"
        CardScanStage.PRESENTATION -> "Report presentation"
    }

internal sealed interface CardScanResult<out M> {
    data class Success<M>(val model: M) : CardScanResult<M>
    data class Failed(val failure: CardScanFailure) : CardScanResult<Nothing>
}

internal suspend fun <R : Any, M : DetectorHeadline> collectDetectorCard(
    detector: Detector<R, M>,
    scanner: DetectorScanner<R>,
    dispatcher: CoroutineDispatcher,
): CardScanResult<M> {
    val report = try {
        scanner.scan()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        return CardScanResult.Failed(CardScanFailure(CardScanStage.COLLECTION, FailureName.of(failure)))
    }
    return try {
        CardScanResult.Success(withContext(dispatcher) { detector.describe(report) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        CardScanResult.Failed(CardScanFailure(CardScanStage.PRESENTATION, FailureName.of(failure)))
    }
}
