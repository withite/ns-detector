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

package com.eltavine.duckdetector.features.networkenvironment.domain

enum class NetworkEnvironmentStage { LOADING, READY, FAILED }

/** Public Android connectivity observations at scan time, not a device-integrity verdict. */
data class NetworkEnvironmentReport(
    val stage: NetworkEnvironmentStage,
    val network: NetworkDefaultSnapshot = NetworkDefaultSnapshot(),
    val proxy: NetworkProxyObservation = NetworkProxyObservation(),
    val errorMessage: String? = null,
) {
    val observationStates: List<NetworkObservationState>
        get() = listOf(network.vpn.state, proxy.state, network.privateDns.state)

    val probed: Boolean
        get() = observationStates.any { it == NetworkObservationState.OBSERVED }

    val partiallyEvaluated: Boolean
        get() = probed && observationStates.any { it != NetworkObservationState.OBSERVED }

    companion object {
        fun loading(): NetworkEnvironmentReport = NetworkEnvironmentReport(NetworkEnvironmentStage.LOADING)

        fun failed(message: String): NetworkEnvironmentReport = NetworkEnvironmentReport(
            stage = NetworkEnvironmentStage.FAILED,
            errorMessage = message,
        )
    }
}
