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

package com.eltavine.duckdetector.features.networkenvironment.detector

import android.content.Context
import com.eltavine.duckdetector.core.detector.Detector
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.detector.DetectorSpecificApi
import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.features.networkenvironment.data.repository.NetworkEnvironmentRepository
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentReport
import com.eltavine.duckdetector.features.networkenvironment.presentation.NetworkEnvironmentCardModelMapper
import com.eltavine.duckdetector.features.networkenvironment.presentation.model.NetworkEnvironmentCardModel
import com.eltavine.duckdetector.features.networkenvironment.presentation.toDetectorReport

/**
 * Network Environment: reports application default network VPN transport and Android recommended proxy and Private DNS usage through public connectivity APIs as contextual information.
 *
 * Collected by [NetworkEnvironmentRepository], judged by `NetworkEnvironmentReport.toDetectorStatus()` in the domain
 * layer, and described by [NetworkEnvironmentCardModelMapper].
 */
@DetectorSpecificApi
public object NetworkEnvironmentDetector : Detector<NetworkEnvironmentReport, NetworkEnvironmentCardModel> {
    override val id: DetectorId = DetectorId("network_environment")

    override fun createScanner(context: Context): DetectorScanner<NetworkEnvironmentReport> = NetworkEnvironmentRepository(context)

    override fun loadingReport(): NetworkEnvironmentReport = NetworkEnvironmentReport.loading()

    override fun describe(report: NetworkEnvironmentReport): NetworkEnvironmentCardModel = NetworkEnvironmentCardModelMapper().map(report)

    override fun export(model: NetworkEnvironmentCardModel): DetectorReport = model.toDetectorReport()
}
