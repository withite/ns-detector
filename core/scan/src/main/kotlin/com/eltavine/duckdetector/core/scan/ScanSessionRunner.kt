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

package com.eltavine.duckdetector.core.scan

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identity of one scan request made through a [ScanSessionRunner]. */
@JvmInline
public value class ScanSessionId(public val value: Long)

/**
 * Owns the lifecycle of one detector's scans.
 *
 * Probes touch shared process state (Keystore aliases, bound isolated services, timing
 * measurements), so two scans of the same detector must never overlap, and a scan that has
 * started must be allowed to finish its own cleanup rather than be cancelled half way. Requests
 * are therefore serialized: a request publishes its starting state, waits for the previous scan
 * to finish, and may publish its result only while it is still the newest request. Replacing a
 * queued request cancels only its waiting coroutine, keeping one active scan and one pending
 * request even when rescan is tapped repeatedly. An older scan can never overwrite a newer one.
 *
 * Cancelling [scope] still cancels everything, which is how the owner's lifetime ends scans.
 */
public class ScanSessionRunner(
    private val scope: CoroutineScope,
) {
    private val admission = Mutex()
    private val newest = AtomicLong(0)
    private val pendingLock = Any()
    private var pending: PendingScan? = null

    /**
     * Requests a scan.
     *
     * [begin] runs before the request first suspends, so on an immediate dispatcher the starting
     * state is visible synchronously, exactly as when the owner launched the scan itself.
     */
    public fun <T> launch(
        begin: () -> Unit,
        collect: suspend () -> T,
        publish: (T) -> Unit,
    ): ScanSessionId {
        val session = ScanSessionId(newest.incrementAndGet())
        // Register before starting: an immediate dispatcher may reach collect before launch
        // returns. A running scan clears this slot before collection and is never replaced.
        val request = scope.launch(start = CoroutineStart.LAZY) {
            begin()
            admission.withLock {
                synchronized(pendingLock) {
                    if (pending?.session == session) pending = null
                }
                if (!isNewest(session)) {
                    return@withLock
                }
                val result = collect()
                // A platform adapter may catch cancellation while cleaning up a probe. It must
                // not publish into an owner whose scope has already ended.
                currentCoroutineContext().ensureActive()
                if (isNewest(session)) {
                    publish(result)
                }
            }
        }
        val replaced = synchronized(pendingLock) {
            if (isNewest(session)) {
                val previous = pending
                pending = PendingScan(session, request)
                previous?.job
            } else {
                request
            }
        }
        replaced?.cancel()
        request.start()
        return session
    }

    public fun isNewest(session: ScanSessionId): Boolean = newest.get() == session.value

    private class PendingScan(val session: ScanSessionId, val job: Job)
}
