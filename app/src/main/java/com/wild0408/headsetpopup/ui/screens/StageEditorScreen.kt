package com.wild0408.headsetpopup.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.config.ConnectedStageConfig
import com.wild0408.headsetpopup.config.ConnectedTransitionMode
import com.wild0408.headsetpopup.config.ConnectedVisualMode
import com.wild0408.headsetpopup.config.LocalPopupConfig
import com.wild0408.headsetpopup.config.PopupCropRect
import com.wild0408.headsetpopup.config.PopupMediaSpec
import com.wild0408.headsetpopup.config.PopupProfile
import com.wild0408.headsetpopup.config.PopupScaleMode
import com.wild0408.headsetpopup.config.PopupStageKey
import com.wild0408.headsetpopup.config.StageOverrideConfig
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.wild0408.headsetpopup.ui.components.OverlayDropdownMenu
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
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.backdrop.GlassBackdrop

private enum class MediaPickSlot {
    STAGE_MAIN,
    CONNECTED_TRANSITION,
    CONNECTED_HEADSET,
    CONNECTED_CASE,
    CONNECTED_UNIFIED,
}

@Composable
fun StageEditorScreen(
    profileId: String,
    stageKey: String,
    onBack: () -> Unit,
) {
    val popupConfig = LocalPopupConfig.current
    val cropCoordinator = LocalMediaCropCoordinator.current
    val stage = remember(stageKey) { PopupStageKey.fromKey(stageKey) }
    val profile = popupConfig.document.findProfile(profileId)
    val listState = rememberLazyListState()

    var activePickSlot by remember { mutableStateOf<MediaPickSlot?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    fun slotMedia(current: PopupProfile, slot: MediaPickSlot): PopupMediaSpec = when (slot) {
        MediaPickSlot.STAGE_MAIN -> current.stageConfig(stage).media
        MediaPickSlot.CONNECTED_TRANSITION -> current.connectedStage.transitionMedia
        MediaPickSlot.CONNECTED_HEADSET -> current.connectedStage.headsetMedia
        MediaPickSlot.CONNECTED_CASE -> current.connectedStage.caseMedia
        MediaPickSlot.CONNECTED_UNIFIED -> current.connectedStage.unifiedMedia
    }

    fun applySlotMedia(slot: MediaPickSlot, spec: PopupMediaSpec) {
        popupConfig.updateProfile(profile.id) { current ->
            when (slot) {
                MediaPickSlot.STAGE_MAIN -> {
                    val s = current.stageConfig(stage)
                    current.withStageConfig(stage, s.copy(enabled = true, media = spec))
                }
                MediaPickSlot.CONNECTED_TRANSITION -> current.copy(
                    connectedStage = current.connectedStage.copy(
                        enabled = true,
                        transitionMode = ConnectedTransitionMode.CUSTOM,
                        transitionMedia = spec.copy(loop = false),
                    ),
                )
                MediaPickSlot.CONNECTED_HEADSET -> current.copy(
                    connectedStage = current.connectedStage.copy(
                        enabled = true,
                        visualMode = ConnectedVisualMode.SEPARATE,
                        headsetMedia = spec,
                    ),
                )
                MediaPickSlot.CONNECTED_CASE -> current.copy(
                    connectedStage = current.connectedStage.copy(
                        enabled = true,
                        visualMode = ConnectedVisualMode.SEPARATE,
                        caseMedia = spec,
                    ),
                )
                MediaPickSlot.CONNECTED_UNIFIED -> current.copy(
                    connectedStage = current.connectedStage.copy(
                        enabled = true,
                        visualMode = ConnectedVisualMode.UNIFIED,
                        unifiedMedia = spec,
                    ),
                )
            }
        }
    }

    fun defaultCropPresetForSlot(slot: MediaPickSlot): CropAspectPreset = when (slot) {
        MediaPickSlot.CONNECTED_UNIFIED -> CropAspectPreset.BANNER_4_3
        else -> CropAspectPreset.SQUARE
    }

    fun openRecrop(slot: MediaPickSlot) {
        val existing = slotMedia(profile, slot)
        if (!existing.isConfigured) return
        cropCoordinator.open(
            source = MediaCropSource.FromAsset(
                assetName = existing.assetName,
                displayName = existing.displayName,
                mediaType = existing.type,
            ),
            initialCrop = existing.crop,
            defaultPreset = defaultCropPresetForSlot(slot),
            title = "调整素材裁剪",
            onConfirm = { cropRect ->
                applySlotMedia(slot, existing.copy(crop = cropRect))
                statusIsError = false
                statusMessage = "已更新素材裁剪区域：${cropRect.summaryLabel()}"
            },
        )
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val slot = activePickSlot
        activePickSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        cropCoordinator.open(
            source = MediaCropSource.FromUri(uri),
            initialCrop = PopupCropRect.FULL,
            defaultPreset = defaultCropPresetForSlot(slot),
            title = "选择素材裁剪",
            onConfirm = { cropRect ->
                val defaultLoop = slot != MediaPickSlot.CONNECTED_TRANSITION
                val slotPrefix = "${profile.id}_${stage.key}_${slot.name.lowercase()}"
                val result = popupConfig.importMedia(
                    uri = uri,
                    slotPrefix = slotPrefix,
                    defaultScaleMode = PopupScaleMode.FIT_CENTER,
                    defaultLoop = defaultLoop,
                    crop = cropRect,
                )
                result.onSuccess { spec ->
                    applySlotMedia(slot, spec)
                    statusIsError = false
                    statusMessage = "已导入素材：${spec.displayName}（${spec.crop.summaryLabel()}）"
                }.onFailure { error ->
                    statusIsError = true
                    statusMessage = error.message ?: "导入素材失败"
                }
            },
        )
    }

    fun launchMediaPicker(slot: MediaPickSlot) {
        activePickSlot = slot
        mediaPicker.launch(arrayOf("image/*", "video/*"))
    }

    DocumentPageScaffold(
        title = stage.title,
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
                bottom = paddingValues.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = if (profile.isGlobal) "当前方案：全局默认" else "当前方案：${profile.name} (${profile.id})",
                    )
                }
            }

            if (stage == PopupStageKey.CONNECTED) {
                item {
                    ConnectedStageEditorBody(
                        config = profile.connectedStage,
                        backdrop = backdrop,
                        onUpdate = { next ->
                            popupConfig.updateProfile(profile.id) { it.copy(connectedStage = next) }
                        },
                        onPickSlot = ::launchMediaPicker,
                        onCropSlot = ::openRecrop,
                    )
                }
            } else {
                item {
                    SimpleStageEditorBody(
                        stage = stage,
                        config = profile.stageConfig(stage),
                        backdrop = backdrop,
                        onUpdate = { next ->
                            popupConfig.updateProfile(profile.id) { it.withStageConfig(stage, next) }
                        },
                        onPickMainMedia = { launchMediaPicker(MediaPickSlot.STAGE_MAIN) },
                        onCropMainMedia = { openRecrop(MediaPickSlot.STAGE_MAIN) },
                    )
                }
            }

            statusMessage?.let { msg ->
                item {
                    NexioText(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (statusIsError) SettingsAccents.Red else NexioTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

        }
    }
}

