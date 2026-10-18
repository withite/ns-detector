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
import org.junit.Assert.*
import org.junit.Test

class ProcessSandboxReportStatusTest {
    private fun ready(uid: Int = 10_123, system: Boolean = false, mask: ULong = 0uL, mode: Int = 2) =
        ProcessSandboxReport(
            ProcessSandboxStage.READY, SandboxObservation.observed(SandboxHost(uid, system)),
            SandboxStatusSnapshot(
                SandboxObservation.observed(SandboxUidQuartet(uid.toLong(), uid.toLong(), uid.toLong(), uid.toLong())),
                SandboxObservation.observed(mask), SandboxObservation.observed(mask), SandboxObservation.observed(mask),
                SandboxObservation.observed(mode),
            ),
        )

    @Test fun `loading and failed scans remain distinct informational states`() {
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), ProcessSandboxReport.loading().toDetectorStatus())
        assertEquals(DetectorStatus.info(InfoKind.ERROR), ProcessSandboxReport.failed("failure").toDetectorStatus())
    }

    @Test fun `matching ordinary credentials and zero masks are context not attestation`() {
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), ready().toDetectorStatus())
        assertFalse(ready().hasUidInconsistency())
        assertFalse(ready().hasExpandedCapabilities())
    }

    @Test fun `all multiuser ordinary app identities apply the same baseline`() {
        listOf(10_000, 19_999, 110_123, 2010_123).forEach { uid ->
            assertTrue(SandboxHost(uid, false).ordinaryApplication)
            assertEquals(DetectorStatus.warning(), ready(uid, mask = 1uL shl 21).toDetectorStatus())
        }
    }

    @Test fun `system root isolated and sdk sandbox identities are context only`() {
        listOf(0, 1000, 1002, 1073, 20_000, 29_999, 90_000, 99_001, 100_000).forEach { uid ->
            assertFalse(SandboxHost(uid, false).ordinaryApplication)
            assertEquals(DetectorStatus.info(InfoKind.CONTEXT), ready(uid, mask = ULong.MAX_VALUE).toDetectorStatus())
        }
    }

    @Test fun `preinstalled and updated system applications are exempt at ordinary app UIDs`() {
        val report = ready(system = true, mask = ULong.MAX_VALUE)
        assertFalse(report.host.value!!.ordinaryApplication)
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), report.toDetectorStatus())
    }

    @Test fun `AOSP wakelock capability alone never raises a finding in any granted set`() {
        assertEquals(DetectorStatus.info(InfoKind.CONTEXT), ready(mask = 1uL shl 36).toDetectorStatus())
    }

    @Test fun `wakelock exception does not hide an additional granted capability`() {
        val report = ready(mask = (1uL shl 36) or (1uL shl 7))
        assertTrue(report.hasExpandedCapabilities())
        assertEquals(DetectorStatus.warning(), report.toDetectorStatus())
    }

    @Test fun `unsigned high capability bit is preserved and observed`() {
        assertEquals(DetectorStatus.warning(), ready(mask = 1uL shl 63).toDetectorStatus())
    }

    @Test fun `permitted and ambient grants are evaluated independently of effective grants`() {
        val base = ready()
        listOf(
            base.snapshot.copy(permittedCapabilities = SandboxObservation.observed(1uL)),
            base.snapshot.copy(ambientCapabilities = SandboxObservation.observed(1uL)),
        ).forEach { snapshot ->
            assertEquals(DetectorStatus.warning(), base.copy(snapshot = snapshot).toDetectorStatus())
        }
    }

    @Test fun `each UID slot must agree with stable same-thread API UID`() {
        val uid = 10_123L
        listOf(
            SandboxUidQuartet(0, uid, uid, uid), SandboxUidQuartet(uid, 0, uid, uid),
            SandboxUidQuartet(uid, uid, 0, uid), SandboxUidQuartet(uid, uid, uid, 0),
        ).forEach { quartet ->
            val base = ready()
            val report = base.copy(snapshot = base.snapshot.copy(uids = SandboxObservation.observed(quartet)))
            assertTrue(report.hasUidInconsistency())
            assertEquals(DetectorStatus.warning(), report.uidStatus())
        }
    }

    @Test fun `all defined Seccomp modes remain context including legitimately disabled mode`() {
        (0..2).forEach { mode -> assertEquals(DetectorStatus.info(InfoKind.CONTEXT), ready(mode = mode).toDetectorStatus()) }
    }

    @Test fun `unknown Seccomp number means unsupported interpretation`() {
        val report = ready(mode = 7)
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
        assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.seccompStatus())
    }

    @Test fun `missing or denied observations are coverage limitations never zero grants`() {
        listOf(SandboxObservationState.MISSING, SandboxObservationState.PERMISSION_DENIED, SandboxObservationState.UNAVAILABLE,
            SandboxObservationState.UNSUPPORTED, SandboxObservationState.NOT_EVALUATED).forEach { state ->
            val report = ready().copy(snapshot = SandboxStatusSnapshot.unavailable(state))
            assertFalse(report.probed)
            assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
        }
    }

    @Test fun `malformed and failed observations remain explicit errors`() {
        listOf(SandboxObservationState.MALFORMED, SandboxObservationState.FAILED).forEach { state ->
            assertEquals(DetectorStatus.info(InfoKind.ERROR), ready().copy(snapshot = SandboxStatusSnapshot.unavailable(state)).toDetectorStatus())
        }
    }

    @Test fun `missing host or changing UID prevents ordinary-baseline alarms`() {
        listOf(SandboxObservationState.MISSING, SandboxObservationState.IDENTITY_CHANGED).forEach { state ->
            val report = ready(mask = ULong.MAX_VALUE).copy(host = SandboxObservation(state))
            assertFalse(report.hasExpandedCapabilities())
            assertFalse(report.hasUidInconsistency())
            assertEquals(DetectorStatus.info(InfoKind.SUPPORT), report.toDetectorStatus())
        }
    }

    @Test fun `an observed grant warning survives another malformed field with typed detail`() {
        val base = ready(mask = 1uL)
        val report = base.copy(snapshot = base.snapshot.copy(seccomp = SandboxObservation(SandboxObservationState.MALFORMED)))
        assertEquals(DetectorStatus.warning(), report.toDetectorStatus())
        assertEquals(DetectorStatus.info(InfoKind.ERROR), report.seccompStatus())
    }

    @Test fun `observation invariants reject unreadable fake zeros and observed nulls`() {
        assertThrows(IllegalArgumentException::class.java) { SandboxObservation(SandboxObservationState.MISSING, 0uL) }
        assertThrows(IllegalArgumentException::class.java) { SandboxObservation<ULong>(SandboxObservationState.OBSERVED) }
        assertThrows(IllegalArgumentException::class.java) { SandboxHost(-1, false) }
        assertThrows(IllegalArgumentException::class.java) { SandboxUidQuartet(-1, 0, 0, 0) }
    }
}
