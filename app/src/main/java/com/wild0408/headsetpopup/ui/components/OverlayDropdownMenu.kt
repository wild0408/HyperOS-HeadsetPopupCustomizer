package com.wild0408.headsetpopup.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.basiccomponent.BasicComponentColors
import com.haooz.nexioui.basiccomponent.BasicComponentDefaults
import com.haooz.nexioui.dropdown.DropdownArrowEndAction
import com.haooz.nexioui.dropdown.DropdownColors
import com.haooz.nexioui.dropdown.DropdownDefaults
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownMaskMode
import com.haooz.nexioui.dropdown.DropdownPanelDragSelectState
import com.haooz.nexioui.dropdown.OverlayDropdownPopup
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop

private fun resolveSelectedText(entries: List<DropdownEntry>): String? {
    for (entry in entries) {
        for (item in entry.items) {
            if (item.selected) return item.text
        }
    }
    return null
}

@Composable
private fun RowScope.DropdownTriggerContent(
    selectedText: String?,
    actionColor: Color,
    alpha: () -> Float,
) {
    if (selectedText != null) {
        NexioText(
            text = selectedText,
            fontSize = 14.2.sp,
            color = NexioTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
                .graphicsLayer { this.alpha = alpha() },
        )
    }
    DropdownArrowEndAction(
        actionColor = actionColor,
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
    )
}

private val DefaultCollapseExtra = DpSize(14.dp, 10.dp)

@Composable
fun rememberDropdownMenuColors(useGlass: Boolean): DropdownColors = if (useGlass) {
    DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )
} else {
    DropdownDefaults.dropdownColors()
}

@Composable
fun OverlayDropdownMenu(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors? = null,
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val entries = remember(entry) { listOf(entry) }
    OverlayDropdownMenu(
        entries = entries,
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        liquidGlassBackdrop = liquidGlassBackdrop,
        collapseExtra = collapseExtra,
    )
}

@Composable
fun OverlayDropdownMenu(
    entries: List<DropdownEntry>,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors? = null,
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    var popupFullyDismissed by remember { mutableStateOf(true) }
    val currentHapticFeedback = LocalHapticFeedback.current
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)

    val setExpanded = remember {
        { expanded: Boolean ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    LaunchedEffect(isDropdownExpanded.value) {
        if (isDropdownExpanded.value) {
            repeat(2) { withFrameNanos { } }
            popupFullyDismissed = false
        }
    }

    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val triggerAlpha: () -> Float = { if (popupFullyDismissed) 1f else 0f }
    val dragSelectState = remember {
        DropdownPanelDragSelectState(maskMode = DropdownMaskMode.Sliding)
    }

    val nonEmptyEntries = entries.filter { it.items.isNotEmpty() }
    val hasEntries = nonEmptyEntries.isNotEmpty()
    val actualEnabled = enabled && hasEntries
    val selectedText = resolveSelectedText(nonEmptyEntries)
    val resolvedBackdrop = if (NexioMaterial.isNone()) null else liquidGlassBackdrop
    val resolvedDropdownColors = dropdownColors ?: rememberDropdownMenuColors(useGlass = resolvedBackdrop != null)
    val actionColor = if (actualEnabled) {
        NexioTheme.colorScheme.onSurfaceVariantActions
    } else {
        NexioTheme.colorScheme.disabledOnSecondaryVariant
    }

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                setExpanded(!isDropdownExpanded.value)
                if (isDropdownExpanded.value) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { triggerSize = it },
            ) {
                DropdownTriggerContent(
                    selectedText = selectedText,
                    actionColor = actionColor,
                    alpha = triggerAlpha,
                )
            }
            if (hasEntries) {
                OverlayDropdownPopup(
                    entries = nonEmptyEntries,
                    show = isDropdownExpanded.value,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = {
                        isHoldDown.value = false
                        popupFullyDismissed = true
                    },
                    maxHeight = maxHeight,
                    dropdownColors = resolvedDropdownColors,
                    renderInRootScaffold = renderInRootScaffold,
                    collapseOnSelection = collapseOnSelection,
                    liquidGlassBackdrop = resolvedBackdrop,
                    collapseSize = triggerSize,
                    collapseExtra = collapseExtra,
                    dragSelectState = dragSelectState,
                    collapseContent = {
                        val density = LocalDensity.current
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (triggerSize.width > 0) {
                                Modifier.widthIn(max = with(density) { triggerSize.width.toDp() })
                            } else {
                                Modifier
                            },
                        ) {
                            DropdownTriggerContent(
                                selectedText = selectedText,
                                actionColor = actionColor,
                                alpha = { 1f },
                            )
                        }
                    },
                )
            }
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}
