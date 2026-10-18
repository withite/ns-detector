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

package com.eltavine.duckdetector.features.appintegrity.data.repository

import android.content.Context
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.features.appintegrity.domain.AppIntegrityReport
import com.eltavine.duckdetector.features.appintegrity.domain.AppIntegrityStage
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservation
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservationState
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Independent failures preserve evidence from the other sources; cancellation always propagates. */
class AppIntegrityRepository internal constructor(private val probe: AppIntegrityProbe) : DetectorScanner<AppIntegrityReport> {
    constructor(context: Context) : this(AndroidAppIntegrityProbe(context))

    override suspend fun scan(): AppIntegrityReport = withContext(Dispatchers.IO) { collect() }

    internal fun collect(): AppIntegrityReport = AppIntegrityReport(
        stage = AppIntegrityStage.READY,
        build = read(probe::build),
        signing = read(probe::signing),
        policy = read(probe::policy),
        debugger = read(probe::debugger),
        tracerPid = read(probe::tracerPid),
    )

    private fun <T> read(source: () -> AppObservation<T>): AppObservation<T> = try {
        source()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        AppObservation(AppObservationState.UNAVAILABLE)
    } catch (_: FileNotFoundException) {
        AppObservation(AppObservationState.UNAVAILABLE)
    } catch (failure: Exception) {
        // Platform error messages can contain local paths; retain only the stable exception type.
        AppObservation(AppObservationState.FAILED, failureName = FailureName.of(failure))
    }
}
