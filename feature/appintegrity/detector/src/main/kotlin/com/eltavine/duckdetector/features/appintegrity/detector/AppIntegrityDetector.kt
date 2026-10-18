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

package com.eltavine.duckdetector.features.appintegrity.detector

import android.content.Context
import com.eltavine.duckdetector.core.detector.Detector
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.detector.DetectorSpecificApi
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.features.appintegrity.data.repository.AppIntegrityRepository
import com.eltavine.duckdetector.features.appintegrity.domain.AppIntegrityReport
import com.eltavine.duckdetector.features.appintegrity.presentation.AppIntegrityCardModelMapper
import com.eltavine.duckdetector.features.appintegrity.presentation.model.AppIntegrityCardModel
import com.eltavine.duckdetector.features.appintegrity.presentation.toDetectorReport

/**
 * Application Integrity: observes the current host signing certificate and debug state through PackageManager, Debug APIs and the self-process procfs status, without treating local diagnostics as device trust proof.
 *
 * Collected by [AppIntegrityRepository], judged by `AppIntegrityReport.toDetectorStatus()` in the domain
 * layer, and described by [AppIntegrityCardModelMapper].
 */
@DetectorSpecificApi
public object AppIntegrityDetector : Detector<AppIntegrityReport, AppIntegrityCardModel> {
    override val id: DetectorId = DetectorId("app_integrity")

    override fun createScanner(context: Context): DetectorScanner<AppIntegrityReport> = AppIntegrityRepository(context)

    override fun loadingReport(): AppIntegrityReport = AppIntegrityReport.loading()

    override fun describe(report: AppIntegrityReport): AppIntegrityCardModel = AppIntegrityCardModelMapper().map(report)

    override fun export(model: AppIntegrityCardModel): DetectorReport = model.toDetectorReport()
}
