package com.wild0408.headsetpopup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.wild0408.headsetpopup.ui.components.OverlayDropdownMenu
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.state.THEME_MODE_DARK
import com.wild0408.headsetpopup.ui.state.THEME_MODE_LIGHT
import com.wild0408.headsetpopup.ui.state.THEME_MODE_SYSTEM
import com.wild0408.headsetpopup.ui.theme.LocalSystemDarkTheme
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import com.haooz.nexioui.material.NexioMaterialLevel
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioDarkColorScheme
import com.haooz.nexioui.theme.NexioLightColorScheme
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.continuousBorder
import com.haooz.softglass.shape.continuousClip

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val settings = LocalAppSettings.current
    val listState = rememberLazyListState()
    val systemDark = LocalSystemDarkTheme.current
    val effectiveDark = when (settings.themeMode) {
        THEME_MODE_DARK -> true
        THEME_MODE_LIGHT -> false
        else -> systemDark
    }

    DocumentPageScaffold(
        title = "外观与材质",
        onBack = onBack,
    ) { paddingValues, scrollBehavior, backdrop ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ThemeSelectionBox(
                        label = "浅色模式",
                        darkPreview = false,
                        selected = !effectiveDark,
                        onClick = {
                            if (settings.themeMode == THEME_MODE_SYSTEM && !systemDark) return@ThemeSelectionBox
                            settings.updateThemeMode(THEME_MODE_LIGHT)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSelectionBox(
                        label = "深色模式",
                        darkPreview = true,
                        selected = effectiveDark,
                        onClick = {
                            if (settings.themeMode == THEME_MODE_SYSTEM && systemDark) return@ThemeSelectionBox
                            settings.updateThemeMode(THEME_MODE_DARK)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = "跟随系统深浅色",
                        summary = null,
                        checked = settings.themeMode == THEME_MODE_SYSTEM,
                        onCheckedChange = { follow ->
                            settings.updateThemeMode(
                                if (follow) THEME_MODE_SYSTEM
                                else if (systemDark) THEME_MODE_DARK else THEME_MODE_LIGHT,
                            )
                        },
                    )
                }
            }

            item {
                SmallTitle(text = "玻璃材质")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val materialOptions = if (settings.materialLocked) {
                            listOf(NexioMaterialLevel.None)
                        } else {
                            listOf(
                                NexioMaterialLevel.Liquid,
                                NexioMaterialLevel.Soft,
                                NexioMaterialLevel.Frosted,
                                NexioMaterialLevel.None,
                            )
                        }
                        OverlayDropdownMenu(
                            title = "界面材质档位",
                            summary = if (settings.materialLocked) "当前系统不支持" else null,
                            entry = DropdownEntry(
                                items = materialOptions.map { level ->
                                    DropdownItem(
                                        text = settings.materialLabel(level),
                                        selected = settings.materialLevel == level,
                                        onClick = { settings.updateMaterialLevel(level) },
                                    )
                                },
                            ),
                            enabled = !settings.materialLocked,
                            liquidGlassBackdrop = backdrop,
                        )
                        SwitchPreference(
                            title = "顶栏真·渐进模糊",
                            summary = null,
                            checked = settings.trueProgressiveBlur,
                            enabled = settings.materialLevel != NexioMaterialLevel.None,
                            onCheckedChange = settings::updateTrueProgressiveBlur,
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "交互体验")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = "预测性返回动画",
                            summary = null,
                            checked = settings.predictiveBackEnabled,
                            onCheckedChange = settings::updatePredictiveBack,
                        )
                        SwitchPreference(
                            title = "触感反馈",
                            summary = null,
                            checked = settings.hapticFeedbackEnabled,
                            onCheckedChange = settings::updateHapticFeedback,
                        )
                    }
                }
            }
        }
    }
}

/** 复刻 NexioSchedule 的深浅色选择框：G2 超椭圆 3dp 蓝色选中外框 + 6dp 留白 + 20dp 连续圆角预览。 */
@Composable
private fun ThemeSelectionBox(
    label: String,
    darkPreview: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewScheme = if (darkPreview) NexioDarkColorScheme else NexioLightColorScheme

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .continuousBorder(
                    width = 3.dp,
                    color = if (selected) NexioTheme.colorScheme.primary else Color.Transparent,
                    cornerRadius = 26.dp,
                )
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .continuousClip(20.dp)
                    .background(previewScheme.surface)
                    .padding(14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .height(10.dp)
                                .clip(ContinuousCapsule())
                                .background(previewScheme.onSurface.copy(alpha = 0.85f)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .continuousClip(12.dp)
                                .background(previewScheme.surfaceContainer)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .continuousClip(6.dp)
                                        .background(previewScheme.primary),
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(8.dp)
                                        .clip(ContinuousCapsule())
                                        .background(previewScheme.onSurface.copy(alpha = 0.25f)),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .continuousClip(10.dp)
                                .background(previewScheme.surfaceContainer),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(20.dp)
                                .clip(ContinuousCapsule())
                                .background(previewScheme.surfaceContainer)
                                .padding(3.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(14.dp)
                                    .clip(ContinuousCapsule())
                                    .background(previewScheme.primary.copy(alpha = 0.22f)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NexioText(
            text = label,
            color = if (selected) NexioTheme.colorScheme.primary
            else NexioTheme.colorScheme.onSurfaceSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
