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

import com.eltavine.duckdetector.features.appintegrity.domain.AppObservationState
import org.junit.Assert.*
import org.junit.Test

class AppIntegrityParsersTest {
    private val pin = "ab".repeat(32)
    private fun policy(pins: String = "\"$pin\"") =
        """{"version":1,"allowedPackage":"com.example.host","certificateSha256":[$pins]}"""

    @Test fun `valid policy normalizes complete SHA256 fingerprints`() {
        val result = AppIntegrityParsers.signingPolicy(policy())
        assertEquals(AppObservationState.OBSERVED, result.state)
        assertEquals("com.example.host", result.value?.allowedPackage)
        assertEquals(setOf("AB".repeat(32)), result.value?.certificateSha256)
    }

    @Test fun `explicit current rotation pins accepted with no historical inference`() {
        val result = AppIntegrityParsers.signingPolicy(policy("\"$pin\",\"${"C".repeat(64)}\""))
        assertEquals(2, result.value?.certificateSha256?.size)
    }

    @Test fun `missing mistyped unsupported fields and coercion cannot create a pin policy`() {
        val invalid = listOf(
            "{}", "[]", "null", "garbage", policy() + " trailing",
            policy().replace("\"version\":1", "\"version\":2"),
            policy().replace("\"version\":1", "\"version\":\"1\""),
            policy().replace("\"version\":1", "\"version\":1.0"),
            policy().replace("com.example.host", "com.example.*"),
            policy().replace("com.example.host", "com.example.host "),
            policy().replace("com.example.host", ""),
            policy(""), policy("12"), policy("null"), policy("\"${"G".repeat(64)}\""),
            policy("\"${"a".repeat(63)}\""), policy("\"${"AA:".repeat(21)}AA\""),
            policy().replace("\"certificateSha256\"", "\"certificateSha265\""),
            policy().replace("{", "{\"extra\":true,"), " ".repeat(8193),
        )
        for (raw in invalid) {
            val result = AppIntegrityParsers.signingPolicy(raw)
            assertEquals(raw.take(100), AppObservationState.MALFORMED, result.state)
            assertNull(result.value)
        }
    }

    @Test fun `tracer accepts exact zero and positive integer fields only`() {
        assertEquals(0, AppIntegrityParsers.tracerPid("Name:\tapp\nTracerPid:\t0\nUid:\t10000").value)
        assertEquals(321, AppIntegrityParsers.tracerPid("TracerPid:\t321\n").value)
        assertEquals(AppObservationState.UNAVAILABLE, AppIntegrityParsers.tracerPid("Name: app").state)
        for (raw in listOf("TracerPid:", "TracerPid: -1", "TracerPid: 1x", "TracerPid: 1 2",
            "TracerPid: 2147483648", "TracerPid: 0\nTracerPid: 9")) {
            val result = AppIntegrityParsers.tracerPid(raw)
            assertEquals(raw, AppObservationState.MALFORMED, result.state)
            assertNull(result.value)
        }
        assertEquals(AppObservationState.UNAVAILABLE, AppIntegrityParsers.tracerPid("FakeTracerPid: 0").state)
    }
}
