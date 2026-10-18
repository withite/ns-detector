/*
 * Copyright 2026 Duck Apps Contributor
 * Modified 2026 by NS Detector contributors: independent diagnostics product.
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

package com.eltavine.duckdetector.startup.legal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.R
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveContent
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** Consent is a single deliberate action; it never opts the user into network or experiments. */
@Composable
fun AgreementScreen(onAgree: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val onExit = { context.findActivity()?.finishAndRemoveTask(); Unit }
    AdaptiveContent(
        miuix = {
            MiuixScaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = { AgreementActions(onAgree, onExit) },
            ) { padding ->
                AgreementDocument(Modifier.padding(padding))
            }
        },
        material = {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = { AgreementActions(onAgree, onExit) },
            ) { padding ->
                AgreementDocument(Modifier.padding(padding))
            }
        },
    )
}

@Composable
private fun AgreementDocument(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("NS Detector", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.ns_welcome_subtitle), style = MaterialTheme.typography.titleMedium)
        AgreementSection(
            icon = Icons.Outlined.Gavel,
            title = stringResource(R.string.user_agreement_title),
            content = stringResource(R.string.ns_agreement_scope),
        )
        AgreementRiskBanner()
        AgreementSection(
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.ns_optional_title),
            content = stringResource(R.string.ns_optional_risks),
            tone = AgreementSectionTone.Warning,
        )
        AgreementSection(
            icon = Icons.Outlined.PrivacyTip,
            title = stringResource(R.string.privacy_notice_title),
            content = stringResource(R.string.privacy_notice_content),
            tone = AgreementSectionTone.Notice,
        )
    }
}

@Composable
private fun AgreementActions(onAgree: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp).widthIn(max = 640.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdaptiveContent(
            miuix = {
                MiuixButton(onClick = onExit) { MiuixText(stringResource(R.string.ns_exit)) }
                MiuixButton(onClick = onAgree, colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                    MiuixText(stringResource(R.string.i_agree_continue))
                }
            },
            material = {
                TextButton(onClick = onExit) { Text(stringResource(R.string.ns_exit)) }
                Button(onClick = onAgree) { Text(stringResource(R.string.i_agree_continue)) }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
