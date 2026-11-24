package com.wild0408.headsetpopup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.trigger.BluetoothProcessController
import com.wild0408.headsetpopup.trigger.BluetoothRestartResult
import com.wild0408.headsetpopup.ui.navigation.AppRoute
import com.wild0408.headsetpopup.ui.navigation.LocalAppRouter
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Info
import com.haooz.nexioui.icon.extended.Refresh
import com.haooz.nexioui.icon.extended.Theme
import com.haooz.nexioui.icon.extended.Tune
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentBackdrop: LayerBackdrop,
    chromeBackdrop: GlassBackdrop,
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val router = LocalAppRouter.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "2.0.0" }
    }

    var showRestartDialog by remember { mutableStateOf(false) }
    var restartingBluetooth by remember { mutableStateOf(false) }
    var restartSummary by remember { mutableStateOf("需 Root 权限 · 重载 ${BluetoothProcessController.TARGET_PACKAGE}") }
    var restartDialogError by remember { mutableStateOf<String?>(null) }

    MainTabPage(
        title = "设置",
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
                SmallTitle(text = "个性化")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "外观与材质",
                            summary = settings.materialLabel(),
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Purple,
                                    icon = NexioIcons.Theme,
                                )
                            },
                            onClick = { router.navigate(AppRoute.Appearance) },
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "模块与诊断")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "重启蓝牙进程",
                            summary = if (restartingBluetooth) "正在校验 Root 并重启进程…" else restartSummary,
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Orange,
                                    icon = NexioIcons.Refresh,
                                )
                            },
                            onClick = {
                                if (!restartingBluetooth) {
                                    restartDialogError = null
                                    showRestartDialog = true
                                }
                            },
                        )
                        ArrowPreference(
                            title = "调试中心",
                            summary = null,
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Green,
                                    icon = NexioIcons.Tune,
                                )
                            },
                            onClick = { router.navigate(AppRoute.Debug) },
                        )
                        ArrowPreference(
                            title = "关于",
                            summary = versionName,
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Gray,
                                    icon = NexioIcons.Info,
                                )
                            },
                            onClick = { router.navigate(AppRoute.About) },
                        )
                    }
                }
            }
        }

        OverlayDialog(
            show = showRestartDialog,
            title = "重启蓝牙进程",
            liquidGlassBackdrop = backdrop,
            onDismissRequest = {
                if (!restartingBluetooth) {
                    showRestartDialog = false
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NexioText(
                    text = "此操作需要 Root 权限。确认后将先检测 Root 授权，再强行停止 ${BluetoothProcessController.TARGET_PACKAGE} 以重新加载模块 Hook（不会关闭系统底层蓝牙）。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                )
                restartDialogError?.let { err ->
                    NexioText(
                        text = err,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = SettingsAccents.Red,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlatTextButton(
                        text = "取消",
                        onClick = { showRestartDialog = false },
                        enabled = !restartingBluetooth,
                        colors = ButtonDefaults.textButtonColors(),
                        modifier = Modifier.weight(1f),
                    )
                    FlatTextButton(
                        text = if (restartingBluetooth) "正在重启…" else "确认重启",
                        onClick = {
                            if (restartingBluetooth) return@FlatTextButton
                            restartingBluetooth = true
                            restartDialogError = null
                            scope.launch {
                                when (val result = BluetoothProcessController.restartBluetoothProcess()) {
                                    is BluetoothRestartResult.Success -> {
                                        restartSummary = "已重启 ${BluetoothProcessController.TARGET_PACKAGE}"
                                        showRestartDialog = false
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is BluetoothRestartResult.NoRoot -> {
                                        restartSummary = "未获取 Root 权限"
                                        restartDialogError = result.message
                                    }
                                    is BluetoothRestartResult.Failed -> {
                                        restartDialogError = result.message
                                    }
                                }
                                restartingBluetooth = false
                            }
                        },
                        enabled = !restartingBluetooth,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
