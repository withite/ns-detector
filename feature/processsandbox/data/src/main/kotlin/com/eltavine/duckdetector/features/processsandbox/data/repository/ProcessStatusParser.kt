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

package com.eltavine.duckdetector.features.processsandbox.data.repository

import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservation
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservationState
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxStatusSnapshot
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxUidQuartet

/** ACK formats these exact fields as decimal UID/mode and unsigned hexadecimal capability masks. */
internal object ProcessStatusParser {
    private val keys = setOf("Uid", "CapEff", "CapPrm", "CapAmb", "Seccomp")
    private val whitespace = Regex("\\s+")
    private val decimal = Regex("[0-9]+")
    private val hexadecimal = Regex("[0-9a-fA-F]{1,16}")

    fun parse(bytes: ByteArray): SandboxStatusSnapshot {
        if (bytes.size > MAX_STATUS_BYTES || bytes.any { it == 0.toByte() }) {
            return SandboxStatusSnapshot.unavailable(SandboxObservationState.MALFORMED)
        }
        val fields = mutableMapOf<String, MutableList<String>>()
        bytes.toString(Charsets.US_ASCII).lineSequence().forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon)
                if (key in keys) fields.getOrPut(key) { mutableListOf() }.add(line.substring(colon + 1).trim())
            }
        }
        return SandboxStatusSnapshot(
            uids = field(fields, "Uid") { raw ->
                val values = raw.split(whitespace)
                if (values.size != 4 || values.any { !decimal.matches(it) }) null else {
                    val parsed = values.map { it.toLongOrNull()?.takeIf { uid -> uid in 0..0xFFFF_FFFFL } }
                    if (parsed.any { it == null }) null else SandboxUidQuartet(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!)
                }
            },
            effectiveCapabilities = capability(fields, "CapEff"),
            permittedCapabilities = capability(fields, "CapPrm"),
            ambientCapabilities = capability(fields, "CapAmb"),
            seccomp = field(fields, "Seccomp") { if (decimal.matches(it)) it.toIntOrNull() else null },
        )
    }

    private fun capability(fields: Map<String, List<String>>, key: String): SandboxObservation<ULong> =
        field(fields, key) { if (hexadecimal.matches(it)) it.toULongOrNull(16) else null }

    private fun <T> field(fields: Map<String, List<String>>, key: String, parse: (String) -> T?): SandboxObservation<T> {
        val values = fields[key] ?: return SandboxObservation(SandboxObservationState.MISSING)
        if (values.size != 1) return SandboxObservation(SandboxObservationState.MALFORMED)
        val value = parse(values.single()) ?: return SandboxObservation(SandboxObservationState.MALFORMED)
        return SandboxObservation.observed(value)
    }
}
