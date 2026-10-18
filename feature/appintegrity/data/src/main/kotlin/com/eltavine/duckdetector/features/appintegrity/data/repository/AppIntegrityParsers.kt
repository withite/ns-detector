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

import com.eltavine.duckdetector.features.appintegrity.domain.AppObservation
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservationState
import com.eltavine.duckdetector.features.appintegrity.domain.AppSigningPolicy
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object AppIntegrityParsers {
    /** Bounded input is provided by the adapter; coercing strings/numbers is intentionally forbidden. */
    fun signingPolicy(raw: String): AppObservation<AppSigningPolicy> = try {
        require(raw.length <= 8192)
        val tokener = JSONTokener(raw)
        val json = tokener.nextValue() as? JSONObject ?: error("Policy root must be an object")
        require(tokener.nextClean() == 0.toChar())
        require(json.keys().asSequence().toSet() == setOf("version", "allowedPackage", "certificateSha256"))
        require(json.opt("version") is Int && json.getInt("version") == 1)
        val allowedPackage = json.opt("allowedPackage") as? String ?: error("Package must be a string")
        val pins = json.opt("certificateSha256") as? JSONArray ?: error("Pins must be an array")
        require(pins.length() in 1..16)
        val certificates = (0 until pins.length()).map { index ->
            val pin = pins.opt(index) as? String ?: error("Pin must be a string")
            require(pin.matches(Regex("[0-9a-fA-F]{64}")))
            pin.uppercase(Locale.ROOT)
        }.toSet()
        AppObservation.observed(AppSigningPolicy(allowedPackage, certificates))
    } catch (_: Exception) {
        AppObservation(AppObservationState.MALFORMED)
    }

    /** Only the exact kernel field is interpreted. Missing/duplicate/malformed is never PID zero. */
    fun tracerPid(raw: String): AppObservation<Int> {
        val fields = raw.lineSequence().filter { it.startsWith("TracerPid:") }.toList()
        if (fields.isEmpty()) return AppObservation(AppObservationState.UNAVAILABLE)
        if (fields.size != 1) return AppObservation(AppObservationState.MALFORMED)
        val value = fields.single().substringAfter(':').trim()
        if (!value.matches(Regex("[0-9]+"))) return AppObservation(AppObservationState.MALFORMED)
        val pid = value.toIntOrNull() ?: return AppObservation(AppObservationState.MALFORMED)
        return AppObservation.observed(pid)
    }
}
