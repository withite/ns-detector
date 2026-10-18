/*
 * Copyright 2026 Duck Apps Contributor
 * Modified 2026 by NS Detector contributors: redacted text/JSON export and reliable writes.
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */

package com.eltavine.duckdetector.features.dashboard.ui

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eltavine.duckdetector.core.ui.LocalAppBuildInfo
import com.eltavine.duckdetector.core.ui.detector.DetectorSession
import com.eltavine.duckdetector.core.ui.detector.DeviceProfileSession
import com.eltavine.duckdetector.core.ui.presentation.formatBuildTimeUtc
import com.eltavine.duckdetector.features.dashboard.presentation.export.DashboardExport
import com.eltavine.duckdetector.features.dashboard.presentation.export.DashboardJsonReportRenderer
import com.eltavine.duckdetector.features.dashboard.presentation.export.DashboardReportRenderer
import com.eltavine.duckdetector.features.dashboard.presentation.export.ExportHeader
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState
import io.github.xiaotong6666.uihelper.dialog.AdaptiveDecisionDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns format selection and SAF persistence; both formats use presentation-layer redaction. */
@Composable
internal fun rememberDashboardReportExporter(
    uiState: DashboardUiState,
    detectors: List<DetectorSession>,
    deviceProfile: DeviceProfileSession,
): () -> Unit {
    val context = LocalContext.current
    val buildInfo = LocalAppBuildInfo.current
    val coroutineScope = rememberCoroutineScope()
    var showFormatPicker by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<DashboardExport?>(null) }
    val save: (Uri?, Boolean) -> Unit = { uri, json ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val export = requireNotNull(snapshot)
                        val content = if (json) DashboardJsonReportRenderer.render(export)
                        else DashboardReportRenderer.render(export)
                        // Some document providers return null without throwing; this is a failure.
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { stream ->
                            stream.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) R.string.dashboard_report_saved else R.string.dashboard_report_save_failed_unknown,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val textLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        save(it, false)
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        save(it, true)
    }
    val launchExport: (Boolean) -> Unit = { json ->
        showFormatPicker = false
        // Freeze the same scan before opening another app's document picker.
        runCatching {
            pendingExport = DashboardExport(
                header = ExportHeader(
                    versionName = buildInfo.versionName,
                    versionCode = buildInfo.versionCode,
                    buildHash = buildInfo.buildHash,
                    buildTime = formatBuildTimeUtc(buildInfo.buildTimeUtc),
                    reportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (z)", Locale.US).format(Date()),
                ),
                overview = uiState.overview,
                topFindings = uiState.topFindings,
                detectors = detectors.map { it.report() },
                device = deviceProfile.report(),
            )
            val filename = generateExportReportFileName(json = json)
            if (json) jsonLauncher.launch(filename) else textLauncher.launch(filename)
        }.onFailure {
            pendingExport = null
            Toast.makeText(context, R.string.dashboard_report_save_failed_unknown, Toast.LENGTH_SHORT).show()
        }
    }
    AdaptiveDecisionDialog(
        show = showFormatPicker,
        title = stringResource(R.string.dashboard_export_title),
        message = stringResource(R.string.dashboard_export_privacy),
        confirmLabel = stringResource(R.string.dashboard_export_text),
        dismissLabel = stringResource(R.string.dashboard_export_json),
        onConfirm = { launchExport(false) },
        onDismissButton = { launchExport(true) },
        onDismissRequest = { showFormatPicker = false },
        icon = Icons.Rounded.FileDownload,
    )
    return { showFormatPicker = true }
}

internal fun generateExportReportFileName(
    model: String = Build.MODEL,
    nowEpochMillis: Long = System.currentTimeMillis(),
    json: Boolean = false,
): String {
    val sanitizedModel = model.trim().ifBlank { "unknown" }
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val timestamp = timestampFormat.format(Date(nowEpochMillis))
    return "ns_detector_report_${sanitizedModel}_$timestamp.${if (json) "json" else "txt"}"
}
