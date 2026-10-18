/* Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0 */
package com.eltavine.duckdetector.features.update.ui

import com.eltavine.duckdetector.features.update.domain.NightlyUpdateChecker
import com.eltavine.duckdetector.features.update.domain.UpdateApkDownloader
import com.eltavine.duckdetector.features.update.domain.UpdateApkInstaller
import com.eltavine.duckdetector.features.update.domain.UpdateCheckResult
import com.eltavine.duckdetector.features.update.domain.UpdateDownloadResult
import com.eltavine.duckdetector.features.update.domain.UpdateInstallResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateInstallationLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `repeated confirm starts one verification and resets busy state after completion`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val vm = viewModel(
            checker = NightlyUpdateChecker { _, _ -> UpdateCheckResult.Available(availableUpdate()) },
            downloader = UpdateApkDownloader { _, _, _ -> UpdateDownloadResult.Ready("/cache/test.apk") },
            installer = UpdateApkInstaller { _, _ -> calls++; finish.await(); UpdateInstallResult.Launched },
        )
        vm.checkAutomatically(); advanceUntilIdle()
        vm.downloadUpdate(); advanceUntilIdle()
        val first = async { vm.installDownloaded() }
        runCurrent()
        assertTrue(vm.uiState.value.isInstalling)
        assertEquals(UpdateInstallResult.InProgress, vm.installDownloaded())
        assertEquals(1, calls)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(UpdateInstallResult.Launched, first.await())
        assertFalse(vm.uiState.value.isInstalling)
        assertFalse(vm.uiState.value.isDialogVisible)
    }

    @Test
    fun `dismissing during verification cancels it before the installer can open`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        var opened = false
        val vm = viewModel(
            checker = NightlyUpdateChecker { _, _ -> UpdateCheckResult.Available(availableUpdate()) },
            downloader = UpdateApkDownloader { _, _, _ -> UpdateDownloadResult.Ready("/cache/test.apk") },
            installer = UpdateApkInstaller { _, _ -> finish.await(); opened = true; UpdateInstallResult.Launched },
        )
        vm.checkAutomatically(); advanceUntilIdle()
        vm.downloadUpdate(); advanceUntilIdle()
        val job = launch { vm.installDownloaded() }
        runCurrent()
        vm.dismissUpdate()
        runCurrent()
        finish.complete(Unit)
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertFalse(opened)
        assertFalse(vm.uiState.value.isInstalling)
        assertFalse(vm.uiState.value.isDialogVisible)
    }
}
