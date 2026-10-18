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

enum class TrustStoreObservationState {
    NOT_EVALUATED,
    OBSERVED,
    PROVIDER_UNAVAILABLE,
    PERMISSION_REQUIRED,
    TRUNCATED,
    UNRECOGNIZED_ALIASES,
    FAILED,
}

/** Entry counts only: no certificate, alias, subject, issuer or fingerprint is retained. */
data class NetworkTrustStoreCounts(
    val userAdded: Int,
    val system: Int,
    val unrecognized: Int = 0,
) {
    init {
        require(userAdded >= 0 && system >= 0 && unrecognized >= 0)
        require(userAdded.toLong() + system + unrecognized <= Int.MAX_VALUE)
    }

    val examined: Int get() = userAdded + system + unrecognized
}

/** AndroidCAStore visibility for this application's user/profile, not this app's TLS policy. */
data class NetworkTrustStoreObservation(
    val state: TrustStoreObservationState = TrustStoreObservationState.NOT_EVALUATED,
    val counts: NetworkTrustStoreCounts? = null,
    val failureName: String? = null,
) {
    init {
        val hasCounts = state == TrustStoreObservationState.OBSERVED ||
            state == TrustStoreObservationState.TRUNCATED || state == TrustStoreObservationState.UNRECOGNIZED_ALIASES
        require(hasCounts == (counts != null))
        if (state == TrustStoreObservationState.OBSERVED) require(counts?.unrecognized == 0)
        if (state == TrustStoreObservationState.UNRECOGNIZED_ALIASES) require(counts?.unrecognized != 0)
    }

    val hasObservation: Boolean get() = counts != null
}
