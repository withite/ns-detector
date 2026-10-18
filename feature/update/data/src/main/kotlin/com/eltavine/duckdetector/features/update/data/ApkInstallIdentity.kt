/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */

package com.eltavine.duckdetector.features.update.data

/** Current signing certificates encoded as DER hex; certificate history is deliberately excluded. */
internal data class ApkInstallIdentity(
    val packageName: String,
    val signerCertificates: Set<String>,
)

/** The OS still performs the final APK and signature verification when installing. */
internal fun canReplaceInstalledApk(installed: ApkInstallIdentity, candidate: ApkInstallIdentity): Boolean =
    installed.packageName.isNotBlank() &&
        candidate.packageName == installed.packageName &&
        installed.signerCertificates.isNotEmpty() &&
        installed.signerCertificates.none(String::isBlank) &&
        candidate.signerCertificates == installed.signerCertificates