@Composable
private fun SimpleStageEditorBody(
    stage: PopupStageKey,
    config: StageOverrideConfig,
    backdrop: GlassBackdrop,
    onUpdate: (StageOverrideConfig) -> Unit,
    onPickMainMedia: () -> Unit,
    onCropMainMedia: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallTitle(text = "阶段开关")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            SwitchPreference(
                title = "启用「${stage.title}」自定义",
                checked = config.enabled,
                onCheckedChange = { onUpdate(config.copy(enabled = it)) },
            )
        }

        SmallTitle(text = "文案替换")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = config.title,
                    onValueChange = { onUpdate(config.copy(enabled = true, title = it)) },
                    label = "自定义标题",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = config.subtitle,
                    onValueChange = { onUpdate(config.copy(enabled = true, subtitle = it)) },
                    label = "提示文案",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (stage == PopupStageKey.FIND || stage == PopupStageKey.FAIL) {
                    TextField(
                        value = config.buttonText,
                        onValueChange = { onUpdate(config.copy(enabled = true, buttonText = it)) },
                        label = if (stage == PopupStageKey.FIND) "主按钮文案" else "重试按钮文案",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SmallTitle(text = "素材替换")
        MediaSlotEditorCard(
            slotTitle = "展示素材",
            slotSummary = "未替换 · 保留原版",
            media = config.media,
            allowLoopToggle = true,
            backdrop = backdrop,
            onPick = onPickMainMedia,
            onCrop = onCropMainMedia,
            onClear = { onUpdate(config.copy(media = PopupMediaSpec.NONE)) },
            onMediaChange = { updatedMedia -> onUpdate(config.copy(enabled = true, media = updatedMedia)) },
        )
    }
}

