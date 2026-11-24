package com.wild0408.headsetpopup.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.config.ConnectedTransitionMode
import com.wild0408.headsetpopup.config.ConnectedVisualMode
import com.wild0408.headsetpopup.config.LocalPopupConfig
import com.wild0408.headsetpopup.config.PopupButtonStyle
import com.wild0408.headsetpopup.config.PopupCropRect
import com.wild0408.headsetpopup.config.PopupForegroundTone
import com.wild0408.headsetpopup.config.PopupMediaSpec
import com.wild0408.headsetpopup.config.PopupProfile
import com.wild0408.headsetpopup.config.PopupScaleMode
import com.wild0408.headsetpopup.config.PopupStageKey
import com.wild0408.headsetpopup.ui.components.AddModelProfileDialog
import com.wild0408.headsetpopup.ui.components.OverlayDropdownMenu
import com.wild0408.headsetpopup.ui.components.PopupVisualPreviewCard
import com.wild0408.headsetpopup.ui.components.ProfileChipStrip
import com.wild0408.headsetpopup.ui.components.rememberCroppedMediaThumbnail
import com.wild0408.headsetpopup.ui.navigation.AppRoute
import com.wild0408.headsetpopup.ui.navigation.LocalAppRouter
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.dropdown.DropdownEntry
import com.haooz.nexioui.dropdown.DropdownItem
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Close2
import com.haooz.nexioui.icon.extended.Image
import com.haooz.nexioui.icon.extended.Layers
import com.haooz.nexioui.icon.extended.Refresh
import com.haooz.nexioui.icon.extended.SearchDevice
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.slider.Slider
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.softglass.shape.continuousClip
import kotlin.math.roundToInt

