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

package com.eltavine.duckdetector.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NsVersionCodeTest {
    @Test
    fun `accepts Android version code bounds`() {
        assertEquals(10000, parseNsVersionCode("10000"))
        assertEquals(2_100_000_000, parseNsVersionCode("2100000000"))
    }

    @Test
    fun `rejects overflow non integers and zero`() {
        listOf("0", "-1", "2100000001", "not-a-code").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { parseNsVersionCode(value) }
        }
    }
}
