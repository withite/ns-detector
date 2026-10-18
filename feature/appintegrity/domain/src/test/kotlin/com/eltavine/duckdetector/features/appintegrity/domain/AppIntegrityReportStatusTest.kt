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

package com.eltavine.duckdetector.features.appintegrity.domain

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import org.junit.Assert.*
import org.junit.Test

class AppIntegrityReportStatusTest {
    private val pin = "A".repeat(64)
    private val other = "B".repeat(64)
    private fun ready() = AppIntegrityReport(
        stage = AppIntegrityStage.READY,
        build = AppObservation.observed(AppBuildSnapshot("com.example.host", false, false)),
        signing = AppObservation.observed(AppSigningSnapshot(setOf(pin), false, 1)),
        policy = AppObservation.observed(AppSigningPolicy("com.example.host", setOf(pin))),
        debugger = AppObservation.observed(AppDebuggerSnapshot(false, false)),
        tracerPid = AppObservation.observed(0),
    )

    @Test fun `matching signer remains local context never all clear`() {
        assertEquals(AppSignatureAssessment.MATCHED, ready().signatureAssessment())
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), ready().toDetectorStatus())
    }

    @Test fun `production signer mismatch warns without claiming device compromise`() {
        val report = ready().copy(signing = AppObservation.observed(AppSigningSnapshot(setOf(other), false, 1)))
        assertEquals(AppSignatureAssessment.MISMATCHED, report.signatureAssessment())
        assertEquals(DetectorStatus.warning(), report.toDetectorStatus())
    }

    @Test fun `development or test build never gets release key mismatch warning`() {
        for ((debug, test) in listOf(true to false, false to true, true to true)) {
            val report = ready().copy(
                build = AppObservation.observed(AppBuildSnapshot("com.example.host.debug", debug, test)),
                signing = AppObservation.observed(AppSigningSnapshot(setOf(other), false, 1)),
            )
            assertEquals(AppSignatureAssessment.DEVELOPMENT_BUILD, report.signatureAssessment())
            assertEquals(DetectorStatus.info(InfoKind.CONTEXT), report.toDetectorStatus())
        }
    }

    @Test fun `policy is package exact with no prefix or substring acceptance`() {
        for (pkg in listOf("com.example", "com.example.host.extra", "other.com.example.host", "COM.example.host")) {
            val report = ready().copy(build = AppObservation.observed(AppBuildSnapshot(pkg, false, false)))
            assertEquals(AppSignatureAssessment.POLICY_NOT_APPLICABLE, report.signatureAssessment())
            assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
        }
    }

    @Test fun `no policy on SDK host including debug means assessment unsupported`() {
        for (debug in listOf(true, false)) {
            val report = ready().copy(
                build = AppObservation.observed(AppBuildSnapshot("com.example.host", debug, false)),
                policy = AppObservation(AppObservationState.MISSING),
            )
            assertEquals(AppSignatureAssessment.POLICY_MISSING, report.signatureAssessment())
            assertEquals(InfoKind.SUPPORT, report.toDetectorStatus().infoKind)
        }
    }

    @Test fun `multi signer cannot pass by matching one certificate`() {
        val report = ready().copy(signing = AppObservation.observed(AppSigningSnapshot(setOf(pin, other), true, 0)))
        assertEquals(AppSignatureAssessment.MULTIPLE_SIGNERS_UNSUPPORTED, report.signatureAssessment())
        assertEquals(InfoKind.SUPPORT, report.toDetectorStatus().infoKind)
    }

    @Test fun `old rotation pin does not authorize current key without explicit new pin`() {
        val rotated = ready().copy(signing = AppObservation.observed(AppSigningSnapshot(setOf(other), false, 2)))
        assertEquals(AppSignatureAssessment.MISMATCHED, rotated.signatureAssessment())
        assertEquals(AppSignatureAssessment.MATCHED, rotated.copy(
            policy = AppObservation.observed(AppSigningPolicy("com.example.host", setOf(pin, other))),
        ).signatureAssessment())
    }

    @Test fun `debugger attachment waiting and ptrace observation are context on any build`() {
        for (debug in listOf(true, false)) for (attached in listOf(true, false)) for (waiting in listOf(true, false)) {
            val report = ready().copy(
                build = AppObservation.observed(AppBuildSnapshot("com.example.host", debug, false)),
                debugger = AppObservation.observed(AppDebuggerSnapshot(attached, waiting)),
                tracerPid = AppObservation.observed(234),
            )
            assertEquals(DetectorStatus.info(InfoKind.CONTEXT), report.toDetectorStatus())
        }
    }

    @Test fun `unread sources remain unknown and collection failures stay errors`() {
        for (state in AppObservationState.entries.filter { it != AppObservationState.OBSERVED }) {
            val report = ready().copy(signing = AppObservation(state))
            assertEquals(if (state.isFailure) InfoKind.ERROR else InfoKind.SUPPORT, report.toDetectorStatus().infoKind)
            assertNotEquals(AppSignatureAssessment.MATCHED, report.signatureAssessment())
            val runtime = ready().copy(tracerPid = AppObservation(state))
            assertEquals(if (state.isFailure) InfoKind.ERROR else InfoKind.SUPPORT, runtime.toDetectorStatus().infoKind)
        }
        assertEquals(InfoKind.ERROR, AppIntegrityReport.failed("IOException").toDetectorStatus().infoKind)
        assertEquals(InfoKind.SUPPORT, AppIntegrityReport.loading().toDetectorStatus().infoKind)
    }

    @Test fun `malformed policy is an error even on development build`() {
        val report = ready().copy(
            build = AppObservation.observed(AppBuildSnapshot("com.example.host", true, false)),
            policy = AppObservation(AppObservationState.MALFORMED),
        )
        assertEquals(InfoKind.ERROR, report.toDetectorStatus().infoKind)
    }

    @Test fun `observation must preserve value availability contract`() {
        assertThrows(IllegalArgumentException::class.java) { AppObservation<Int>(AppObservationState.OBSERVED) }
        assertThrows(IllegalArgumentException::class.java) { AppObservation(AppObservationState.FAILED, 0) }
        assertThrows(IllegalArgumentException::class.java) { AppSigningPolicy("", setOf(pin)) }
        assertThrows(IllegalArgumentException::class.java) { AppSigningSnapshot(setOf(pin), true, 0) }
        assertEquals(DetectionSeverity.INFO, ready().toDetectorStatus().severity)
    }
}