@Composable
fun StylePreviewScreen(
    contentBackdrop: LayerBackdrop,
    chromeBackdrop: GlassBackdrop,
) {
    val context = LocalContext.current
    val popupConfig = LocalPopupConfig.current
    val router = LocalAppRouter.current
    val cropCoordinator = LocalMediaCropCoordinator.current
    val listState = rememberLazyListState()

    val doc = popupConfig.document
    val profile = popupConfig.activeProfile
    val bg = profile.background
    val bgThumb = rememberCroppedMediaThumbnail(media = bg.media, maxEdge = 160)

    var showAddModelDialog by remember { mutableStateOf(false) }
    var showDestructiveConfirmDialog by remember { mutableStateOf(false) }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val targetProfileId = profile.id
        val targetScaleMode = bg.media.scaleMode
        cropCoordinator.open(
            source = MediaCropSource.FromUri(uri),
            initialCrop = PopupCropRect.FULL,
            defaultPreset = CropAspectPreset.POPUP_CARD,
            title = "弹窗背景裁剪",
            onConfirm = { cropRect ->
                val res = popupConfig.importMedia(
                    uri = uri,
                    slotPrefix = "${targetProfileId}_bg",
                    defaultScaleMode = targetScaleMode,
                    defaultLoop = true,
                    crop = cropRect,
                )
                res.onSuccess { spec ->
                    popupConfig.updateProfile(targetProfileId) { current ->
                        current.copy(
                            background = current.background.copy(
                                enabled = true,
                                media = spec,
                            ),
                        )
                    }
                    showToast("已应用背景：${spec.displayName}")
                }.onFailure { error ->
                    showToast(error.message ?: "导入背景素材失败")
                }
            },
        )
    }

    MainTabPage(
        title = "弹窗自定义",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val allProfiles = remember(doc) {
                    listOf(doc.globalProfile) + doc.deviceProfiles
                }
                ProfileChipStrip(
                    profiles = allProfiles,
                    activeProfileId = profile.id,
                    onSelectProfile = popupConfig::selectProfile,
                    onAddProfile = { showAddModelDialog = true },
                )
            }

            item {
                val resolvedPreview = remember(doc, profile) {
                    doc.resolveForProfile(profile)
                }
                PopupVisualPreviewCard(
                    resolved = resolvedPreview,
                    fallbackDeviceName = if (profile.isGlobal) "Redmi Buds 8 Pro" else profile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            item {
                SmallTitle(text = "卡片背景")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val bgSwitchSummary = when {
                            !profile.isGlobal && profile.inheritGlobal && !bg.enabled -> "继承全局"
                            !profile.isGlobal && profile.inheritGlobal && bg.enabled && !bg.media.isConfigured -> "未选素材 · 恢复原版背景"
                            !bg.enabled && bg.media.isConfigured -> "已保存素材：${bg.media.displayName.ifBlank { bg.media.assetName }}"
                            else -> null
                        }
                        SwitchPreference(
                            title = "自定义卡片背景",
                            summary = bgSwitchSummary,
                            checked = bg.enabled,
                            onCheckedChange = { enabled ->
                                popupConfig.updateProfile(profile.id) { current ->
                                    current.copy(background = current.background.copy(enabled = enabled))
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = bg.enabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                BasicComponent(
                                    title = "背景素材",
                                    summary = bg.media.summaryLabel(),
                                    startAction = {
                                        if (bg.media.isConfigured && bgThumb != null) {
                                            Image(
                                                bitmap = bgThumb,
                                                contentDescription = "背景缩略图",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .continuousClip(10.dp)
                                                    .border(
                                                        width = 0.5.dp,
                                                        color = NexioTheme.colorScheme.outline.copy(alpha = 0.25f),
                                                        shape = ContinuousRoundedRectangle(10.dp),
                                                    ),
                                            )
                                        } else {
                                            SquareAccentIcon(
                                                accent = SettingsAccents.Purple,
                                                icon = NexioIcons.Image,
                                            )
                                        }
                                    },
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FlatTextButton(
                                        text = if (bg.media.isConfigured) "更换素材" else "选择素材",
                                        onClick = { bgPicker.launch(arrayOf("image/*", "video/*")) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                    )
                                    if (bg.media.isConfigured) {
                                        FlatTextButton(
                                            text = "裁剪",
                                            onClick = {
                                                val targetProfileId = profile.id
                                                val currentMedia = bg.media
                                                cropCoordinator.open(
                                                    source = MediaCropSource.FromAsset(
                                                        assetName = currentMedia.assetName,
                                                        displayName = currentMedia.displayName,
                                                        mediaType = currentMedia.type,
                                                    ),
                                                    initialCrop = currentMedia.crop,
                                                    defaultPreset = CropAspectPreset.POPUP_CARD,
                                                    title = "调整背景裁剪",
                                                    onConfirm = { cropRect ->
                                                        popupConfig.updateProfile(targetProfileId) { current ->
                                                            current.copy(
                                                                background = current.background.copy(
                                                                    enabled = true,
                                                                    media = current.background.media.copy(crop = cropRect),
                                                                ),
                                                            )
                                                        }
                                                        showToast("已更新裁剪区域：${cropRect.summaryLabel()}")
                                                    },
                                                )
                                            },
                                            colors = ButtonDefaults.textButtonColors(),
                                        )
                                        FlatTextButton(
                                            text = "清除",
                                            onClick = {
                                                popupConfig.updateProfile(profile.id) { current ->
                                                    current.copy(
                                                        background = current.background.copy(
                                                            enabled = false,
                                                            media = PopupMediaSpec(scaleMode = PopupScaleMode.CENTER_CROP),
                                                        ),
                                                    )
                                                }
                                                showToast(
                                                    if (!profile.isGlobal && profile.inheritGlobal) {
                                                        "已清除当前型号背景，恢复继承全局"
                                                    } else {
                                                        "已恢复原生背景"
                                                    },
                                                )
                                            },
                                            colors = ButtonDefaults.textButtonColors(),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = bg.media.isConfigured,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        OverlayDropdownMenu(
                                            title = "背景缩放",
                                            summary = null,
                                            entry = DropdownEntry(
                                                items = PopupScaleMode.entries.map { mode ->
                                                    DropdownItem(
                                                        text = mode.label,
                                                        selected = mode == bg.media.scaleMode,
                                                        onClick = {
                                                            popupConfig.updateProfile(profile.id) { current ->
                                                                current.copy(
                                                                    background = current.background.copy(
                                                                        media = current.background.media.copy(scaleMode = mode),
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                    )
                                                },
                                            ),
                                            liquidGlassBackdrop = backdrop,
                                        )
                                        OverlayDropdownMenu(
                                            title = "文字色调",
                                            summary = null,
                                            entry = DropdownEntry(
                                                items = PopupForegroundTone.entries.map { tone ->
                                                    DropdownItem(
                                                        text = tone.label,
                                                        selected = tone == bg.foregroundTone,
                                                        onClick = {
                                                            popupConfig.updateProfile(profile.id) { current ->
                                                                current.copy(
                                                                    background = current.background.copy(foregroundTone = tone),
                                                                )
                                                            }
                                                        },
                                                    )
                                                },
                                            ),
                                            liquidGlassBackdrop = backdrop,
                                        )
                                        BasicComponent(
                                            title = "背景不透明度",
                                            summary = "${(bg.alpha * 100f).roundToInt()}%",
                                            bottomAction = {
                                                Slider(
                                                    value = bg.alpha,
                                                    onValueChange = { value ->
                                                        popupConfig.updateProfile(profile.id) { current ->
                                                            current.copy(background = current.background.copy(alpha = value))
                                                        }
                                                    },
                                                    valueRange = 0.15f..1.0f,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            },
                                        )
                                        BasicComponent(
                                            title = "暗化遮罩",
                                            summary = "${(bg.scrimAlpha * 100f).roundToInt()}%",
                                            bottomAction = {
                                                Slider(
                                                    value = bg.scrimAlpha,
                                                    onValueChange = { value ->
                                                        popupConfig.updateProfile(profile.id) { current ->
                                                            current.copy(background = current.background.copy(scrimAlpha = value))
                                                        }
                                                    },
                                                    valueRange = 0.0f..0.80f,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "按钮材质")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    val allowInherit = !profile.isGlobal && profile.inheritGlobal
                    val styleOptions = remember(allowInherit) {
                        if (allowInherit) {
                            PopupButtonStyle.entries
                        } else {
                            PopupButtonStyle.entries.filter { it != PopupButtonStyle.INHERIT }
                        }
                    }
                    val displayedStyle = if (!allowInherit && profile.buttonStyle == PopupButtonStyle.INHERIT) {
                        PopupButtonStyle.NATIVE
                    } else {
                        profile.buttonStyle
                    }
                    OverlayDropdownMenu(
                        title = "底部按钮与关闭键",
                        summary = null,
                        entry = DropdownEntry(
                            items = styleOptions.map { style ->
                                DropdownItem(
                                    text = style.label,
                                    selected = style == displayedStyle,
                                    onClick = {
                                        popupConfig.updateProfile(profile.id) { current ->
                                            current.copy(buttonStyle = style)
                                        }
                                        showToast("已切换按钮材质：${style.label}")
                                    },
                                )
                            },
                        ),
                        liquidGlassBackdrop = backdrop,
                    )
                }
            }

            item {
                SmallTitle(text = "弹窗阶段")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val fallbackGlobal = if (!profile.isGlobal && profile.inheritGlobal && doc.globalProfile.enabled) {
                            doc.globalProfile
                        } else {
                            null
                        }
                        StageEntryRow(
                            stage = PopupStageKey.FIND,
                            profile = profile,
                            fallbackGlobal = fallbackGlobal,
                            accent = SettingsAccents.Blue,
                            icon = NexioIcons.SearchDevice,
                            onClick = {
                                router.navigate(AppRoute.StageEditor(profile.id, PopupStageKey.FIND.key))
                            },
                        )
                        StageEntryRow(
                            stage = PopupStageKey.CONNECTED,
                            profile = profile,
                            fallbackGlobal = fallbackGlobal,
                            accent = SettingsAccents.Green,
                            icon = NexioIcons.Layers,
                            onClick = {
                                router.navigate(AppRoute.StageEditor(profile.id, PopupStageKey.CONNECTED.key))
                            },
                        )
                        StageEntryRow(
                            stage = PopupStageKey.CONNECTING,
                            profile = profile,
                            fallbackGlobal = fallbackGlobal,
                            accent = SettingsAccents.Purple,
                            icon = NexioIcons.Image,
                            onClick = {
                                router.navigate(AppRoute.StageEditor(profile.id, PopupStageKey.CONNECTING.key))
                            },
                        )
                        StageEntryRow(
                            stage = PopupStageKey.FAIL,
                            profile = profile,
                            fallbackGlobal = fallbackGlobal,
                            accent = SettingsAccents.Red,
                            icon = NexioIcons.Close2,
                            onClick = {
                                router.navigate(AppRoute.StageEditor(profile.id, PopupStageKey.FAIL.key))
                            },
                        )
                        StageEntryRow(
                            stage = PopupStageKey.RESET,
                            profile = profile,
                            fallbackGlobal = fallbackGlobal,
                            accent = SettingsAccents.Orange,
                            icon = NexioIcons.Refresh,
                            onClick = {
                                router.navigate(AppRoute.StageEditor(profile.id, PopupStageKey.RESET.key))
                            },
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "方案设置")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = if (profile.isGlobal) "启用全局默认方案" else "启用当前型号方案",
                            summary = null,
                            checked = profile.enabled,
                            onCheckedChange = { enabled ->
                                popupConfig.updateProfile(profile.id) { it.copy(enabled = enabled) }
                            },
                        )
                        if (!profile.isGlobal) {
                            SwitchPreference(
                                title = "未配置项继承全局",
                                summary = null,
                                checked = profile.inheritGlobal,
                                onCheckedChange = { inherit ->
                                    popupConfig.updateProfile(profile.id) { current ->
                                        current.copy(
                                            inheritGlobal = inherit,
                                            buttonStyle = if (!inherit && current.buttonStyle == PopupButtonStyle.INHERIT) {
                                                PopupButtonStyle.NATIVE
                                            } else {
                                                current.buttonStyle
                                            },
                                        )
                                    }
                                },
                            )
                        }
                        FlatTextButton(
                            text = if (profile.isGlobal) "重置为原版" else "删除当前型号方案",
                            onClick = { showDestructiveConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            colors = ButtonDefaults.textButtonColors(),
                        )
                    }
                }
            }
        }

        AddModelProfileDialog(
            show = showAddModelDialog,
            backdrop = backdrop,
            onDismiss = { showAddModelDialog = false },
            onConfirm = { id, name ->
                val created = popupConfig.ensureDeviceProfile(id, name)
                showToast("已切换到 ${created.name} (${created.id})")
                showAddModelDialog = false
            },
        )

        OverlayDialog(
            show = showDestructiveConfirmDialog,
            title = if (profile.isGlobal) "重置全局方案" else "删除型号方案",
            liquidGlassBackdrop = backdrop,
            onDismissRequest = { showDestructiveConfirmDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NexioText(
                    text = if (profile.isGlobal) {
                        "将清空全局默认方案的自定义背景、按钮材质及全部阶段配置，恢复为系统原版样式。"
                    } else {
                        "将删除「${profile.name} (${profile.id})」的全部独立自定义配置，之后该型号将回退使用全局默认方案。"
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlatTextButton(
                        text = "取消",
                        onClick = { showDestructiveConfirmDialog = false },
                        colors = ButtonDefaults.textButtonColors(),
                        modifier = Modifier.weight(1f),
                    )
                    FlatTextButton(
                        text = if (profile.isGlobal) "确认重置" else "确认删除",
                        onClick = {
                            showDestructiveConfirmDialog = false
                            if (profile.isGlobal) {
                                popupConfig.resetProfile(profile.id)
                                showToast("已重置全局方案")
                            } else {
                                val deletedName = profile.name
                                popupConfig.deleteDeviceProfile(profile.id)
                                showToast("已删除型号方案：$deletedName")
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StageEntryRow(
    stage: PopupStageKey,
    profile: PopupProfile,
    fallbackGlobal: PopupProfile?,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = stage.title,
        summary = stageDetailSummary(profile, fallbackGlobal, stage),
        startAction = {
            SquareAccentIcon(
                accent = accent,
                icon = icon,
            )
        },
        onClick = onClick,
    )
}

private fun stageDetailSummary(
    profile: PopupProfile,
    fallbackGlobal: PopupProfile?,
    stage: PopupStageKey,
): String {
    if (stage == PopupStageKey.CONNECTED) {
        val c = profile.connectedStage
        if (!c.hasAnyOverride) {
            return if (fallbackGlobal != null && fallbackGlobal.connectedStage.hasAnyOverride) {
                "继承全局"
            } else {
                "系统原版"
            }
        }
        val parts = mutableListOf<String>()
        when (c.transitionMode) {
            ConnectedTransitionMode.NATIVE -> Unit
            ConnectedTransitionMode.CUSTOM -> if (c.transitionMedia.isConfigured) parts += "过场"
            ConnectedTransitionMode.SKIP -> parts += "跳过过场"
        }
        when (c.visualMode) {
            ConnectedVisualMode.SEPARATE -> {
                if (c.headsetMedia.isConfigured) parts += "耳机图"
                if (c.caseMedia.isConfigured) parts += "充电盒图"
            }
            ConnectedVisualMode.UNIFIED -> {
                if (c.unifiedMedia.isConfigured) parts += "整块素材"
            }
        }
        if (c.title.isNotBlank()) parts += "标题"
        if (c.buttonText.isNotBlank()) parts += "按钮"
        return parts.joinToString(" · ").ifBlank { "已自定义" }
    }
    val s = profile.stageConfig(stage)
    if (!s.hasAnyOverride) {
        return if (fallbackGlobal != null && fallbackGlobal.stageConfig(stage).hasAnyOverride) {
            "继承全局"
        } else {
            "系统原版"
        }
    }
    val parts = mutableListOf<String>()
    if (s.media.isConfigured) parts += s.media.displayName.ifBlank { "素材" }
    if (s.title.isNotBlank()) parts += "标题"
    if (s.subtitle.isNotBlank()) parts += "文案"
    if (s.buttonText.isNotBlank()) parts += "按钮"
    return parts.joinToString(" · ").ifBlank { "已自定义" }
}
