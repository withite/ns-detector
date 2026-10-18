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

package com.eltavine.duckdetector.features.processsandbox.detector

import android.content.Context
import com.eltavine.duckdetector.core.detector.Detector
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.detector.DetectorSpecificApi
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.features.processsandbox.data.repository.ProcessSandboxRepository
import com.eltavine.duckdetector.features.processsandbox.domain.ProcessSandboxReport
import com.eltavine.duckdetector.features.processsandbox.presentation.ProcessSandboxCardModelMapper
import com.eltavine.duckdetector.features.processsandbox.presentation.model.ProcessSandboxCardModel
import com.eltavine.duckdetector.features.processsandbox.presentation.toDetectorReport

/**
 * Process Sandbox: observes the host process credential quartet, granted capability masks and Seccomp mode through a bounded ACK proc status snapshot, with AOSP Zygote application baseline exceptions.
 *
 * Collected by [ProcessSandboxRepository], judged by `ProcessSandboxReport.toDetectorStatus()` in the domain
 * layer, and described by [ProcessSandboxCardModelMapper].
 */
@DetectorSpecificApi
public object ProcessSandboxDetector : Detector<ProcessSandboxReport, ProcessSandboxCardModel> {
    override val id: DetectorId = DetectorId("process_sandbox")

    override fun createScanner(context: Context): DetectorScanner<ProcessSandboxReport> = ProcessSandboxRepository(context)

    override fun loadingReport(): ProcessSandboxReport = ProcessSandboxReport.loading()

    override fun describe(report: ProcessSandboxReport): ProcessSandboxCardModel = ProcessSandboxCardModelMapper().map(report)

    override fun export(model: ProcessSandboxCardModel): DetectorReport = model.toDetectorReport()
}
