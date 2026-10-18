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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkDefaultSnapshot
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkObservationState
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkVpnObservation
import kotlin.coroutines.cancellation.CancellationException

/** Reads only public APIs; no sockets, DNS requests, package inventory or interface enumeration. */
internal class AndroidNetworkEnvironmentProbe(context: Context) : NetworkEnvironmentProbe {
    private val context = context.applicationContext

    override fun readDefaultNetwork(): NetworkDefaultSnapshot {
        if (Build.VERSION.SDK_INT < 23) {
            return NetworkDefaultSnapshot.unavailable(NetworkObservationState.UNSUPPORTED)
        }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return NetworkDefaultSnapshot.unavailable(NetworkObservationState.PERMISSION_REQUIRED)
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkDefaultSnapshot.unavailable(NetworkObservationState.UNAVAILABLE)
        val network = connectivity.activeNetwork
            ?: return NetworkDefaultSnapshot.unavailable(NetworkObservationState.NO_DEFAULT_NETWORK)
        val vpn = readVpn { connectivity.getNetworkCapabilities(network) }
        val dns = readPrivateDns { connectivity.getLinkProperties(network) }
        // A disconnect or default-network switch must not read as an absent VPN or disabled DNS.
        if (connectivity.activeNetwork != network) {
            return NetworkDefaultSnapshot.unavailable(NetworkObservationState.NETWORK_CHANGED)
        }
        return NetworkDefaultSnapshot(vpn = vpn, privateDns = dns)
    }

    override fun readProxy(): NetworkProxyObservation {
        if (Build.VERSION.SDK_INT < 23) {
            return NetworkProxyObservation(state = NetworkObservationState.UNSUPPORTED)
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkProxyObservation(state = NetworkObservationState.UNAVAILABLE)
        val proxy = connectivity.defaultProxy
        // Deliberately retain neither a proxy endpoint nor a PAC URL in the report.
        val mode = if (proxy == null) {
            NetworkProxyMode.NONE
        } else {
            val pac = proxy.pacFileUrl
            if (pac != null && pac != Uri.EMPTY) NetworkProxyMode.AUTOMATIC else NetworkProxyMode.MANUAL
        }
        return NetworkProxyObservation(state = NetworkObservationState.OBSERVED, mode = mode)
    }

    private fun readVpn(read: () -> NetworkCapabilities?): NetworkVpnObservation = try {
        val capabilities = read()
        if (capabilities == null) NetworkVpnObservation(state = NetworkObservationState.UNAVAILABLE)
        else NetworkVpnObservation(
            state = NetworkObservationState.OBSERVED,
            usesVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        NetworkVpnObservation(state = NetworkObservationState.PERMISSION_REQUIRED)
    } catch (failure: Exception) {
        NetworkVpnObservation(state = NetworkObservationState.FAILED, failureName = FailureName.of(failure))
    }

    private fun readPrivateDns(read: () -> android.net.LinkProperties?): NetworkPrivateDnsObservation {
        if (Build.VERSION.SDK_INT < 28) {
            return NetworkPrivateDnsObservation(state = NetworkObservationState.UNSUPPORTED)
        }
        return try {
            val properties = read()
            if (properties == null) NetworkPrivateDnsObservation(state = NetworkObservationState.UNAVAILABLE)
            else NetworkPrivateDnsObservation(
                state = NetworkObservationState.OBSERVED,
                mode = when {
                    properties.isPrivateDnsActive && properties.privateDnsServerName != null -> NetworkPrivateDnsMode.ACTIVE_STRICT
                    properties.isPrivateDnsActive -> NetworkPrivateDnsMode.ACTIVE_OPPORTUNISTIC
                    properties.privateDnsServerName != null -> NetworkPrivateDnsMode.STRICT_NOT_ACTIVE
                    else -> NetworkPrivateDnsMode.INACTIVE
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            NetworkPrivateDnsObservation(state = NetworkObservationState.PERMISSION_REQUIRED)
        } catch (failure: Exception) {
            NetworkPrivateDnsObservation(state = NetworkObservationState.FAILED, failureName = FailureName.of(failure))
        }
    }
}
