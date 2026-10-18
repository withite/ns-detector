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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxHost
import java.io.FileInputStream
import java.io.InputStream

internal class AndroidProcessSandboxProbe(context: Context) : ProcessSandboxProbe {
    private val context = context.applicationContext

    override fun host(): SandboxHost {
        val flags = context.applicationInfo.flags
        return SandboxHost(
            processUid = Process.myUid(),
            systemApplication = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        )
    }

    override fun status(checkpoint: () -> Unit): ByteArray =
        FileInputStream("/proc/thread-self/status").use { readBoundedStatus(it, checkpoint) }
}

internal class StatusSnapshotTooLarge : Exception()

/** One bounded read; no readText/readBytes, retries, process enumeration or per-field file opens. */
internal fun readBoundedStatus(input: InputStream, checkpoint: () -> Unit = {}): ByteArray {
    val buffer = ByteArray(MAX_STATUS_BYTES + 1)
    var count = 0
    while (count < buffer.size) {
        checkpoint()
        val read = input.read(buffer, count, minOf(4096, buffer.size - count))
        if (read < 0) break
        if (read == 0) throw java.io.IOException("Empty status read")
        count += read
    }
    checkpoint()
    if (count > MAX_STATUS_BYTES) throw StatusSnapshotTooLarge()
    return buffer.copyOf(count)
}

internal const val MAX_STATUS_BYTES: Int = 64 * 1024
