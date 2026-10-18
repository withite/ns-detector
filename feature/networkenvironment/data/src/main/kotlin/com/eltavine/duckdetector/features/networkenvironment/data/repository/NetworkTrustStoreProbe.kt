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

import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreCounts
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.Enumeration
import kotlin.coroutines.cancellation.CancellationException

internal fun interface NetworkTrustStoreProbe {
    fun read(checkCancellation: () -> Unit): NetworkTrustStoreObservation
}

internal fun interface TrustStoreAliasSource {
    /** Null means the AndroidCAStore provider is missing, not an empty store. */
    fun aliases(): Enumeration<out String?>?
}

internal class AndroidTrustStoreAliasSource : TrustStoreAliasSource {
    override fun aliases(): Enumeration<out String?>? {
        val store = try {
            KeyStore.getInstance("AndroidCAStore")
        } catch (_: KeyStoreException) {
            return null
        }
        store.load(null, null)
        return store.aliases()
    }
}

/** Uses the provider rather than system paths, including Android 14+ Conscrypt APEX stores. */
internal class AndroidNetworkTrustStoreProbe(
    private val source: TrustStoreAliasSource = AndroidTrustStoreAliasSource(),
    private val maxAliases: Int = 4096,
) : NetworkTrustStoreProbe {
    init { require(maxAliases in 1..4096) }

    override fun read(checkCancellation: () -> Unit): NetworkTrustStoreObservation = try {
        checkCancellation()
        val aliases = source.aliases()
        checkCancellation()
        if (aliases == null) NetworkTrustStoreObservation(TrustStoreObservationState.PROVIDER_UNAVAILABLE)
        else enumerate(aliases, checkCancellation)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        NetworkTrustStoreObservation(TrustStoreObservationState.PERMISSION_REQUIRED)
    } catch (failure: Exception) {
        // Provider exceptions can contain certificate/alias identifiers; preserve only their type.
        NetworkTrustStoreObservation(TrustStoreObservationState.FAILED, failureName = FailureName.of(failure))
    }

    private fun enumerate(
        aliases: Enumeration<out String?>,
        checkCancellation: () -> Unit,
    ): NetworkTrustStoreObservation {
        var user = 0
        var system = 0
        var unknown = 0
        var truncated = false
        while (true) {
            checkCancellation()
            if (!aliases.hasMoreElements()) break
            if (user + system + unknown >= maxAliases) {
                truncated = true
                break
            }
            val alias = aliases.nextElement()
            when {
                alias == null || alias.length > 256 -> unknown++
                alias.startsWith("user:") && alias.length > 5 -> user++
                alias.startsWith("system:") && alias.length > 7 -> system++
                else -> unknown++
            }
        }
        checkCancellation()
        return NetworkTrustStoreObservation(
            state = when {
                truncated -> TrustStoreObservationState.TRUNCATED
                unknown > 0 -> TrustStoreObservationState.UNRECOGNIZED_ALIASES
                else -> TrustStoreObservationState.OBSERVED
            },
            counts = NetworkTrustStoreCounts(user, system, unknown),
        )
    }
}
