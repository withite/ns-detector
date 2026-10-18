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

package com.eltavine.duckdetector.features.networkenvironment.ui.card

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eltavine.duckdetector.core.ui.components.DetectorCardFrame
import com.eltavine.duckdetector.core.ui.components.DetectorDetailRowBlock
import com.eltavine.duckdetector.core.ui.components.DetectorHairline
import com.eltavine.duckdetector.core.ui.components.DetectorSectionFrame
import com.eltavine.duckdetector.core.ui.components.highestSectionSeverity
import com.eltavine.duckdetector.features.networkenvironment.presentation.model.NetworkEnvironmentCardModel

@Composable
internal fun NetworkEnvironmentDetectorCard(
    model: NetworkEnvironmentCardModel,
    modifier: Modifier = Modifier,
) {
    DetectorCardFrame(
        title = model.title,
        subtitle = model.subtitle,
        status = model.status,
        verdict = model.verdict,
        summary = model.summary,
        leadingIcon = Icons.Rounded.Public,
        modifier = modifier,
    ) {
        if (model.rows.isNotEmpty()) {
            DetectorSectionFrame(
                title = model.detailsTitle,
                icon = Icons.Rounded.Info,
                severity = highestSectionSeverity(model.rows.map { it.status }),
                showDivider = false,
            ) {
                model.rows.forEachIndexed { index, row ->
                    DetectorDetailRowBlock(
                        label = row.label,
                        value = row.value,
                        status = row.status,
                        detail = row.detail,
                    )
                    if (index < model.rows.lastIndex) DetectorHairline()
                }
            }
        }
    }
}
