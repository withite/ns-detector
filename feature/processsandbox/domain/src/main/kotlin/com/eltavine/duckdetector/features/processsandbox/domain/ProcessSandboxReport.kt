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

package com.eltavine.duckdetector.features.processsandbox.domain

enum class ProcessSandboxStage { LOADING, READY, FAILED }

enum class SandboxObservationState {
    NOT_EVALUATED, OBSERVED, MISSING, MALFORMED, PERMISSION_DENIED, UNAVAILABLE, UNSUPPORTED, IDENTITY_CHANGED, FAILED,
}

/** Unreadable and malformed fields have no value and can never become zero-valued evidence. */
data class SandboxObservation<T>(
    val state: SandboxObservationState = SandboxObservationState.NOT_EVALUATED,
    val value: T? = null,
    val failureName: String? = null,
) {
    init { require((state == SandboxObservationState.OBSERVED) == (value != null)) }

    companion object {
        fun <T> observed(value: T): SandboxObservation<T> = SandboxObservation(SandboxObservationState.OBSERVED, value)
    }
}

data class SandboxHost(val processUid: Int, val systemApplication: Boolean) {
    init { require(processUid >= 0) }

    /** AOSP multiuser appId, excluding system, isolated and SDK-sandbox identities. */
    val ordinaryApplication: Boolean
        get() = !systemApplication && processUid % 100_000 in 10_000..19_999
}

data class SandboxUidQuartet(val real: Long, val effective: Long, val saved: Long, val filesystem: Long) {
    init { require(listOf(real, effective, saved, filesystem).all { it in 0..0xFFFF_FFFFL }) }

    fun matches(uid: Int): Boolean = listOf(real, effective, saved, filesystem).all { it == uid.toLong() }
}

/** These masks are granted sets; the capability bounding set is deliberately not collected. */
data class SandboxStatusSnapshot(
    val uids: SandboxObservation<SandboxUidQuartet> = SandboxObservation(),
    val effectiveCapabilities: SandboxObservation<ULong> = SandboxObservation(),
    val permittedCapabilities: SandboxObservation<ULong> = SandboxObservation(),
    val ambientCapabilities: SandboxObservation<ULong> = SandboxObservation(),
    val seccomp: SandboxObservation<Int> = SandboxObservation(),
) {
    val states: List<SandboxObservationState>
        get() = listOf(uids.state, effectiveCapabilities.state, permittedCapabilities.state, ambientCapabilities.state, seccomp.state)

    companion object {
        fun unavailable(state: SandboxObservationState, failureName: String? = null): SandboxStatusSnapshot =
            SandboxStatusSnapshot(
                SandboxObservation(state, failureName = failureName),
                SandboxObservation(state, failureName = failureName),
                SandboxObservation(state, failureName = failureName),
                SandboxObservation(state, failureName = failureName),
                SandboxObservation(state, failureName = failureName),
            )
    }
}

/** A bounded proc snapshot, not an attestation or a whole-device sandbox verdict. */
data class ProcessSandboxReport(
    val stage: ProcessSandboxStage,
    val host: SandboxObservation<SandboxHost> = SandboxObservation(),
    val snapshot: SandboxStatusSnapshot = SandboxStatusSnapshot(),
    val errorMessage: String? = null,
) {
    val observationStates: List<SandboxObservationState> get() = listOf(host.state) + snapshot.states
    val probed: Boolean get() = snapshot.states.any { it == SandboxObservationState.OBSERVED }

    companion object {
        fun loading(): ProcessSandboxReport = ProcessSandboxReport(ProcessSandboxStage.LOADING)
        fun failed(message: String): ProcessSandboxReport = ProcessSandboxReport(ProcessSandboxStage.FAILED, errorMessage = message)
    }
}
