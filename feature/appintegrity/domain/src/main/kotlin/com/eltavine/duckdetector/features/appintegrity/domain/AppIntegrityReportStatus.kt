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

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind

enum class AppSignatureAssessment {
    NOT_EVALUATED, SOURCE_UNAVAILABLE, POLICY_MISSING, POLICY_NOT_APPLICABLE,
    MULTIPLE_SIGNERS_UNSUPPORTED, DEVELOPMENT_BUILD, MATCHED, MISMATCHED, ERROR,
}

/** This is a current-certificate policy, deliberately stricter than accepting a historical pin. */
fun AppIntegrityReport.signatureAssessment(): AppSignatureAssessment {
    if (stage == AppIntegrityStage.FAILED) return AppSignatureAssessment.ERROR
    if (stage != AppIntegrityStage.READY) return AppSignatureAssessment.NOT_EVALUATED
    if (listOf(build.state, signing.state, policy.state).any { it.isFailure }) return AppSignatureAssessment.ERROR
    if (policy.state == AppObservationState.MISSING) return AppSignatureAssessment.POLICY_MISSING
    val build = build.value ?: return AppSignatureAssessment.SOURCE_UNAVAILABLE
    val policy = policy.value ?: return AppSignatureAssessment.SOURCE_UNAVAILABLE
    // Debug/test builds commonly use a different package or key and are not a device integrity finding.
    if (build.developmentBuild) return AppSignatureAssessment.DEVELOPMENT_BUILD
    if (build.packageName != policy.allowedPackage) return AppSignatureAssessment.POLICY_NOT_APPLICABLE
    val signing = signing.value ?: return AppSignatureAssessment.SOURCE_UNAVAILABLE
    if (signing.multipleSigners) return AppSignatureAssessment.MULTIPLE_SIGNERS_UNSUPPORTED
    return if (signing.currentCertificateSha256.single() in policy.certificateSha256) {
        AppSignatureAssessment.MATCHED
    } else {
        AppSignatureAssessment.MISMATCHED
    }
}

val AppObservationState.isFailure: Boolean
    get() = this == AppObservationState.FAILED || this == AppObservationState.MALFORMED

fun AppSignatureAssessment.toDetectorStatus(): DetectorStatus = when (this) {
    AppSignatureAssessment.MISMATCHED -> DetectorStatus.warning()
    AppSignatureAssessment.ERROR -> DetectorStatus.info(InfoKind.ERROR)
    AppSignatureAssessment.DEVELOPMENT_BUILD, AppSignatureAssessment.MATCHED -> DetectorStatus.info(InfoKind.CONTEXT)
    else -> DetectorStatus.info(InfoKind.SUPPORT)
}

/** Debugging observations describe this host only, and never raise device severity. */
fun AppIntegrityReport.toDetectorStatus(): DetectorStatus = when {
    signatureAssessment() == AppSignatureAssessment.MISMATCHED -> DetectorStatus.warning()
    stage == AppIntegrityStage.FAILED || sourceStates.any { it.isFailure } -> DetectorStatus.info(InfoKind.ERROR)
    stage != AppIntegrityStage.READY || sourceStates.any { it != AppObservationState.OBSERVED } ->
        DetectorStatus.info(InfoKind.SUPPORT)
    else -> signatureAssessment().toDetectorStatus()
}
