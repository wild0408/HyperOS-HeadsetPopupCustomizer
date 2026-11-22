package com.wild0408.headsetpopup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.config.LocalPopupConfig
import com.wild0408.headsetpopup.trigger.RealFastConnectTrigger
import com.wild0408.headsetpopup.trigger.TriggerDevicePreset
import com.wild0408.headsetpopup.trigger.TriggerResult
import com.wild0408.headsetpopup.trigger.TriggerScenario
import com.wild0408.headsetpopup.ui.components.OverlayDropdownMenu
import com.wild0408.headsetpopup.ui.components.PopupVisualPreviewCard
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Tune
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.shape.continuousClip

@Composable
fun HomeScreen(
    contentBackdrop: LayerBackdrop,
    chromeBackdrop: GlassBackdrop,
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val popupConfig = LocalPopupConfig.current
    val listState = rememberLazyListState()
    val availability = remember(context) { RealFastConnectTrigger.checkAvailability(context) }
    val selectedPreset = TriggerDevicePreset.fromDeviceId(settings.triggerDeviceId)
    val selectedScenario = TriggerScenario.fromKey(settings.triggerScenarioKey)
    val doc = popupConfig.document
    val masterEnabled = doc.masterEnabled
    val globalOverrideCount = doc.globalProfile.activeCustomizationCount()
    val modelProfileCount = doc.deviceProfiles.size
    var triggerFeedback by remember { mutableStateOf<String?>(null) }
    var triggerIsError by remember { mutableStateOf(false) }

    MainTabPage(
        title = "耳机弹窗",
        contentBackdrop = contentBackdrop,
        chromeBackdrop = chromeBackdrop,
    ) { contentPadding, scrollBehavior, backdrop ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = if (masterEnabled) "弹窗自定义已开启" else "弹窗自定义已暂停",
                        summary = if (masterEnabled) {
                            "全局已定制 $globalOverrideCount 项 · 型号方案 $modelProfileCount 个"
                        } else {
                            "已暂停 · 使用系统原版弹窗"
                        },
                        checked = masterEnabled,
                        onCheckedChange = popupConfig::updateMasterEnabled,
                        startAction = {
                            SquareAccentIcon(
                                accent = if (masterEnabled) SettingsAccents.Blue else SettingsAccents.Gray,
                                icon = NexioIcons.Tune,
                            )
                        },
                    )
                }
            }

            item {
                SmallTitle(text = "当前预览")
                val resolvedPreview = remember(doc, selectedPreset) {
                    doc.resolveForDevice(selectedPreset.deviceId)
                }
                PopupVisualPreviewCard(
                    resolved = resolvedPreview,
                    fallbackDeviceName = selectedPreset.displayName,
                )
            }

            item {
                SmallTitle(text = "快速触发")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownMenu(
                            title = "耳机型号",
                            summary = null,
                            entry = DropdownEntry(
                                items = TriggerDevicePreset.entries.map { preset ->
                                    DropdownItem(
                                        text = preset.displayName,
                                        summary = preset.deviceId,
                                        selected = preset == selectedPreset,
                                        onClick = { settings.updateTriggerDeviceId(preset.deviceId) },
                                    )
                                },
                            ),
                            liquidGlassBackdrop = backdrop,
                        )
                        OverlayDropdownMenu(
                            title = "弹窗阶段",
                            summary = null,
                            entry = DropdownEntry(
                                items = TriggerScenario.entries.map { scenario ->
                                    DropdownItem(
                                        text = scenario.label,
                                        selected = scenario == selectedScenario,
                                        onClick = { settings.updateTriggerScenario(scenario) },
                                    )
                                },
                            ),
                            liquidGlassBackdrop = backdrop,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            FlatTextButton(
                                text = "立即触发",
                                onClick = {
                                    when (val result = RealFastConnectTrigger.trigger(context, selectedPreset, selectedScenario)) {
                                        is TriggerResult.Launched -> {
                                            triggerIsError = false
                                            triggerFeedback = "已触发 ${result.preset.displayName} · ${result.scenario.label}"
                                        }
                                        is TriggerResult.Failed -> {
                                            triggerIsError = true
                                            triggerFeedback = result.reason
                                        }
                                    }
                                },
                                enabled = availability.available,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            val inlineHint = if (!availability.available) {
                                availability.summary
                            } else {
                                triggerFeedback
                            }
                            inlineHint?.let { feedback ->
                                NexioText(
                                    text = feedback,
                                    fontSize = 12.sp,
                                    color = if (!availability.available || triggerIsError) {
                                        SettingsAccents.Red
                                    } else {
                                        NexioTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SquareAccentIcon(
    accent: Color,
    icon: ImageVector,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .continuousClip(10.dp)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}
