package com.wild0408.headsetpopup.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.R
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.wild0408.headsetpopup.update.AppUpdateChecker
import com.wild0408.headsetpopup.update.ReleaseInfo
import com.wild0408.headsetpopup.update.UpdateCheckResult
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Contacts
import com.haooz.nexioui.icon.extended.Link
import com.haooz.nexioui.icon.extended.Report
import com.haooz.nexioui.icon.extended.Update
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset
import kotlinx.coroutines.launch
import java.util.Locale

private const val REPO_URL = "https://github.com/wild0408/HyperOS-HeadsetPopupCustomizer"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val AUTHOR_URL = "https://github.com/wild0408"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appMeta = remember(context) {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: "2.0.0"
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 10L
        Triple(context.packageName, versionName, versionCode)
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateSummary by remember { mutableStateOf("从 GitHub Releases 检查最新版本") }
    var availableRelease by remember { mutableStateOf<ReleaseInfo?>(null) }

    DocumentPageScaffold(
        title = "关于",
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "应用图标",
                            modifier = Modifier.size(68.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        NexioText(
                            text = "耳机弹窗自定义工具",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NexioTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(NexioTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            NexioText(
                                text = "v${appMeta.second} (${appMeta.third})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        NexioText(
                            text = "HyperOS 原生耳机快连弹窗原位自定义模块",
                            fontSize = 13.sp,
                            color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "项目与反馈")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "检查更新",
                            summary = if (checkingUpdate) "正在连接 GitHub 检查最新版本…" else updateSummary,
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Green,
                                    icon = NexioIcons.Update,
                                )
                            },
                            onClick = {
                                if (checkingUpdate) return@ArrowPreference
                                checkingUpdate = true
                                scope.launch {
                                    when (val result = AppUpdateChecker.checkLatestRelease(appMeta.second)) {
                                        is UpdateCheckResult.UpdateAvailable -> {
                                            updateSummary = "发现新版本：${result.release.tagName}"
                                            availableRelease = result.release
                                        }
                                        is UpdateCheckResult.UpToDate -> {
                                            updateSummary = "已是最新版本（当前 v${result.currentVersion}）"
                                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                        is UpdateCheckResult.Error -> {
                                            updateSummary = result.message
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    checkingUpdate = false
                                }
                            },
                        )
                        ArrowPreference(
                            title = "GitHub 仓库",
                            summary = "wild0408/HyperOS-HeadsetPopupCustomizer",
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Blue,
                                    icon = NexioIcons.Link,
                                )
                            },
                            onClick = { openExternalUrl(context, REPO_URL) },
                        )
                        ArrowPreference(
                            title = "问题反馈",
                            summary = "提交 Bug 或功能建议",
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Orange,
                                    icon = NexioIcons.Report,
                                )
                            },
                            onClick = { openExternalUrl(context, ISSUES_URL) },
                        )
                        ArrowPreference(
                            title = "开发者",
                            summary = "wild0408",
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Purple,
                                    icon = NexioIcons.Contacts,
                                )
                            },
                            onClick = { openExternalUrl(context, AUTHOR_URL) },
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "模块信息")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "应用包名",
                            summary = "${appMeta.first}（点击复制）",
                            onClick = { copyToClipboard(context, "包名", appMeta.first) },
                        )
                        BasicComponent(
                            title = "工作方式",
                            summary = "HyperOS 原生 PairingDialog 原位 Hook",
                        )
                        BasicComponent(
                            title = "宿主作用域",
                            summary = "com.xiaomi.bluetooth",
                        )
                        BasicComponent(
                            title = "Xposed API",
                            summary = "libxposed API 102",
                        )
                        BasicComponent(
                            title = "界面框架",
                            summary = "NexioUI 0.1.1 · SoftGlass 0.1.3",
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "开源致谢")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "libxposed / LSPosed",
                            summary = "现代 Xposed API 与模块注入框架",
                            onClick = { openExternalUrl(context, "https://github.com/libxposed/api") },
                        )
                        ArrowPreference(
                            title = "NexioUI & SoftGlass",
                            summary = "HyperOS 风格 Compose 组件与液态玻璃渲染",
                            onClick = { openExternalUrl(context, "https://github.com/HaoZai000/NexioUI") },
                        )
                        ArrowPreference(
                            title = "Miuix",
                            summary = "MIUI / HyperOS 设计语言与图标体系",
                            onClick = { openExternalUrl(context, "https://github.com/compose-miuix-ui/miuix") },
                        )
                    }
                }
            }
        }

        val release = availableRelease
        OverlayDialog(
            show = release != null,
            title = if (release != null) "发现新版本 ${release.tagName}" else "发现新版本",
            liquidGlassBackdrop = backdrop,
            onDismissRequest = { availableRelease = null },
        ) {
            if (release != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val sizeLine = if (release.apkSizeBytes > 0L) {
                        val mb = release.apkSizeBytes / (1024.0 * 1024.0)
                        "${release.apkFileName ?: "APK"} · ${String.format(Locale.US, "%.2f MB", mb)}"
                    } else {
                        release.title
                    }
                    NexioText(
                        text = sizeLine,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NexioTheme.colorScheme.onBackground,
                    )
                    if (release.notes.isNotBlank()) {
                        NexioText(
                            text = release.notes,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlatTextButton(
                            text = "发布页",
                            onClick = {
                                availableRelease = null
                                openExternalUrl(context, release.htmlUrl)
                            },
                            colors = ButtonDefaults.textButtonColors(),
                            modifier = Modifier.weight(1f),
                        )
                        FlatTextButton(
                            text = if (release.apkDownloadUrl != null) "下载 APK" else "前往更新",
                            onClick = {
                                availableRelease = null
                                openExternalUrl(context, release.apkDownloadUrl ?: release.htmlUrl)
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
    }
}