@Composable
private fun ConnectedStageEditorBody(
    config: ConnectedStageConfig,
    backdrop: GlassBackdrop,
    onUpdate: (ConnectedStageConfig) -> Unit,
    onPickSlot: (MediaPickSlot) -> Unit,
    onCropSlot: (MediaPickSlot) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallTitle(text = "阶段开关与文案")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            SwitchPreference(
                title = "启用「连接成功与电量」自定义",
                summary = null,
                checked = config.enabled,
                onCheckedChange = { onUpdate(config.copy(enabled = it)) },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = config.title,
                    onValueChange = { onUpdate(config.copy(enabled = true, title = it)) },
                    label = "自定义标题",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = config.buttonText,
                    onValueChange = { onUpdate(config.copy(enabled = true, buttonText = it)) },
                    label = "底部按钮文案",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SmallTitle(text = "连接过场")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            OverlayDropdownMenu(
                title = "过场模式",
                summary = null,
                entry = DropdownEntry(
                    items = ConnectedTransitionMode.entries.map { mode ->
                        DropdownItem(
                            text = mode.label,
                            selected = mode == config.transitionMode,
                            onClick = { onUpdate(config.copy(enabled = true, transitionMode = mode)) },
                        )
                    },
                ),
                liquidGlassBackdrop = backdrop,
            )
        }

        if (config.transitionMode == ConnectedTransitionMode.CUSTOM) {
            MediaSlotEditorCard(
                slotTitle = "过场素材",
                slotSummary = "未替换 · 保留原版",
                media = config.transitionMedia,
                allowLoopToggle = false,
                backdrop = backdrop,
                onPick = { onPickSlot(MediaPickSlot.CONNECTED_TRANSITION) },
                onCrop = { onCropSlot(MediaPickSlot.CONNECTED_TRANSITION) },
                onClear = { onUpdate(config.copy(transitionMedia = PopupMediaSpec.NONE)) },
                onMediaChange = { m -> onUpdate(config.copy(enabled = true, transitionMedia = m.copy(loop = false))) },
            )
        }

        SmallTitle(text = "电量页素材")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            OverlayDropdownMenu(
                title = "替换模式",
                summary = null,
                entry = DropdownEntry(
                    items = ConnectedVisualMode.entries.map { mode ->
                        DropdownItem(
                            text = mode.label,
                            selected = mode == config.visualMode,
                            onClick = { onUpdate(config.copy(enabled = true, visualMode = mode)) },
                        )
                    },
                ),
                liquidGlassBackdrop = backdrop,
            )
        }

        if (config.visualMode == ConnectedVisualMode.SEPARATE) {
            MediaSlotEditorCard(
                slotTitle = "耳机图",
                slotSummary = "未替换 · 保留原版",
                media = config.headsetMedia,
                allowLoopToggle = true,
                backdrop = backdrop,
                onPick = { onPickSlot(MediaPickSlot.CONNECTED_HEADSET) },
                onCrop = { onCropSlot(MediaPickSlot.CONNECTED_HEADSET) },
                onClear = { onUpdate(config.copy(headsetMedia = PopupMediaSpec.NONE)) },
                onMediaChange = { m -> onUpdate(config.copy(enabled = true, headsetMedia = m)) },
            )
            MediaSlotEditorCard(
                slotTitle = "充电盒图",
                slotSummary = "未替换 · 保留原版",
                media = config.caseMedia,
                allowLoopToggle = true,
                backdrop = backdrop,
                onPick = { onPickSlot(MediaPickSlot.CONNECTED_CASE) },
                onCrop = { onCropSlot(MediaPickSlot.CONNECTED_CASE) },
                onClear = { onUpdate(config.copy(caseMedia = PopupMediaSpec.NONE)) },
                onMediaChange = { m -> onUpdate(config.copy(enabled = true, caseMedia = m)) },
            )
        } else {
            MediaSlotEditorCard(
                slotTitle = "整块素材",
                slotSummary = "未替换 · 保留原版",
                media = config.unifiedMedia,
                allowLoopToggle = true,
                backdrop = backdrop,
                onPick = { onPickSlot(MediaPickSlot.CONNECTED_UNIFIED) },
                onCrop = { onCropSlot(MediaPickSlot.CONNECTED_UNIFIED) },
                onClear = { onUpdate(config.copy(unifiedMedia = PopupMediaSpec.NONE)) },
                onMediaChange = { m -> onUpdate(config.copy(enabled = true, unifiedMedia = m)) },
            )
        }
    }
}

@Composable
internal fun MediaSlotEditorCard(
    slotTitle: String,
    slotSummary: String,
    media: PopupMediaSpec,
    allowLoopToggle: Boolean,
    backdrop: GlassBackdrop,
    onPick: () -> Unit,
    onCrop: () -> Unit,
    onClear: () -> Unit,
    onMediaChange: (PopupMediaSpec) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = slotTitle,
                summary = if (media.isConfigured) media.summaryLabel() else slotSummary,
            )
            if (media.isConfigured) {
                OverlayDropdownMenu(
                    title = "缩放方式",
                    summary = null,
                    entry = DropdownEntry(
                        items = PopupScaleMode.entries.map { mode ->
                            DropdownItem(
                                text = mode.label,
                                selected = mode == media.scaleMode,
                                onClick = { onMediaChange(media.copy(scaleMode = mode)) },
                            )
                        },
                    ),
                    liquidGlassBackdrop = backdrop,
                )
                if (allowLoopToggle) {
                    SwitchPreference(
                        title = "循环播放",
                        summary = null,
                        checked = media.loop,
                        onCheckedChange = { onMediaChange(media.copy(loop = it)) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatTextButton(
                    text = if (media.isConfigured) "更换素材" else "选择素材",
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                if (media.isConfigured) {
                    FlatTextButton(
                        text = "裁剪",
                        onClick = onCrop,
                        colors = ButtonDefaults.textButtonColors(),
                    )
                    FlatTextButton(
                        text = "清除",
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(),
                    )
                }
            }
        }
    }
}

