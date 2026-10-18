/*
 * Copyright 2026 NS Detector contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */

package com.eltavine.duckdetector.features.update.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallIdentityTest {
    private val installed = ApkInstallIdentity("io.github.withite.nsdetector", setOf("certificate-a"))

    @Test
    fun `same package signed by the same release key can upgrade`() {
        assertTrue(canReplaceInstalledApk(installed, installed.copy()))
    }

    @Test
    fun `different CI debug keys cannot be offered as an in-place upgrade`() {
        assertFalse(canReplaceInstalledApk(installed, installed.copy(signerCertificates = setOf("certificate-b"))))
    }

    @Test
    fun `a preview package cannot replace the release even when a key is shared`() {
        assertFalse(canReplaceInstalledApk(installed, installed.copy(packageName = "io.github.withite.nsdetector.debug")))
    }

    @Test
    fun `all current signers must match regardless of order`() {
        val multiple = installed.copy(signerCertificates = setOf("certificate-a", "certificate-b"))
        assertTrue(canReplaceInstalledApk(multiple, multiple.copy(signerCertificates = setOf("certificate-b", "certificate-a"))))
        assertFalse(canReplaceInstalledApk(multiple, installed))
    }

    @Test
    fun `unreadable or empty identities are never accepted`() {
        assertFalse(canReplaceInstalledApk(installed, installed.copy(signerCertificates = emptySet())))
        assertFalse(canReplaceInstalledApk(installed.copy(signerCertificates = emptySet()), installed.copy(signerCertificates = emptySet())))
        assertFalse(canReplaceInstalledApk(installed.copy(packageName = ""), installed.copy(packageName = "")))
    }
}
