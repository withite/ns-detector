/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */

package com.eltavine.duckdetector.features.update.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * GET_SIGNING_CERTIFICATES populates SigningInfo on API 28+ (the app requires API 29+).
 * Matching current signers is intentionally conservative: certificate rotation is not supported.
 * https://developer.android.com/reference/android/content/pm/SigningInfo#getApkContentsSigners()
 */
@Suppress("DEPRECATION")
internal fun hasCompatibleInstallIdentity(context: Context, archivePath: String): Boolean {
    return try {
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val candidate = manager.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return false
        canReplaceInstalledApk(installed.toInstallIdentity(), candidate.toInstallIdentity())
    } catch (_: Exception) {
        false
    }
}

private fun PackageInfo.toInstallIdentity(): ApkInstallIdentity = ApkInstallIdentity(
    packageName = packageName.orEmpty(),
    signerCertificates = signingInfo?.apkContentsSigners?.map { it.toCharsString() }.orEmpty().toSet(),
)
