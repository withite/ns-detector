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
import com.eltavine.duckdetector.core.detector.DetectorScanner
import com.eltavine.duckdetector.core.evidence.FailureName
import com.eltavine.duckdetector.features.processsandbox.domain.ProcessSandboxReport
import com.eltavine.duckdetector.features.processsandbox.domain.ProcessSandboxStage
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxHost
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservation
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservationState
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxStatusSnapshot
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ProcessSandboxRepository internal constructor(private val probe: ProcessSandboxProbe) : DetectorScanner<ProcessSandboxReport> {
    constructor(context: Context) : this(AndroidProcessSandboxProbe(context))

    override suspend fun scan(): ProcessSandboxReport = withContext(Dispatchers.IO) {
        collect { coroutineContext.ensureActive() }
    }

    internal fun collect(checkpoint: () -> Unit = {}): ProcessSandboxReport {
        checkpoint()
        val firstHost = readHost()
        val status = readStatus(checkpoint)
        val lastHost = readHost()
        val firstIdentity = firstHost.value
        val lastIdentity = lastHost.value
        val host: SandboxObservation<SandboxHost> = if (firstIdentity != null && lastIdentity != null &&
            firstIdentity.processUid != lastIdentity.processUid) {
            SandboxObservation(SandboxObservationState.IDENTITY_CHANGED)
        } else if (lastHost.state != SandboxObservationState.OBSERVED) {
            lastHost
        } else {
            firstHost
        }
        checkpoint()
        return ProcessSandboxReport(ProcessSandboxStage.READY, host, status)
    }

    private fun readHost(): SandboxObservation<SandboxHost> = try {
        SandboxObservation.observed(probe.host())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        SandboxObservation(SandboxObservationState.PERMISSION_DENIED)
    } catch (failure: Exception) {
        SandboxObservation(SandboxObservationState.FAILED, failureName = FailureName.of(failure))
    }

    private fun readStatus(checkpoint: () -> Unit): SandboxStatusSnapshot = try {
        ProcessStatusParser.parse(probe.status(checkpoint))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        SandboxStatusSnapshot.unavailable(SandboxObservationState.PERMISSION_DENIED)
    } catch (_: FileNotFoundException) {
        // FileInputStream uses this for missing paths and access denials; do not guess which.
        SandboxStatusSnapshot.unavailable(SandboxObservationState.UNAVAILABLE)
    } catch (_: StatusSnapshotTooLarge) {
        SandboxStatusSnapshot.unavailable(SandboxObservationState.MALFORMED)
    } catch (failure: Exception) {
        // Failure names are stable; exception messages and raw proc text never enter the report.
        SandboxStatusSnapshot.unavailable(SandboxObservationState.FAILED, FailureName.of(failure))
    }
}
