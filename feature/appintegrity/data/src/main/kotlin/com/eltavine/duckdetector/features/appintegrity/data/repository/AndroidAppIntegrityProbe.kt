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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.eltavine.duckdetector.features.appintegrity.domain.AppBuildSnapshot
import com.eltavine.duckdetector.features.appintegrity.domain.AppDebuggerSnapshot
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservation
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservationState
import com.eltavine.duckdetector.features.appintegrity.domain.AppSigningPolicy
import com.eltavine.duckdetector.features.appintegrity.domain.AppSigningSnapshot
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Public APIs and a bounded read of this process only; no package enumeration or root requests. */
internal class AndroidAppIntegrityProbe(context: Context) : AppIntegrityProbe {
    private val context = context.applicationContext

    override fun build(): AppObservation<AppBuildSnapshot> {
        val flags = context.applicationInfo.flags
        return AppObservation.observed(AppBuildSnapshot(
            packageName = context.packageName,
            debuggable = flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            testOnly = flags and ApplicationInfo.FLAG_TEST_ONLY != 0,
        ))
    }

    override fun signing(): AppObservation<AppSigningSnapshot> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return AppObservation(AppObservationState.UNSUPPORTED)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (info.packageName != context.packageName) return AppObservation(AppObservationState.MALFORMED)
        val signing = info.signingInfo ?: return AppObservation(AppObservationState.UNAVAILABLE)
        val multiple = signing.hasMultipleSigners()
        val history = if (multiple) emptyList() else signing.signingCertificateHistory?.toList().orEmpty()
        // Android returns single-signer rotation history oldest first, current certificate last.
        val current = if (multiple) signing.apkContentsSigners?.toList().orEmpty() else history.takeLast(1)
        if (current.isEmpty()) return AppObservation(AppObservationState.UNAVAILABLE)
        val fingerprints = current.map { signature ->
            val bytes = signature.toByteArray()
            require(bytes.isNotEmpty())
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
                val value = byte.toInt() and 0xff
                HEX[value ushr 4].toString() + HEX[value and 15]
            }
        }.toSet()
        return AppObservation.observed(AppSigningSnapshot(fingerprints, multiple, history.size))
    }

    override fun policy(): AppObservation<AppSigningPolicy> {
        val raw = try {
            context.assets.open(POLICY_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8193)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count > 8192) return AppObservation(AppObservationState.MALFORMED)
                String(buffer, 0, count)
            }
        } catch (_: FileNotFoundException) {
            return AppObservation(AppObservationState.MISSING)
        }
        return AppIntegrityParsers.signingPolicy(raw)
    }

    override fun debugger(): AppObservation<AppDebuggerSnapshot> = AppObservation.observed(
        AppDebuggerSnapshot(Debug.isDebuggerConnected(), Debug.waitingForDebugger()),
    )

    override fun tracerPid(): AppObservation<Int> {
        val raw = File("/proc/self/status").bufferedReader().use { reader ->
            val buffer = CharArray(65537)
            var count = 0
            while (count < buffer.size) {
                val read = reader.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            if (count > 65536) return AppObservation(AppObservationState.MALFORMED)
            String(buffer, 0, count)
        }
        return AppIntegrityParsers.tracerPid(raw)
    }

    private companion object {
        const val POLICY_ASSET = "ns_signing_policy.json"
        const val HEX = "0123456789ABCDEF"
    }
}
