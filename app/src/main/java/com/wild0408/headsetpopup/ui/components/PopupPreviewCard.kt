package com.wild0408.headsetpopup.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wild0408.headsetpopup.config.PopupButtonStyle
import com.wild0408.headsetpopup.config.PopupConfigStorage
import com.wild0408.headsetpopup.config.PopupForegroundTone
import com.wild0408.headsetpopup.config.PopupMediaSpec
import com.wild0408.headsetpopup.config.PopupMediaType
import com.wild0408.headsetpopup.config.PopupProfile
import com.wild0408.headsetpopup.config.PopupScaleMode
import com.wild0408.headsetpopup.config.ResolvedPopupConfig
import com.wild0408.headsetpopup.hook.GlassButtonStyler
import com.wild0408.headsetpopup.trigger.TriggerDevicePreset
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Add
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.material.NexioGlassStyle
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.LiquidLens
import com.haooz.softglass.glass.NoneFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.softglass.shape.continuousClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private object PreviewThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(8) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, image: ImageBitmap) = synchronized(cache) { cache.put(key, image) }
}

/**
 * 异步加载并缓存已导入素材的裁剪后缩略图（支持图片/GIF首帧与MP4视频首帧），
 * 首页与自定义页共享同一份缓存，避免切页重复解码。
 */
@Composable
fun rememberCroppedMediaThumbnail(
    media: PopupMediaSpec,
    maxEdge: Int = 720,
): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = remember(media.assetName, media.type, media.crop, maxEdge) {
        if (!media.isConfigured) {
            null
        } else {
            val c = media.crop.normalized()
            "${media.assetName}|${media.type.wire}|$maxEdge|${c.left},${c.top},${c.right},${c.bottom}"
        }
    }
    var image by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { PreviewThumbnailCache.get(it) })
    }

    LaunchedEffect(cacheKey) {
        if (cacheKey == null) {
            image = null
            return@LaunchedEffect
        }
        PreviewThumbnailCache.get(cacheKey)?.let { cached ->
            image = cached
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeCroppedPreviewBitmap(context, media, maxEdge)?.asImageBitmap()
        }
        if (decoded != null) {
            PreviewThumbnailCache.put(cacheKey, decoded)
        }
        image = decoded
    }

    return image
}

/**
 * 实时弹窗视觉预览卡片：直接渲染与真实系统弹窗完全一致的 ResolvedPopupConfig，
 * 确保继承全局、覆写恢复原版背景及按钮材质与真机效果严格一致。
 */
