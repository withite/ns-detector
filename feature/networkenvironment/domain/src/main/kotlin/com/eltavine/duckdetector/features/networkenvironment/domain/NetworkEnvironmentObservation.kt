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

/** Availability belongs to each source: missing evidence never becomes a disabled setting. */
enum class NetworkObservationState {
    NOT_EVALUATED,
    OBSERVED,
    NO_DEFAULT_NETWORK,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    UNSUPPORTED,
    NETWORK_CHANGED,
    FAILED,
}

enum class NetworkProxyMode { NONE, MANUAL, AUTOMATIC }

enum class NetworkPrivateDnsMode { INACTIVE, ACTIVE_OPPORTUNISTIC, ACTIVE_STRICT, STRICT_NOT_ACTIVE }

data class NetworkVpnObservation(
    val state: NetworkObservationState = NetworkObservationState.NOT_EVALUATED,
    val usesVpn: Boolean? = null,
    val failureName: String? = null,
) {
    init {
        require((state == NetworkObservationState.OBSERVED) == (usesVpn != null))
    }
}

data class NetworkProxyObservation(
    val state: NetworkObservationState = NetworkObservationState.NOT_EVALUATED,
    val mode: NetworkProxyMode? = null,
    val failureName: String? = null,
) {
    init {
        require((state == NetworkObservationState.OBSERVED) == (mode != null))
    }
}

data class NetworkPrivateDnsObservation(
    val state: NetworkObservationState = NetworkObservationState.NOT_EVALUATED,
    val mode: NetworkPrivateDnsMode? = null,
    val failureName: String? = null,
) {
    init {
        require((state == NetworkObservationState.OBSERVED) == (mode != null))
    }
}

/** VPN and DNS refer to the same default-network handle; no addresses or hostnames are retained. */
data class NetworkDefaultSnapshot(
    val vpn: NetworkVpnObservation = NetworkVpnObservation(),
    val privateDns: NetworkPrivateDnsObservation = NetworkPrivateDnsObservation(),
) {
    companion object {
        fun unavailable(state: NetworkObservationState, failureName: String? = null): NetworkDefaultSnapshot =
            NetworkDefaultSnapshot(
                vpn = NetworkVpnObservation(state = state, failureName = failureName),
                privateDns = NetworkPrivateDnsObservation(state = state, failureName = failureName),
            )
    }
}
