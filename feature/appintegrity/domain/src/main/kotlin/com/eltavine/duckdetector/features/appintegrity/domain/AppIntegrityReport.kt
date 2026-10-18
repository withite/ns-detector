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

package com.eltavine.duckdetector.features.appintegrity.domain

enum class AppIntegrityStage { LOADING, READY, FAILED }
enum class AppObservationState { NOT_EVALUATED, OBSERVED, MISSING, UNAVAILABLE, UNSUPPORTED, MALFORMED, FAILED }

/** Unavailable sources cannot carry an invented value, including false or zero. */
data class AppObservation<T>(
    val state: AppObservationState = AppObservationState.NOT_EVALUATED,
    val value: T? = null,
    val failureName: String? = null,
) {
    init {
        require((state == AppObservationState.OBSERVED) == (value != null))
    }
    companion object {
        fun <T> observed(value: T): AppObservation<T> = AppObservation(AppObservationState.OBSERVED, value)
    }
}

data class AppBuildSnapshot(val packageName: String, val debuggable: Boolean, val testOnly: Boolean) {
    val developmentBuild: Boolean get() = debuggable || testOnly
}
data class AppDebuggerSnapshot(val attached: Boolean, val waiting: Boolean)

data class AppSigningSnapshot(
    /** Current signer(s) only. Old rotation certificates are never included here. */
    val currentCertificateSha256: Set<String>,
    val multipleSigners: Boolean,
    val rotationHistoryCount: Int,
) {
    init {
        require(currentCertificateSha256.isNotEmpty())
        require(currentCertificateSha256.all { it.matches(Regex("[0-9A-F]{64}")) })
        require(multipleSigners == (currentCertificateSha256.size > 1))
        require(rotationHistoryCount >= 0)
    }
}

data class AppSigningPolicy(val allowedPackage: String, val certificateSha256: Set<String>) {
    init {
        require(allowedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        require(certificateSha256.isNotEmpty())
        require(certificateSha256.all { it.matches(Regex("[0-9A-F]{64}")) })
    }
}

data class AppIntegrityReport(
    val stage: AppIntegrityStage,
    val build: AppObservation<AppBuildSnapshot> = AppObservation(),
    val signing: AppObservation<AppSigningSnapshot> = AppObservation(),
    val policy: AppObservation<AppSigningPolicy> = AppObservation(),
    val debugger: AppObservation<AppDebuggerSnapshot> = AppObservation(),
    val tracerPid: AppObservation<Int> = AppObservation(),
    val errorMessage: String? = null,
) {
    val sourceStates: List<AppObservationState>
        get() = listOf(build.state, signing.state, policy.state, debugger.state, tracerPid.state)
    val probed: Boolean get() = sourceStates.any { it == AppObservationState.OBSERVED }
    companion object {
        fun loading(): AppIntegrityReport = AppIntegrityReport(AppIntegrityStage.LOADING)
        fun failed(message: String): AppIntegrityReport = AppIntegrityReport(AppIntegrityStage.FAILED, errorMessage = message)
    }
}
