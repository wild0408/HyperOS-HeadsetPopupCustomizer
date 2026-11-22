package com.wild0408.headsetpopup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.config.LocalPopupConfig
import com.wild0408.headsetpopup.config.PopupConfigProvider
import com.wild0408.headsetpopup.trigger.RealFastConnectTrigger
import com.wild0408.headsetpopup.trigger.TriggerDevicePreset
import com.wild0408.headsetpopup.trigger.TriggerResult
import com.wild0408.headsetpopup.trigger.TriggerScenario
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.wild0408.headsetpopup.ui.components.OverlayDropdownMenu
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset

/**
 * 统一调试中心：集中收拢所有主动触发测试器、Hook 目标与运行通道信息。
 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val popupConfig = LocalPopupConfig.current
    val listState = rememberLazyListState()

    val availability = remember(context) { RealFastConnectTrigger.checkAvailability(context) }
    val selectedPreset = TriggerDevicePreset.fromDeviceId(settings.triggerDeviceId)
    val selectedScenario = TriggerScenario.fromKey(settings.triggerScenarioKey)
    val doc = popupConfig.document

    var triggerStatus by remember { mutableStateOf<String?>(null) }
    var triggerError by remember { mutableStateOf(false) }

    DocumentPageScaffold(
        title = "调试中心",
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
                SmallTitle(text = "主动触发真实弹窗测试")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "系统弹窗入口状态",
                            summary = availability.summary,
                        )
                        OverlayDropdownMenu(
                            title = "测试耳机型号",
                            summary = selectedPreset.deviceId,
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
                            title = "测试弹窗阶段",
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
                                text = "触发真实 FastConnect 弹窗",
                                onClick = {
                                    when (
                                        val result = RealFastConnectTrigger.trigger(
                                            context = context,
                                            preset = selectedPreset,
                                            scenario = selectedScenario,
                                        )
                                    ) {
                                        is TriggerResult.Launched -> {
                                            triggerError = false
                                            triggerStatus = "已调起 ${result.preset.displayName} (${result.preset.deviceId}) · ${result.scenario.label}"
                                        }
                                        is TriggerResult.Failed -> {
                                            triggerError = true
                                            triggerStatus = result.reason
                                        }
                                    }
                                },
                                enabled = availability.available,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            triggerStatus?.let { status ->
                                NexioText(
                                    text = status,
                                    fontSize = 12.sp,
                                    color = if (triggerError) {
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

            item {
                SmallTitle(text = "配置与跨进程通道")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "当前配置概览",
                            summary = "总开关：${if (doc.masterEnabled) "开启" else "关闭"} · 全局已定制 ${doc.globalProfile.activeCustomizationCount()} 项 · 型号方案 ${doc.deviceProfiles.size} 个",
                        )
                        BasicComponent(
                            title = "配置提供者 URI",
                            summary = "content://${PopupConfigProvider.AUTHORITY}",
                        )
                        BasicComponent(
                            title = "目标系统 Activity",
                            summary = "${RealFastConnectTrigger.TARGET_PACKAGE} / MiuiFastConnectActivity",
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "Hook 作用域与挂载点")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "LSPosed 作用域",
                            summary = "com.xiaomi.bluetooth",
                        )
                        BasicComponent(
                            title = "日志过滤",
                            summary = "adb logcat -s FastConnectHook",
                        )
                    }
                }
            }
        }
    }
}