@Composable
fun PopupVisualPreviewCard(
    resolved: ResolvedPopupConfig,
    fallbackDeviceName: String,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = NexioTheme.isDark
    val bg = resolved.background
    val previewBitmap = rememberCroppedMediaThumbnail(
        media = if (resolved.active && bg.isActive) bg.media else PopupMediaSpec(),
        maxEdge = 800,
    )
    val hasCustomBg = resolved.active && bg.isActive && previewBitmap != null

    val preferLightText = when {
        !hasCustomBg -> isDarkTheme
        bg.foregroundTone == PopupForegroundTone.LIGHT -> true
        bg.foregroundTone == PopupForegroundTone.DARK -> false
        else -> isDarkTheme || bg.scrimAlpha >= 0.22f
    }
    val titleColor = if (preferLightText) Color(0xFFF6F8FC) else Color(0xFF15181F)
    val subtitleColor = if (preferLightText) Color(0xCCEEF2F8) else Color(0x99353B48)

    val autoDarkGlass = when (bg.foregroundTone) {
        PopupForegroundTone.LIGHT -> true
        PopupForegroundTone.DARK -> false
        PopupForegroundTone.AUTO -> isDarkTheme
    }
    val baseSurface = if (isDarkTheme) Color(0xFF222222) else Color(0xFFFFFFFF)
    val effectiveButtonStyle = if (resolved.active) resolved.buttonStyle else PopupButtonStyle.NATIVE
    val buttonSpec = remember(effectiveButtonStyle, autoDarkGlass, preferLightText, isDarkTheme) {
        resolveButtonGlassSpec(
            style = effectiveButtonStyle,
            autoDark = autoDarkGlass,
            preferLightText = preferLightText,
            isDarkTheme = isDarkTheme,
        )
    }
    val cardBackdrop = rememberLayerBackdrop()
    val bgRecordKey = remember(
        hasCustomBg,
        previewBitmap,
        bg.media.scaleMode,
        bg.alpha,
        bg.scrimAlpha,
        isDarkTheme,
    ) {
        listOf(hasCustomBg, previewBitmap, bg.media.scaleMode, bg.alpha, bg.scrimAlpha, isDarkTheme)
    }

    val findStage = resolved.findStage
    val stagePreviewBitmap = rememberCroppedMediaThumbnail(
        media = if (resolved.active && findStage.enabled && findStage.media.isConfigured) {
            findStage.media
        } else {
            PopupMediaSpec()
        },
        maxEdge = 640,
    )
    val displayDevice = fallbackDeviceName.ifBlank { "Redmi Buds 8 Pro" }
    val deviceTitle = findStage.title.takeIf { resolved.active && findStage.enabled && it.isNotBlank() }
        ?.replace("{device}", displayDevice)
        ?: displayDevice
    val primaryLabel = findStage.buttonText.takeIf { resolved.active && findStage.enabled && it.isNotBlank() }
        ?: "连接"
    val previewSubtitle = findStage.subtitle.takeIf { resolved.active && findStage.enabled && it.isNotBlank() }

    // 严格按 HyperOS 原生 PairingDialog（基准 368dp × 422dp，圆角 32dp）等比缩放还原。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(368f / 422f),
    ) {
        val scale = (maxWidth / 368.dp).coerceIn(0.65f, 1.15f)
        val cardRadius = 32.dp * scale
        val closeSize = 28.dp * scale
        val closeMargin = 20.dp * scale
        val titleTopMargin = 32.dp * scale
        val titleHorizontalPadding = 52.dp * scale
        val stageMediaSize = 200.dp * scale
        val buttonHorizontalMargin = 24.dp * scale
        val buttonBottomMargin = 28.dp * scale
        val buttonHeight = 50.dp * scale

        Box(
            modifier = Modifier
                .fillMaxSize()
                .continuousClip(cardRadius)
                .background(baseSurface)
                .border(
                    width = 0.8.dp,
                    color = NexioTheme.colorScheme.outline.copy(alpha = 0.22f),
                    shape = ContinuousRoundedRectangle(cardRadius),
                ),
        ) {
            // 将预览卡片背景录入局部 LayerBackdrop，供右上角关闭键与底部按钮实时采样真实 SoftGlass 模糊/折射。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (buttonSpec.useRealGlass) {
                            Modifier.layerBackdrop(
                                backdrop = cardBackdrop,
                                recordKey = bgRecordKey,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(baseSurface),
            ) {
                if (hasCustomBg) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "弹窗背景预览",
                        contentScale = if (bg.media.scaleMode == PopupScaleMode.FIT_CENTER) {
                            ContentScale.Fit
                        } else {
                            ContentScale.Crop
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(bg.alpha.coerceIn(0.15f, 1f)),
                    )
                    if (bg.scrimAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = bg.scrimAlpha.coerceIn(0f, 0.72f))),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseSurface),
                    )
                }
            }

            // 内容主体严格对齐 miuix_appcompat_pairing_dialog_content.xml：
            // 顶部 32dp 处居中显示标题与可选副标题，中间 pairingCustom (weight=1) 居中放置 200dp×200dp 动图区，
            // 底部 24dp 边距 + 50dp 高度主按钮。
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = titleTopMargin,
                            start = titleHorizontalPadding,
                            end = titleHorizontalPadding,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NexioText(
                        text = deviceTitle,
                        fontSize = (20f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!previewSubtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp * scale))
                        NexioText(
                            text = previewSubtitle,
                            fontSize = (14f * scale).sp,
                            color = subtitleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (stagePreviewBitmap != null) {
                        Image(
                            bitmap = stagePreviewBitmap,
                            contentDescription = null,
                            contentScale = if (findStage.media.scaleMode == PopupScaleMode.CENTER_CROP) {
                                ContentScale.Crop
                            } else {
                                ContentScale.Fit
                            },
                            modifier = Modifier.size(stageMediaSize),
                        )
                    } else {
                        HeadsetSilhouetteGraphic(
                            tint = titleColor.copy(alpha = if (hasCustomBg) 0.34f else 0.16f),
                            accent = titleColor.copy(alpha = if (hasCustomBg) 0.56f else 0.28f),
                            modifier = Modifier.size(width = 160.dp * scale, height = 84.dp * scale),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = buttonHorizontalMargin,
                            end = buttonHorizontalMargin,
                            bottom = buttonBottomMargin,
                        ),
                ) {
                    PreviewGlassButtonSurface(
                        backdrop = cardBackdrop,
                        styleWire = effectiveButtonStyle.wire,
                        spec = buttonSpec,
                        shape = ContinuousCapsule(),
                        circular = false,
                        fallbackFill = buttonSpec.buttonFallbackFill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                    ) {
                        NexioText(
                            text = primaryLabel,
                            fontSize = (16f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            color = buttonSpec.buttonTextColor,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 右上角关闭键叠加在卡片顶层（距顶/距右 20dp，尺寸 28dp），与原生 pairingClosable 一致。
            PreviewGlassButtonSurface(
                backdrop = cardBackdrop,
                styleWire = effectiveButtonStyle.wire,
                spec = buttonSpec,
                shape = CircleShape,
                circular = true,
                fallbackFill = buttonSpec.closeFallbackFill,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = closeMargin, end = closeMargin)
                    .size(closeSize),
            ) {
                Icon(
                    imageVector = NexioIcons.Close,
                    contentDescription = null,
                    tint = buttonSpec.closeIconColor,
                    modifier = Modifier.size(14.dp * scale),
                )
            }
        }
    }
}

@Composable
private fun PreviewGlassButtonSurface(
    backdrop: GlassBackdrop,
    styleWire: String,
    spec: ButtonGlassPreviewSpec,
    shape: Shape,
    circular: Boolean,
    fallbackFill: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val surfaceDrawable = remember(context, styleWire, spec.useRealGlass, circular, spec.isDarkGlass) {
        if (styleWire == PopupButtonStyle.NATIVE.wire || styleWire == PopupButtonStyle.INHERIT.wire) {
            null
        } else {
            val resolvedTone = if (spec.isDarkGlass) "light" else "dark"
            GlassButtonStyler.createSurfaceDrawable(
                context,
                styleWire,
                resolvedTone,
                spec.useRealGlass,
                circular,
            )
        }
    }

    if (surfaceDrawable == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(fallbackFill),
            contentAlignment = Alignment.Center,
            content = content,
        )
        return
    }

    if (!spec.useRealGlass) {
        Box(
            modifier = modifier
                .clip(shape)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        surfaceDrawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                        surfaceDrawable.draw(canvas.nativeCanvas)
                    }
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(spec.blurDp.toPx())
                },
                onDrawSurface = {
                    drawIntoCanvas { canvas ->
                        surfaceDrawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                        surfaceDrawable.draw(canvas.nativeCanvas)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun HeadsetSilhouetteGraphic(
    tint: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 左耳机
        drawCircle(color = accent, radius = h * 0.21f, center = Offset(w * 0.22f, h * 0.36f))
        drawLine(
            color = accent,
            start = Offset(w * 0.22f, h * 0.48f),
            end = Offset(w * 0.24f, h * 0.84f),
            strokeWidth = h * 0.12f,
            cap = StrokeCap.Round,
        )
        // 右耳机
        drawCircle(color = accent, radius = h * 0.21f, center = Offset(w * 0.42f, h * 0.36f))
        drawLine(
            color = accent,
            start = Offset(w * 0.42f, h * 0.48f),
            end = Offset(w * 0.40f, h * 0.84f),
            strokeWidth = h * 0.12f,
            cap = StrokeCap.Round,
        )
        // 充电盒
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.57f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.22f, h * 0.22f),
        )
        drawLine(
            color = accent,
            start = Offset(w * 0.59f, h * 0.42f),
            end = Offset(w * 0.89f, h * 0.42f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 横向方案切换胶囊条（全局默认 + 已添加型号 + 新增型号按钮），在首页 / 样式 / 阶段间统一复用。
 */
@Composable
fun ProfileChipStrip(
    profiles: List<PopupProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        profiles.forEach { item ->
            val selected = item.id == activeProfileId
            val bgColor = if (selected) {
                NexioTheme.colorScheme.primary
            } else {
                NexioTheme.colorScheme.surfaceContainer
            }
            val textColor = if (selected) {
                NexioTheme.colorScheme.onPrimary
            } else {
                NexioTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .clip(ContinuousCapsule())
                    .background(bgColor)
                    .clickable { onSelectProfile(item.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White.copy(alpha = if (item.enabled) 0.95f else 0.45f)
                            else if (item.enabled) SettingsAccents.Green
                            else SettingsAccents.Gray,
                        ),
                )
                NexioText(
                    text = if (item.isGlobal) "全局默认" else item.name,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(ContinuousCapsule())
                .background(NexioTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onAddProfile)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = NexioIcons.Add,
                contentDescription = "新增型号",
                tint = NexioTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            NexioText(
                text = "新增型号",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = NexioTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddModelProfileDialog(
    show: Boolean,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    onConfirm: (deviceId: String, displayName: String) -> Unit,
) {
    var deviceId by remember(show) { mutableStateOf("") }
    var deviceName by remember(show) { mutableStateOf("") }
    var errorText by remember(show) { mutableStateOf<String?>(null) }

    OverlayDialog(
        show = show,
        title = "新增按型号方案",
        // 使用 NexioUI OverlayDialog 自己的玻璃实现，不在弹窗外叠加材质层。
        liquidGlassBackdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TriggerDevicePreset.entries.forEach { preset ->
                    FlatTextButton(
                        text = preset.displayName,
                        onClick = {
                            deviceId = preset.deviceId
                            deviceName = preset.displayName
                            errorText = null
                        },
                        minHeight = 34.dp,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.textButtonColors(),
                    )
                }
            }

            TextField(
                value = deviceId,
                onValueChange = {
                    deviceId = it
                    errorText = null
                },
                label = "Device ID",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = "名称（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            errorText?.let { err ->
                NexioText(
                    text = err,
                    fontSize = 12.sp,
                    color = SettingsAccents.Red,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlatTextButton(
                    text = "取消",
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.weight(1f),
                )
                FlatTextButton(
                    text = "创建并切换",
                    onClick = {
                        val normalized = PopupProfile.normalizeDeviceId(deviceId)
                        if (normalized.isBlank()) {
                            errorText = "请输入有效的 Device ID"
                        } else {
                            onConfirm(normalized, deviceName.trim())
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal data class ButtonGlassPreviewSpec(
    val useRealGlass: Boolean,
    val isDarkGlass: Boolean,
    val blurDp: Dp,
    val liquidRefraction: Boolean,
    val liquidLens: LiquidLens,
    val glassFill: Color,
    val glassColorMix: Float,
    val lightIntensity: Float,
    val rimStroke: Color,
    val buttonFallbackFill: Color,
    val closeFallbackFill: Color,
    val buttonTextColor: Color,
    val closeIconColor: Color,
)

internal fun resolveButtonGlassSpec(
    style: PopupButtonStyle,
    autoDark: Boolean,
    preferLightText: Boolean,
    isDarkTheme: Boolean,
): ButtonGlassPreviewSpec {
    if (style == PopupButtonStyle.NATIVE || style == PopupButtonStyle.INHERIT) {
        return ButtonGlassPreviewSpec(
            useRealGlass = false,
            isDarkGlass = isDarkTheme,
            blurDp = 0.dp,
            liquidRefraction = false,
            liquidLens = LiquidLens(),
            glassFill = Color.Transparent,
            glassColorMix = 0f,
            lightIntensity = 0f,
            rimStroke = Color.Transparent,
            buttonFallbackFill = Color(0xFF3482FF),
            closeFallbackFill = if (isDarkTheme) Color(0x28FFFFFF) else Color(0x14000000),
            buttonTextColor = Color.White,
            closeIconColor = if (preferLightText) Color(0xDDFFFFFF) else Color(0x991A1D24),
        )
    }

    val realGlassSupported = isRenderEffectSupported()
    val darkGlass = when (style) {
        PopupButtonStyle.LIQUID_LIGHT, PopupButtonStyle.SOFT_LIGHT -> false
        PopupButtonStyle.LIQUID_DARK, PopupButtonStyle.SOFT_DARK,
        PopupButtonStyle.TINT_DARK, PopupButtonStyle.TINT_PURPLE,
        -> true
        PopupButtonStyle.LIQUID_AUTO, PopupButtonStyle.SOFT_AUTO, PopupButtonStyle.FROSTED_AUTO -> autoDark
        PopupButtonStyle.NATIVE, PopupButtonStyle.INHERIT -> isDarkTheme
    }

    val fgColor = when (style) {
        PopupButtonStyle.TINT_DARK -> Color(0xF5FFFFFF)
        PopupButtonStyle.TINT_PURPLE -> Color(0xFAFFFFFF)
        else -> if (darkGlass) Color(0xF5FFFFFF) else Color(0xEB14161A)
    }

    return when (style) {
        PopupButtonStyle.LIQUID_AUTO,
        PopupButtonStyle.LIQUID_LIGHT,
        PopupButtonStyle.LIQUID_DARK,
        -> ButtonGlassPreviewSpec(
            useRealGlass = realGlassSupported,
            isDarkGlass = darkGlass,
            blurDp = 10.dp,
            liquidRefraction = true,
            liquidLens = LiquidLens(
                refractionHeight = 14.dp,
                refractionAmount = 28.dp,
                depthEffect = true,
                chromaticAberration = 0.85f,
            ),
            glassFill = if (darkGlass) Color(0xFF1A202D) else Color(0xFFF2F2F2),
            glassColorMix = if (darkGlass) 0.14f else 0.24f,
            lightIntensity = if (darkGlass) 0.65f else 0.80f,
            rimStroke = if (darkGlass) Color(0x80FFFFFF) else Color(0x28000000),
            buttonFallbackFill = if (darkGlass) Color(0x5C121620) else Color(0x72EBEBEB),
            closeFallbackFill = if (darkGlass) Color(0x52121620) else Color(0x68EBEBEB),
            buttonTextColor = fgColor,
            closeIconColor = fgColor,
        )

        PopupButtonStyle.SOFT_AUTO,
        PopupButtonStyle.SOFT_LIGHT,
        PopupButtonStyle.SOFT_DARK,
        -> ButtonGlassPreviewSpec(
            useRealGlass = realGlassSupported,
            isDarkGlass = darkGlass,
            blurDp = 22.dp,
            liquidRefraction = false,
            liquidLens = LiquidLens(),
            glassFill = if (darkGlass) Color(0xFF202531) else Color(0xFFF0F0F0),
            glassColorMix = if (darkGlass) 0.34f else 0.42f,
            lightIntensity = if (darkGlass) 0.42f else 0.56f,
            rimStroke = if (darkGlass) Color(0x58FFFFFF) else Color(0x26000000),
            buttonFallbackFill = if (darkGlass) Color(0x7E181C26) else Color(0x8CEBEBEB),
            closeFallbackFill = if (darkGlass) Color(0x72181C26) else Color(0x82EBEBEB),
            buttonTextColor = fgColor,
            closeIconColor = fgColor,
        )

        PopupButtonStyle.FROSTED_AUTO -> ButtonGlassPreviewSpec(
            useRealGlass = realGlassSupported,
            isDarkGlass = darkGlass,
            blurDp = 38.dp,
            liquidRefraction = false,
            liquidLens = LiquidLens(),
            glassFill = if (darkGlass) Color(0xFF222732) else Color(0xFFEEEEEE),
            glassColorMix = if (darkGlass) 0.56f else 0.62f,
            lightIntensity = if (darkGlass) 0.24f else 0.34f,
            rimStroke = if (darkGlass) Color(0x46FFFFFF) else Color(0x24000000),
            buttonFallbackFill = if (darkGlass) Color(0x96202530) else Color(0xAEECECEC),
            closeFallbackFill = if (darkGlass) Color(0x8A202530) else Color(0xA4ECECEC),
            buttonTextColor = fgColor,
            closeIconColor = fgColor,
        )

        PopupButtonStyle.TINT_DARK -> ButtonGlassPreviewSpec(
            useRealGlass = realGlassSupported,
            isDarkGlass = true,
            blurDp = 24.dp,
            liquidRefraction = false,
            liquidLens = LiquidLens(),
            glassFill = Color(0xFF0E1117),
            glassColorMix = 0.62f,
            lightIntensity = 0.36f,
            rimStroke = Color(0x52FFFFFF),
            buttonFallbackFill = Color(0xA614171F),
            closeFallbackFill = Color(0x9614171F),
            buttonTextColor = fgColor,
            closeIconColor = fgColor,
        )

        PopupButtonStyle.TINT_PURPLE -> ButtonGlassPreviewSpec(
            useRealGlass = realGlassSupported,
            isDarkGlass = true,
            blurDp = 20.dp,
            liquidRefraction = true,
            liquidLens = LiquidLens(
                refractionHeight = 10.dp,
                refractionAmount = 18.dp,
                depthEffect = false,
                chromaticAberration = 0.35f,
            ),
            glassFill = Color(0xFF6B54D9),
            glassColorMix = 0.42f,
            lightIntensity = 0.52f,
            rimStroke = Color(0x99D9CEFF),
            buttonFallbackFill = Color(0x886A54D8),
            closeFallbackFill = Color(0x7A6A54D8),
            buttonTextColor = fgColor,
            closeIconColor = fgColor,
        )

        PopupButtonStyle.NATIVE, PopupButtonStyle.INHERIT -> error("Handled above")
    }
}

private fun decodeCroppedPreviewBitmap(
    context: Context,
    media: PopupMediaSpec,
    maxEdge: Int,
): Bitmap? {
    val file = PopupConfigStorage.mediaFile(context, media.assetName) ?: return null
    if (!file.isFile) return null
    val raw = when (media.type) {
        PopupMediaType.IMAGE -> runCatching {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                if (w > maxEdge || h > maxEdge) {
                    val scale = min(maxEdge.toFloat() / w, maxEdge.toFloat() / h)
                    decoder.setTargetSize(
                        (w * scale).roundToInt().coerceAtLeast(1),
                        (h * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull()
        PopupMediaType.VIDEO -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        PopupMediaType.NONE -> null
    } ?: return null

    val crop = media.crop.normalized()
    if (crop.isFullFrame) return raw
    val left = (crop.left * raw.width).roundToInt().coerceIn(0, (raw.width - 1).coerceAtLeast(0))
    val top = (crop.top * raw.height).roundToInt().coerceIn(0, (raw.height - 1).coerceAtLeast(0))
    val width = ((crop.right - crop.left) * raw.width).roundToInt().coerceIn(1, (raw.width - left).coerceAtLeast(1))
    val height = ((crop.bottom - crop.top) * raw.height).roundToInt().coerceIn(1, (raw.height - top).coerceAtLeast(1))
    return runCatching {
        val cropped = Bitmap.createBitmap(raw, left, top, width, height)
        if (cropped !== raw) raw.recycle()
        cropped
    }.getOrDefault(raw)
}
