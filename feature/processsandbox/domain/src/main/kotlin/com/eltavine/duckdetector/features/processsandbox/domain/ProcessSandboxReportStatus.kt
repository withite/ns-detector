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

package com.eltavine.duckdetector.features.processsandbox.domain

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind

/** AOSP grants CAP_BLOCK_SUSPEND to the wakelock GID; nonzero masks alone are not findings. */
private val allowedApplicationCapabilities: ULong = 1uL shl 36

fun ProcessSandboxReport.hasUidInconsistency(): Boolean {
    val identity = host.value ?: return false
    return stage == ProcessSandboxStage.READY && identity.ordinaryApplication &&
        snapshot.uids.value?.matches(identity.processUid) == false
}

fun ProcessSandboxReport.hasExpandedCapabilities(): Boolean =
    stage == ProcessSandboxStage.READY && host.value?.ordinaryApplication == true &&
        listOf(snapshot.effectiveCapabilities, snapshot.permittedCapabilities, snapshot.ambientCapabilities)
            .any { it.value?.and(allowedApplicationCapabilities.inv())?.let { unexpected -> unexpected != 0uL } == true }

fun ProcessSandboxReport.hasUnknownSeccompMode(): Boolean = snapshot.seccomp.value?.let { it !in 0..2 } == true

/** Only ordinary installed application credentials are compared with the AOSP app baseline. */
fun ProcessSandboxReport.toDetectorStatus(): DetectorStatus = when {
    hasUidInconsistency() || hasExpandedCapabilities() -> DetectorStatus.warning()
    stage == ProcessSandboxStage.FAILED || observationStates.any {
        it == SandboxObservationState.FAILED || it == SandboxObservationState.MALFORMED
    } -> DetectorStatus.info(InfoKind.ERROR)
    stage != ProcessSandboxStage.READY || hasUnknownSeccompMode() ||
        observationStates.any { it != SandboxObservationState.OBSERVED } -> DetectorStatus.info(InfoKind.SUPPORT)
    else -> DetectorStatus.info(InfoKind.CONTEXT)
}

fun SandboxObservation<*>.toObservationStatus(): DetectorStatus = when (state) {
    SandboxObservationState.OBSERVED -> DetectorStatus.info(InfoKind.CONTEXT)
    SandboxObservationState.MALFORMED, SandboxObservationState.FAILED -> DetectorStatus.info(InfoKind.ERROR)
    else -> DetectorStatus.info(InfoKind.SUPPORT)
}

fun ProcessSandboxReport.uidStatus(): DetectorStatus = when {
    hasUidInconsistency() -> DetectorStatus.warning()
    snapshot.uids.state != SandboxObservationState.OBSERVED -> snapshot.uids.toObservationStatus()
    host.state != SandboxObservationState.OBSERVED -> host.toObservationStatus()
    else -> DetectorStatus.info(InfoKind.CONTEXT)
}

fun ProcessSandboxReport.capabilityStatus(observation: SandboxObservation<ULong>): DetectorStatus = when {
    observation.state != SandboxObservationState.OBSERVED -> observation.toObservationStatus()
    host.state != SandboxObservationState.OBSERVED -> host.toObservationStatus()
    stage == ProcessSandboxStage.READY && host.value?.ordinaryApplication == true &&
        observation.value?.and(allowedApplicationCapabilities.inv()) != 0uL -> DetectorStatus.warning()
    else -> DetectorStatus.info(InfoKind.CONTEXT)
}

fun ProcessSandboxReport.seccompStatus(): DetectorStatus = when {
    hasUnknownSeccompMode() -> DetectorStatus.info(InfoKind.SUPPORT)
    else -> snapshot.seccomp.toObservationStatus()
}
