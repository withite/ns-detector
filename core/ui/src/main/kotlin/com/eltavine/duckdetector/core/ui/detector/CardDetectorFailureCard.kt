/* Copyright 2026 NS Detector contributors. SPDX-License-Identifier: Apache-2.0 */
package com.eltavine.duckdetector.core.ui.detector

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.ui.R
import com.eltavine.duckdetector.core.ui.components.DetectorCardFrame
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveTextButton

/** Runtime infrastructure failures are shown independently from any previous detector model. */
@Composable
internal fun CardDetectorFailureCard(title: String, failure: CardScanFailure, onRetry: () -> Unit) {
    DetectorCardFrame(
        title = title,
        subtitle = stringResource(R.string.ns_scan_failed_scope),
        status = DetectorStatus.info(InfoKind.ERROR),
        verdict = stringResource(R.string.ns_scan_failed_title),
        summary = stringResource(R.string.ns_scan_failed_scope),
        leadingIcon = Icons.Rounded.ErrorOutline,
        expanded = true,
        footerActions = {
            AdaptiveTextButton(onClick = onRetry) { WrapSafeText(stringResource(R.string.ns_scan_retry)) }
        },
    ) {
        WrapSafeText(failure.name)
    }
}
