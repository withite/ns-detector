/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.scan.DetectorSummary
import com.eltavine.duckdetector.core.ui.components.DetectorHairline
import com.eltavine.duckdetector.core.ui.components.DuckIcon
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.core.ui.presentation.rememberStatusAppearance
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveTextButton
import io.github.xiaotong6666.uihelper.adaptive.adaptiveContainerContentColor
import io.github.xiaotong6666.uihelper.adaptive.adaptiveInsetSurfaceColor
import io.github.xiaotong6666.uihelper.adaptive.adaptivePrimaryColor
import io.github.xiaotong6666.uihelper.adaptive.adaptiveSecondaryTextColor

/** Controls consume typed filters; evidence classification stays in the console model. */
@Composable
internal fun DashboardBrowserControls(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: DashboardConsoleFilter,
    onFilterChange: (DashboardConsoleFilter) -> Unit,
    visibleCount: Int,
    totalCount: Int,
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
) {
    DuckPanel(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WrapSafeText(
            text = stringResource(R.string.dashboard_browser_title),
            modifier = Modifier.semantics { heading() },
            style = DuckTypography.PanelTitle,
            color = adaptiveContainerContentColor(),
        )
        DashboardBrowserSearch(query = query, onQueryChange = onQueryChange)
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DashboardConsoleFilter.entries.size, key = { DashboardConsoleFilter.entries[it].name }) { index ->
                val option = DashboardConsoleFilter.entries[index]
                BrowserChoice(browserFilterLabel(option), option == filter) { onFilterChange(option) }
            }
        }
        DetectorHairline()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WrapSafeText(
                text = stringResource(R.string.dashboard_browser_count, visibleCount, totalCount),
                modifier = Modifier.weight(1f),
                style = DuckTypography.PanelSupporting,
                color = adaptiveSecondaryTextColor(),
            )
            AdaptiveTextButton(
                text = stringResource(if (compact) R.string.dashboard_browser_details else R.string.dashboard_browser_compact),
                onClick = { onCompactChange(!compact) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun DashboardBrowserSearch(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val searchLabel = stringResource(R.string.dashboard_browser_search)
    val primary = adaptivePrimaryColor()
    val content = adaptiveContainerContentColor()
    val secondary = adaptiveSecondaryTextColor()
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(adaptiveInsetSurfaceColor())
            .then(if (focused) Modifier.border(2.dp, primary, RoundedCornerShape(16.dp)) else Modifier)
            .semantics { contentDescription = searchLabel },
        textStyle = DuckTypography.PanelBody.copy(color = content),
        cursorBrush = SolidColor(primary),
        singleLine = true,
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
        decorationBox = { input ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DuckIcon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = secondary)
                Box(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (query.isEmpty()) {
                        WrapSafeText(text = searchLabel, style = DuckTypography.PanelBody, color = secondary)
                    }
                    input()
                }
                if (query.isNotEmpty()) {
                    val clearLabel = stringResource(R.string.dashboard_browser_clear_search)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") }
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        DuckIcon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
                    }
                }
            }
        },
    )
}

@Composable
private fun BrowserChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = adaptivePrimaryColor()
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) primary.copy(alpha = 0.14f) else adaptiveInsetSurfaceColor())
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            DuckIcon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
        }
        WrapSafeText(text = label, style = DuckTypography.ActionLabel, color = adaptiveContainerContentColor())
    }
}

@Composable
private fun browserFilterLabel(filter: DashboardConsoleFilter): String = stringResource(
    when (filter) {
        DashboardConsoleFilter.ALL -> R.string.dashboard_browser_all
        DashboardConsoleFilter.ATTENTION -> R.string.dashboard_browser_attention
        DashboardConsoleFilter.LIMITED -> R.string.dashboard_browser_limited
        DashboardConsoleFilter.ERRORS -> R.string.dashboard_browser_errors
        DashboardConsoleFilter.OTHER -> R.string.dashboard_browser_other
    },
)

@Composable
internal fun DashboardCompactModule(
    summary: DetectorSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    details: @Composable () -> Unit,
) {
    val appearance = rememberStatusAppearance(summary.status)
    val actionLabel = stringResource(
        if (expanded) R.string.dashboard_browser_hide_evidence else R.string.dashboard_browser_show_evidence,
    )
    val expansionState = stringResource(
        if (expanded) R.string.dashboard_browser_expanded else R.string.dashboard_browser_collapsed,
    )
    DuckPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { stateDescription = expansionState }
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onToggle)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DuckIcon(
                    imageVector = if (summary.ready) appearance.icon else Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (summary.ready) appearance.iconTint else adaptiveSecondaryTextColor(),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    WrapSafeText(
                        text = summary.title,
                        modifier = Modifier.semantics { heading() },
                        style = DuckTypography.PanelTitle,
                        color = adaptiveContainerContentColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WrapSafeText(
                        text = if (summary.ready) {
                            listOfNotNull(appearance.label, appearance.metaLabel).joinToString(" · ")
                        } else stringResource(R.string.dashboard_browser_waiting),
                        style = DuckTypography.PanelCaption,
                        color = adaptiveSecondaryTextColor(),
                    )
                }
                DuckIcon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = adaptivePrimaryColor(),
                )
            }
            WrapSafeText(
                // A previous status must never surface as this scan's completed evidence.
                text = if (summary.ready) summary.headline else stringResource(R.string.dashboard_browser_waiting_detail),
                style = DuckTypography.PanelSupporting,
                color = adaptiveSecondaryTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            DetectorHairline()
            details()
        }
    }
}

@Composable
internal fun DashboardBrowserEmptyState(onReset: () -> Unit) {
    DuckPanel(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WrapSafeText(
            text = stringResource(R.string.dashboard_browser_empty),
            style = DuckTypography.PanelTitle,
            color = adaptiveContainerContentColor(),
        )
        WrapSafeText(
            text = stringResource(R.string.dashboard_browser_empty_hint),
            style = DuckTypography.PanelBody,
            color = adaptiveSecondaryTextColor(),
        )
        AdaptiveTextButton(
            text = stringResource(R.string.dashboard_browser_reset),
            onClick = onReset,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
