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

package com.eltavine.duckdetector.features.networkenvironment.data.repository

import android.content.Context
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkDefaultSnapshot
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentReport
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentStage
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkObservationState
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Independent source failures preserve observations from the remaining source. */
class NetworkEnvironmentRepository internal constructor(
    private val probe: NetworkEnvironmentProbe,
    private val trustStoreProbe: NetworkTrustStoreProbe,
) : DetectorScanner<NetworkEnvironmentReport> {
    constructor(context: Context) : this(AndroidNetworkEnvironmentProbe(context), AndroidNetworkTrustStoreProbe())

    override suspend fun scan(): NetworkEnvironmentReport = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        collect { coroutineContext.ensureActive() }
    }

    internal fun collect(checkCancellation: () -> Unit = {}): NetworkEnvironmentReport {
        checkCancellation()
        val network = readNetwork()
        checkCancellation()
        val proxy = readProxy()
        checkCancellation()
        val trustStore = readTrustStore(checkCancellation)
        checkCancellation()
        return NetworkEnvironmentReport(
            stage = NetworkEnvironmentStage.READY,
            network = network,
            proxy = proxy,
            trustStore = trustStore,
        )
    }

    private fun readNetwork(): NetworkDefaultSnapshot = try {
        probe.readDefaultNetwork()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        NetworkDefaultSnapshot.unavailable(NetworkObservationState.PERMISSION_REQUIRED)
    } catch (failure: Exception) {
        // Exception messages can contain network identifiers, so retain only the stable failure type.
        NetworkDefaultSnapshot.unavailable(NetworkObservationState.FAILED, FailureName.of(failure))
    }

    private fun readProxy(): NetworkProxyObservation = try {
        probe.readProxy()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        NetworkProxyObservation(state = NetworkObservationState.PERMISSION_REQUIRED)
    } catch (failure: Exception) {
        NetworkProxyObservation(state = NetworkObservationState.FAILED, failureName = FailureName.of(failure))
    }

    private fun readTrustStore(checkCancellation: () -> Unit): NetworkTrustStoreObservation = try {
        trustStoreProbe.read(checkCancellation)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        NetworkTrustStoreObservation(TrustStoreObservationState.PERMISSION_REQUIRED)
    } catch (failure: Exception) {
        NetworkTrustStoreObservation(TrustStoreObservationState.FAILED, failureName = FailureName.of(failure))
    }
}
