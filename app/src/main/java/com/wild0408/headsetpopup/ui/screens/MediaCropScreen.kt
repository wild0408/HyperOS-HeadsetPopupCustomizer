package com.wild0408.headsetpopup.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wild0408.headsetpopup.config.PopupConfigStorage
import com.wild0408.headsetpopup.config.PopupCropRect
import com.wild0408.headsetpopup.config.PopupMediaType
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.wild0408.headsetpopup.ui.navigation.AppRoute
import com.wild0408.headsetpopup.ui.navigation.AppRouter
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatIconButton
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Pause
import com.haooz.nexioui.icon.extended.Play
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.continuousClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface MediaCropSource {
    data class FromUri(val uri: Uri) : MediaCropSource
    data class FromAsset(
        val assetName: String,
        val displayName: String,
        val mediaType: PopupMediaType,
    ) : MediaCropSource
}

enum class CropAspectPreset(val label: String, val ratio: Float?) {
    FREE("自由", null),
    POPUP_CARD("弹窗卡片", 0.87f),
    SQUARE("1:1", 1.0f),
    BANNER_4_3("4:3", 4f / 3f),
    WIDE_16_9("16:9", 16f / 9f),
}

data class MediaCropRequest(
    val id: String,
    val source: MediaCropSource,
    val initialCrop: PopupCropRect,
    val defaultPreset: CropAspectPreset,
    val title: String,
    val onConfirm: (PopupCropRect) -> Unit,
)

@Stable
class MediaCropCoordinator(private val router: AppRouter) {
    private val requests = mutableStateMapOf<String, MediaCropRequest>()

    fun open(
        source: MediaCropSource,
        initialCrop: PopupCropRect,
        defaultPreset: CropAspectPreset,
        title: String,
        onConfirm: (PopupCropRect) -> Unit,
    ) {
        val id = UUID.randomUUID().toString()
        requests[id] = MediaCropRequest(
            id = id,
            source = source,
            initialCrop = initialCrop,
            defaultPreset = defaultPreset,
            title = title,
            onConfirm = onConfirm,
        )
        router.navigate(AppRoute.MediaCrop(id))
    }

    fun get(id: String): MediaCropRequest? = requests[id]

    fun finish(id: String, crop: PopupCropRect?) {
        val req = requests.remove(id)
        router.popBack()
        if (crop != null) {
            req?.onConfirm?.invoke(crop)
        }
    }
}

val LocalMediaCropCoordinator = staticCompositionLocalOf<MediaCropCoordinator> {
    error("LocalMediaCropCoordinator not provided")
}

@Composable
fun rememberMediaCropCoordinator(router: AppRouter): MediaCropCoordinator {
    return remember(router) { MediaCropCoordinator(router) }
}

private enum class CropDragHandle {
    NONE,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
}

@Composable
private fun ScrubbableVideoSlider(
    value: Float,
    durationMs: Long,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val activeTrackColor = NexioTheme.colorScheme.primary
    val inactiveTrackColor = NexioTheme.colorScheme.sliderBackground
    val thumbColor = NexioTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // 在 Initial pass 接管整条轨道，避免外层 LazyColumn 或 Slider 的点击处理
            // 把拖动拆成单次跳转。
            .pointerInput(durationMs, density) {
                val inset = with(density) { 4.dp.toPx() }
                val capRadius = with(density) { 13.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )

                    fun updateFromX(x: Float) {
                        val minX = inset + capRadius
                        val maxX = (size.width - inset - capRadius).coerceAtLeast(minX + 1f)
                        val fraction = ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
                        onValueChange(durationMs * fraction)
                    }

                    updateFromX(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        updateFromX(change.position.x)
                    }
                    onValueChangeFinished()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val insetPx = with(density) { 4.dp.toPx() }
            val trackHeight = with(density) { 26.dp.toPx() }
            val radius = trackHeight / 2f
            val centerY = size.height / 2f
            val startX = insetPx
            val endX = (size.width - insetPx).coerceAtLeast(startX + trackHeight)
            val minCenterX = startX + radius
            val maxCenterX = (endX - radius).coerceAtLeast(minCenterX)
            val fraction = (value / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            val thumbCenterX = minCenterX + (maxCenterX - minCenterX) * fraction
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(startX, centerY - radius),
                size = Size(endX - startX, trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = activeTrackColor,
                topLeft = Offset(startX, centerY - radius),
                size = Size((thumbCenterX - startX + radius).coerceIn(trackHeight, endX - startX), trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawCircle(
                color = thumbColor,
                radius = with(density) { 9.dp.toPx() },
                center = Offset(thumbCenterX, centerY),
            )
        }
    }
}

private data class LoadedMediaMeta(
    val isVideo: Boolean,
    val title: String,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val imageBitmap: Bitmap? = null,
    val videoUri: Uri? = null,
    val videoFile: File? = null,
    val error: String? = null,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}

/**
 * Hardware-accelerated persistent MediaPlayer scrubber for 60fps TextureView preview.
 * Coalesces rapid drag updates into a single in-flight hardware seek so dragging never stutters.
 */
private class SmoothVideoScrubController(
    private val context: Context,
    private val uri: Uri?,
    private val file: File?,
    private val onPreparedMeta: (durationMs: Long, width: Int, height: Int) -> Unit,
    private val onError: (String) -> Unit,
) : TextureView.SurfaceTextureListener {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var released = false
    private var playing = false

    private var seekInFlight = false
    private var seekStartUptimeMs = 0L
    private var lastSeekDurationMs = 20L
    private var lastDispatchedSeekMs = -1L
    private var pendingSeekMs = -1L
    private var pendingSeekExact = false

    fun attachTo(textureView: TextureView) {
        textureView.surfaceTextureListener = this
        if (textureView.isAvailable && textureView.surfaceTexture != null) {
            onSurfaceTextureAvailable(textureView.surfaceTexture!!, textureView.width, textureView.height)
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        releasePlayerOnly()
        val s = Surface(surfaceTexture)
        surface = s
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setSurface(s)
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            when {
                uri != null -> mp.setDataSource(context, uri)
                file != null -> mp.setDataSource(file.absolutePath)
                else -> {
                    onError("找不到视频文件")
                    return
                }
            }
            mp.setOnPreparedListener { player ->
                if (released || player !== mediaPlayer) return@setOnPreparedListener
                prepared = true
                val dur = player.duration.toLong().coerceAtLeast(0L)
                onPreparedMeta(dur, player.videoWidth, player.videoHeight)
                if (pendingSeekMs >= 0L) {
                    dispatchSeek(pendingSeekMs, pendingSeekExact)
                } else {
                    dispatchSeek(0L, true)
                }
            }
            mp.setOnSeekCompleteListener { player ->
                if (released || player !== mediaPlayer) return@setOnSeekCompleteListener
                val elapsed = (SystemClock.uptimeMillis() - seekStartUptimeMs).coerceAtLeast(1L)
                lastSeekDurationMs = (lastSeekDurationMs * 2L + elapsed) / 3L
                seekInFlight = false
                val queued = pendingSeekMs
                val queuedExact = pendingSeekExact
                if (queued >= 0L && (queued != lastDispatchedSeekMs || queuedExact)) {
                    pendingSeekMs = -1L
                    pendingSeekExact = false
                    dispatchSeek(queued, queuedExact)
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                onError("视频解码器错误 ($what/$extra)")
                true
            }
            mp.prepareAsync()
        } catch (err: Throwable) {
            onError(err.message ?: "无法加载视频预览")
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayerOnly()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun scrubTo(positionMs: Long, exact: Boolean) {
        val target = positionMs.coerceAtLeast(0L)
        val mp = mediaPlayer
        if (playing && mp != null && prepared) {
            runCatching { mp.pause() }
            playing = false
        }
        if (!prepared || mp == null) {
            pendingSeekMs = target
            pendingSeekExact = exact
            return
        }
        if (seekInFlight) {
            pendingSeekMs = target
            pendingSeekExact = pendingSeekExact || exact
            return
        }
        dispatchSeek(target, exact)
    }

    private fun dispatchSeek(targetMs: Long, exact: Boolean) {
        val mp = mediaPlayer ?: return
        if (!prepared || released) return
        seekInFlight = true
        seekStartUptimeMs = SystemClock.uptimeMillis()
        lastDispatchedSeekMs = targetMs
        val mode = if (exact || lastSeekDurationMs <= 55L) {
            MediaPlayer.SEEK_CLOSEST
        } else {
            MediaPlayer.SEEK_CLOSEST_SYNC
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(targetMs, mode)
            } else {
                mp.seekTo(targetMs.toInt())
            }
        }.onFailure {
            seekInFlight = false
        }
    }

    fun setPlaying(shouldPlay: Boolean): Boolean {
        val mp = mediaPlayer ?: return false
        if (!prepared || released) return false
        return runCatching {
            if (shouldPlay) {
                pendingSeekMs = -1L
                mp.start()
                playing = true
            } else {
                mp.pause()
                playing = false
            }
            playing
        }.getOrDefault(false)
    }

    fun currentPositionMs(): Long {
        val mp = mediaPlayer ?: return 0L
        if (!prepared || released) return 0L
        return runCatching { mp.currentPosition.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun releasePlayerOnly() {
        prepared = false
        playing = false
        seekInFlight = false
        mediaPlayer?.let { mp ->
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
        surface?.let { runCatching { it.release() } }
        surface = null
    }

    fun release() {
        released = true
        releasePlayerOnly()
    }
}

@Composable
fun MediaCropScreen(
    requestId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coordinator = LocalMediaCropCoordinator.current
    val request = coordinator.get(requestId)

    if (request == null) {
        LaunchedEffect(requestId) { onBack() }
        return
    }

    var meta by remember(requestId) { mutableStateOf<LoadedMediaMeta?>(null) }
    var crop by remember(requestId) { mutableStateOf(request.initialCrop.normalized()) }
    var selectedPreset by remember(requestId) {
        mutableStateOf(if (request.initialCrop.isFullFrame) request.defaultPreset else CropAspectPreset.FREE)
    }
    var didApplyInitialPreset by remember(requestId) {
        mutableStateOf(!request.initialCrop.isFullFrame)
    }

    var videoDurationMs by remember(requestId) { mutableLongStateOf(0L) }
    var videoPositionMs by remember(requestId) { mutableLongStateOf(0L) }
    var isVideoPlaying by remember(requestId) { mutableStateOf(false) }
    var videoError by remember(requestId) { mutableStateOf<String?>(null) }

    LaunchedEffect(requestId) {
        val loaded = withContext(Dispatchers.IO) {
            loadMediaMetadata(context, request.source)
        }
        meta = loaded
        videoDurationMs = loaded.durationMs
        if (!didApplyInitialPreset && loaded.width > 0 && loaded.height > 0) {
            didApplyInitialPreset = true
            selectedPreset.ratio?.let { targetRatio ->
                crop = fitCenteredAspectCrop(loaded.aspectRatio, targetRatio)
            }
        }
    }

    DisposableEffect(requestId) {
        onDispose {
            val bmp = meta?.imageBitmap
            if (bmp != null && !bmp.isRecycled) {
                runCatching { bmp.recycle() }
            }
        }
    }

    val videoController = remember(requestId, meta?.isVideo, meta?.videoUri, meta?.videoFile) {
        val m = meta
        if (m != null && m.isVideo && m.error == null) {
            SmoothVideoScrubController(
                context = context,
                uri = m.videoUri,
                file = m.videoFile,
                onPreparedMeta = { dur, w, h ->
                    if (dur > 0L) videoDurationMs = dur
                    if (w > 0 && h > 0 && (m.width <= 0 || m.height <= 0)) {
                        meta = m.copy(width = w, height = h, durationMs = max(dur, m.durationMs))
                        if (!didApplyInitialPreset) {
                            didApplyInitialPreset = true
                            selectedPreset.ratio?.let { targetRatio ->
                                crop = fitCenteredAspectCrop(w.toFloat() / h.toFloat(), targetRatio)
                            }
                        }
                    }
                },
                onError = { err -> videoError = err },
            )
        } else {
            null
        }
    }

    DisposableEffect(videoController) {
        onDispose {
            videoController?.release()
        }
    }

    LaunchedEffect(videoController, isVideoPlaying) {
        if (videoController == null || !isVideoPlaying) return@LaunchedEffect
        while (isActive && isVideoPlaying) {
            val pos = videoController.currentPositionMs()
            if (videoDurationMs > 0L) {
                videoPositionMs = pos.coerceIn(0L, videoDurationMs)
            }
            delay(33L)
        }
    }

    val primaryColor = NexioTheme.colorScheme.primary

    DocumentPageScaffold(
        title = request.title,
        onBack = { coordinator.finish(requestId, null) },
        collapsedOnly = true,
    ) { paddingValues, _, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status strip matching NexioUI subtitle typography
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexioText(
                    text = meta?.title?.takeIf { it.isNotBlank() } ?: "正在读取素材...",
                    fontSize = 13.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(ContinuousCapsule())
                        .background(NexioTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    NexioText(
                        text = crop.summaryLabel(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NexioTheme.colorScheme.primary,
                    )
                }
            }

            // Interactive Crop Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                insideMargin = PaddingValues(10.dp),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .continuousClip(14.dp)
                        .background(NexioTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val loaded = meta
                    val errText = videoError ?: loaded?.error
                    when {
                        loaded == null -> {
                            NexioText(
                                text = "正在加载预览画面...",
                                fontSize = 14.sp,
                                color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        errText != null -> {
                            NexioText(
                                text = errText,
                                fontSize = 14.sp,
                                color = SettingsAccents.Red,
                            )
                        }
                        !loaded.isVideo && (loaded.imageBitmap == null || loaded.imageBitmap.isRecycled) -> {
                            NexioText(
                                text = "无法读取图片画面，仍可直接按全图应用",
                                fontSize = 14.sp,
                                color = SettingsAccents.Red,
                            )
                        }
                        else -> {
                            val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                            val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                            val padPx = with(density) { 14.dp.toPx() }
                            val availW = (containerW - padPx * 2f).coerceAtLeast(1f)
                            val availH = (containerH - padPx * 2f).coerceAtLeast(1f)
                            val mediaAspect = loaded.aspectRatio.coerceAtLeast(0.05f)
                            val availAspect = availW / availH
                            val drawW: Float
                            val drawH: Float
                            if (mediaAspect > availAspect) {
                                drawW = availW
                                drawH = availW / mediaAspect
                            } else {
                                drawH = availH
                                drawW = availH * mediaAspect
                            }
                            val imgRect = Rect(
                                left = (containerW - drawW) * 0.5f,
                                top = (containerH - drawH) * 0.5f,
                                right = (containerW + drawW) * 0.5f,
                                bottom = (containerH + drawH) * 0.5f,
                            )

                            val drawWDp = with(density) { drawW.toDp() }
                            val drawHDp = with(density) { drawH.toDp() }
                            val touchSlopPx = with(density) { 30.dp.toPx() }
                            var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(imgRect, selectedPreset, mediaAspect) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                activeHandle = hitTestHandle(offset, imgRect, crop, touchSlopPx)
                                            },
                                            onDragEnd = { activeHandle = CropDragHandle.NONE },
                                            onDragCancel = { activeHandle = CropDragHandle.NONE },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (activeHandle == CropDragHandle.NONE) return@detectDragGestures
                                                crop = applyCropDrag(
                                                    current = crop,
                                                    handle = activeHandle,
                                                    dxNorm = dragAmount.x / imgRect.width.coerceAtLeast(1f),
                                                    dyNorm = dragAmount.y / imgRect.height.coerceAtLeast(1f),
                                                    imgAspect = mediaAspect,
                                                    lockedRatio = selectedPreset.ratio,
                                                )
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loaded.isVideo && videoController != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            TextureView(ctx).apply {
                                                isOpaque = false
                                                videoController.attachTo(this)
                                            }
                                        },
                                        modifier = Modifier.size(drawWDp, drawHDp),
                                    )
                                } else if (loaded.imageBitmap != null && !loaded.imageBitmap.isRecycled) {
                                    val composeBmp = remember(loaded.imageBitmap) {
                                        loaded.imageBitmap.asImageBitmap()
                                    }
                                    Image(
                                        bitmap = composeBmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.size(drawWDp, drawHDp),
                                    )
                                }

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cl = imgRect.left + crop.left * imgRect.width
                                    val ct = imgRect.top + crop.top * imgRect.height
                                    val cr = imgRect.left + crop.right * imgRect.width
                                    val cb = imgRect.top + crop.bottom * imgRect.height
                                    val cw = (cr - cl).coerceAtLeast(1f)
                                    val ch = (cb - ct).coerceAtLeast(1f)

                                    val scrim = Color(0x96000000)
                                    if (ct > imgRect.top) {
                                        drawRect(
                                            color = scrim,
                                            topLeft = Offset(imgRect.left, imgRect.top),
                                            size = Size(imgRect.width, ct - imgRect.top),
                                        )
                                    }
                                    if (cb < imgRect.bottom) {
                                        drawRect(
                                            color = scrim,
                                            topLeft = Offset(imgRect.left, cb),
                                            size = Size(imgRect.width, imgRect.bottom - cb),
                                        )
                                    }
                                    if (cl > imgRect.left) {
                                        drawRect(
                                            color = scrim,
                                            topLeft = Offset(imgRect.left, ct),
                                            size = Size(cl - imgRect.left, ch),
                                        )
                                    }
                                    if (cr < imgRect.right) {
                                        drawRect(
                                            color = scrim,
                                            topLeft = Offset(cr, ct),
                                            size = Size(imgRect.right - cr, ch),
                                        )
                                    }

                                    // Rule-of-thirds grid
                                    val gridColor = Color(0x66FFFFFF)
                                    val gridStroke = 1.dp.toPx()
                                    for (i in 1..2) {
                                        val gx = cl + cw * i / 3f
                                        val gy = ct + ch * i / 3f
                                        drawLine(gridColor, Offset(gx, ct), Offset(gx, cb), strokeWidth = gridStroke)
                                        drawLine(gridColor, Offset(cl, gy), Offset(cr, gy), strokeWidth = gridStroke)
                                    }

                                    // Crisp white border
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(cl, ct),
                                        size = Size(cw, ch),
                                        style = Stroke(width = 1.6.dp.toPx()),
                                    )

                                    // Corner handles accented with NexioTheme primary
                                    val cornerLen = min(22.dp.toPx(), min(cw, ch) * 0.34f)
                                    val cornerStroke = 3.6.dp.toPx()
                                    fun drawCorner(x: Float, y: Float, dx: Float, dy: Float) {
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(x, y),
                                            end = Offset(x + dx * cornerLen, y),
                                            strokeWidth = cornerStroke + 1.5f,
                                            cap = StrokeCap.Round,
                                        )
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(x, y),
                                            end = Offset(x, y + dy * cornerLen),
                                            strokeWidth = cornerStroke + 1.5f,
                                            cap = StrokeCap.Round,
                                        )
                                        drawLine(
                                            color = primaryColor,
                                            start = Offset(x, y),
                                            end = Offset(x + dx * cornerLen, y),
                                            strokeWidth = cornerStroke * 0.72f,
                                            cap = StrokeCap.Round,
                                        )
                                        drawLine(
                                            color = primaryColor,
                                            start = Offset(x, y),
                                            end = Offset(x, y + dy * cornerLen),
                                            strokeWidth = cornerStroke * 0.72f,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                    drawCorner(cl, ct, 1f, 1f)
                                    drawCorner(cr, ct, -1f, 1f)
                                    drawCorner(cl, cb, 1f, -1f)
                                    drawCorner(cr, cb, -1f, -1f)
                                }
                            }
                        }
                    }
                }
            }

            // Silky-smooth hardware video scrubber Card
            if (meta?.isVideo == true && videoDurationMs > 0L && videoController != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FlatIconButton(
                                    onClick = {
                                        isVideoPlaying = videoController.setPlaying(!isVideoPlaying)
                                    },
                                    backgroundColor = NexioTheme.colorScheme.secondaryContainer,
                                    cornerRadius = 17.dp,
                                    minWidth = 34.dp,
                                    minHeight = 34.dp,
                                ) {
                                    androidx.compose.foundation.Image(
                                        imageVector = if (isVideoPlaying) NexioIcons.Pause else NexioIcons.Play,
                                        contentDescription = if (isVideoPlaying) "暂停预览" else "播放预览",
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                            NexioTheme.colorScheme.onSecondaryVariant,
                                        ),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                NexioText(
                                    text = "预览视频帧",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NexioTheme.colorScheme.onSurface,
                                )
                            }
                            NexioText(
                                text = "${formatTimeMsFine(videoPositionMs)} / ${formatTimeMsFine(videoDurationMs)}",
                                fontSize = 12.sp,
                                color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        ScrubbableVideoSlider(
                            value = videoPositionMs.toFloat().coerceIn(0f, videoDurationMs.toFloat()),
                            durationMs = videoDurationMs,
                            onValueChange = { next ->
                                val clamped = next.toLong().coerceIn(0L, videoDurationMs)
                                isVideoPlaying = false
                                videoPositionMs = clamped
                                videoController.scrubTo(clamped, exact = false)
                            },
                            onValueChangeFinished = {
                                videoController.scrubTo(videoPositionMs, exact = true)
                            },
                        )
                    }
                }
            }

            // Aspect Ratio & Bottom Actions Card (strictly NexioUI theme & components)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CropAspectPreset.entries.forEach { preset ->
                            val active = selectedPreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(ContinuousCapsule())
                                    .background(
                                        if (active) {
                                            NexioTheme.colorScheme.primary
                                        } else {
                                            NexioTheme.colorScheme.secondaryContainer
                                        },
                                    )
                                    .clickable {
                                        selectedPreset = preset
                                        val aspect = meta?.aspectRatio ?: 1f
                                        preset.ratio?.let { targetRatio ->
                                            crop = fitCenteredAspectCrop(aspect, targetRatio)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                NexioText(
                                    text = preset.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) {
                                        NexioTheme.colorScheme.onPrimary
                                    } else {
                                        NexioTheme.colorScheme.onSecondaryVariant
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlatTextButton(
                            text = "重置全图",
                            onClick = {
                                selectedPreset = CropAspectPreset.FREE
                                crop = PopupCropRect.FULL
                            },
                            enabled = !crop.isFullFrame,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(),
                        )
                        FlatTextButton(
                            text = "应用裁剪",
                            onClick = { coordinator.finish(requestId, crop.normalized()) },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}

private fun fitCenteredAspectCrop(imgAspect: Float, targetRatio: Float): PopupCropRect {
    if (imgAspect <= 0f || targetRatio <= 0f) return PopupCropRect.FULL
    val normW: Float
    val normH: Float
    if (imgAspect > targetRatio) {
        normH = 1f
        normW = (targetRatio / imgAspect).coerceIn(0.1f, 1f)
    } else {
        normW = 1f
        normH = (imgAspect / targetRatio).coerceIn(0.1f, 1f)
    }
    val l = (1f - normW) * 0.5f
    val t = (1f - normH) * 0.5f
    return PopupCropRect(l, t, l + normW, t + normH).normalized()
}

private fun hitTestHandle(
    touch: Offset,
    imgRect: Rect,
    crop: PopupCropRect,
    slopPx: Float,
): CropDragHandle {
    val cl = imgRect.left + crop.left * imgRect.width
    val ct = imgRect.top + crop.top * imgRect.height
    val cr = imgRect.left + crop.right * imgRect.width
    val cb = imgRect.top + crop.bottom * imgRect.height

    fun dist(x: Float, y: Float) = hypot(touch.x - x, touch.y - y)

    val corners = listOf(
        CropDragHandle.TOP_LEFT to dist(cl, ct),
        CropDragHandle.TOP_RIGHT to dist(cr, ct),
        CropDragHandle.BOTTOM_LEFT to dist(cl, cb),
        CropDragHandle.BOTTOM_RIGHT to dist(cr, cb),
    )
    val nearestCorner = corners.minByOrNull { it.second }
    if (nearestCorner != null && nearestCorner.second <= slopPx * 1.25f) {
        return nearestCorner.first
    }

    val withinY = touch.y in (ct - slopPx)..(cb + slopPx)
    val withinX = touch.x in (cl - slopPx)..(cr + slopPx)
    if (withinY && abs(touch.x - cl) <= slopPx) return CropDragHandle.LEFT
    if (withinY && abs(touch.x - cr) <= slopPx) return CropDragHandle.RIGHT
    if (withinX && abs(touch.y - ct) <= slopPx) return CropDragHandle.TOP
    if (withinX && abs(touch.y - cb) <= slopPx) return CropDragHandle.BOTTOM

    if (touch.x in cl..cr && touch.y in ct..cb) {
        return CropDragHandle.MOVE
    }
    return CropDragHandle.NONE
}

private fun applyCropDrag(
    current: PopupCropRect,
    handle: CropDragHandle,
    dxNorm: Float,
    dyNorm: Float,
    imgAspect: Float,
    lockedRatio: Float?,
): PopupCropRect {
    val minSpan = 0.10f
    var l = current.left
    var t = current.top
    var r = current.right
    var b = current.bottom

    if (handle == CropDragHandle.MOVE) {
        val w = r - l
        val h = b - t
        val nl = (l + dxNorm).coerceIn(0f, 1f - w)
        val nt = (t + dyNorm).coerceIn(0f, 1f - h)
        return PopupCropRect(nl, nt, nl + w, nt + h)
    }

    if (lockedRatio == null || lockedRatio <= 0f) {
        when (handle) {
            CropDragHandle.LEFT -> l = (l + dxNorm).coerceIn(0f, r - minSpan)
            CropDragHandle.RIGHT -> r = (r + dxNorm).coerceIn(l + minSpan, 1f)
            CropDragHandle.TOP -> t = (t + dyNorm).coerceIn(0f, b - minSpan)
            CropDragHandle.BOTTOM -> b = (b + dyNorm).coerceIn(t + minSpan, 1f)
            CropDragHandle.TOP_LEFT -> {
                l = (l + dxNorm).coerceIn(0f, r - minSpan)
                t = (t + dyNorm).coerceIn(0f, b - minSpan)
            }
            CropDragHandle.TOP_RIGHT -> {
                r = (r + dxNorm).coerceIn(l + minSpan, 1f)
                t = (t + dyNorm).coerceIn(0f, b - minSpan)
            }
            CropDragHandle.BOTTOM_LEFT -> {
                l = (l + dxNorm).coerceIn(0f, r - minSpan)
                b = (b + dyNorm).coerceIn(t + minSpan, 1f)
            }
            CropDragHandle.BOTTOM_RIGHT -> {
                r = (r + dxNorm).coerceIn(l + minSpan, 1f)
                b = (b + dyNorm).coerceIn(t + minSpan, 1f)
            }
            else -> {}
        }
        return PopupCropRect(l, t, r, b).normalized()
    }

    val normRatio = lockedRatio / imgAspect.coerceAtLeast(0.05f)
    val minW = max(minSpan, minSpan * normRatio)

    fun clampWidth(desiredW: Float, maxW: Float, maxH: Float): Pair<Float, Float> {
        val maxAllowedW = min(maxW, maxH * normRatio)
        val w = desiredW.coerceIn(min(minW, maxAllowedW), maxAllowedW)
        val h = (w / normRatio).coerceIn(minSpan, maxH)
        return w to h
    }

    when (handle) {
        CropDragHandle.TOP_LEFT -> {
            val desiredW = (r - (l + dxNorm))
            val (w, h) = clampWidth(desiredW, r, b)
            l = r - w
            t = b - h
        }
        CropDragHandle.TOP_RIGHT -> {
            val desiredW = ((r + dxNorm) - l)
            val (w, h) = clampWidth(desiredW, 1f - l, b)
            r = l + w
            t = b - h
        }
        CropDragHandle.BOTTOM_LEFT -> {
            val desiredW = (r - (l + dxNorm))
            val (w, h) = clampWidth(desiredW, r, 1f - t)
            l = r - w
            b = t + h
        }
        CropDragHandle.BOTTOM_RIGHT -> {
            val desiredW = ((r + dxNorm) - l)
            val (w, h) = clampWidth(desiredW, 1f - l, 1f - t)
            r = l + w
            b = t + h
        }
        CropDragHandle.LEFT -> {
            val cy = (t + b) * 0.5f
            val desiredW = r - (l + dxNorm)
            val maxH = min(cy, 1f - cy) * 2f
            val (w, h) = clampWidth(desiredW, r, maxH)
            l = r - w
            t = cy - h * 0.5f
            b = cy + h * 0.5f
        }
        CropDragHandle.RIGHT -> {
            val cy = (t + b) * 0.5f
            val desiredW = (r + dxNorm) - l
            val maxH = min(cy, 1f - cy) * 2f
            val (w, h) = clampWidth(desiredW, 1f - l, maxH)
            r = l + w
            t = cy - h * 0.5f
            b = cy + h * 0.5f
        }
        CropDragHandle.TOP -> {
            val cx = (l + r) * 0.5f
            val desiredH = b - (t + dyNorm)
            val maxW = min(cx, 1f - cx) * 2f
            val (w, h) = clampWidth(desiredH * normRatio, maxW, b)
            t = b - h
            l = cx - w * 0.5f
            r = cx + w * 0.5f
        }
        CropDragHandle.BOTTOM -> {
            val cx = (l + r) * 0.5f
            val desiredH = (b + dyNorm) - t
            val maxW = min(cx, 1f - cx) * 2f
            val (w, h) = clampWidth(desiredH * normRatio, maxW, 1f - t)
            b = t + h
            l = cx - w * 0.5f
            r = cx + w * 0.5f
        }
        else -> {}
    }
    return PopupCropRect(l, t, r, b).normalized()
}

private fun loadMediaMetadata(context: Context, source: MediaCropSource): LoadedMediaMeta {
    return when (source) {
        is MediaCropSource.FromUri -> {
            val mime = runCatching { context.contentResolver.getType(source.uri) }.getOrNull().orEmpty()
            val name = queryDisplayName(context, source.uri).ifBlank { "所选素材" }
            val isVideo = mime.startsWith("video/", ignoreCase = true) ||
                name.endsWith(".mp4", ignoreCase = true) ||
                name.endsWith(".webm", ignoreCase = true) ||
                name.endsWith(".mkv", ignoreCase = true)
            if (isVideo) {
                probeVideoFromUri(context, source.uri, name)
            } else {
                loadImageFromUri(context, source.uri, name)
            }
        }
        is MediaCropSource.FromAsset -> {
            val file = PopupConfigStorage.mediaFile(context, source.assetName)
            if (file == null || !file.exists()) {
                LoadedMediaMeta(
                    isVideo = source.mediaType == PopupMediaType.VIDEO,
                    title = source.displayName,
                    width = 0,
                    height = 0,
                    error = "找不到本地媒体文件",
                )
            } else if (source.mediaType == PopupMediaType.VIDEO) {
                probeVideoFromFile(file, source.displayName)
            } else {
                loadImageFromFile(file, source.displayName)
            }
        }
    }
}

private fun probeVideoFromUri(context: Context, uri: Uri, name: String): LoadedMediaMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val (w, h, dur) = extractVideoDimensions(retriever)
        LoadedMediaMeta(
            isVideo = true,
            title = name,
            width = w,
            height = h,
            durationMs = dur,
            videoUri = uri,
        )
    } catch (err: Throwable) {
        LoadedMediaMeta(
            isVideo = true,
            title = name,
            width = 0,
            height = 0,
            videoUri = uri,
            error = err.message ?: "无法读取视频元数据",
        )
    } finally {
        runCatching { retriever.release() }
    }
}

private fun probeVideoFromFile(file: File, name: String): LoadedMediaMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val (w, h, dur) = extractVideoDimensions(retriever)
        LoadedMediaMeta(
            isVideo = true,
            title = name,
            width = w,
            height = h,
            durationMs = dur,
            videoFile = file,
        )
    } catch (err: Throwable) {
        LoadedMediaMeta(
            isVideo = true,
            title = name,
            width = 0,
            height = 0,
            videoFile = file,
            error = err.message ?: "无法读取视频元数据",
        )
    } finally {
        runCatching { retriever.release() }
    }
}

private fun extractVideoDimensions(retriever: MediaMetadataRetriever): Triple<Int, Int, Long> {
    val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    return if (rot == 90 || rot == 270) {
        Triple(rawH, rawW, dur)
    } else {
        Triple(rawW, rawH, dur)
    }
}

private fun loadImageFromUri(context: Context, uri: Uri, name: String): LoadedMediaMeta {
    return runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = decodeScaledImage(source)
        LoadedMediaMeta(
            isVideo = false,
            title = name,
            width = bmp.width,
            height = bmp.height,
            imageBitmap = bmp,
        )
    }.getOrElse { err ->
        LoadedMediaMeta(
            isVideo = false,
            title = name,
            width = 0,
            height = 0,
            error = err.message ?: "读取图片失败",
        )
    }
}

private fun loadImageFromFile(file: File, name: String): LoadedMediaMeta {
    return runCatching {
        val source = ImageDecoder.createSource(file)
        val bmp = decodeScaledImage(source)
        LoadedMediaMeta(
            isVideo = false,
            title = name,
            width = bmp.width,
            height = bmp.height,
            imageBitmap = bmp,
        )
    }.getOrElse { err ->
        LoadedMediaMeta(
            isVideo = false,
            title = name,
            width = 0,
            height = 0,
            error = err.message ?: "读取图片失败",
        )
    }
}

private fun decodeScaledImage(source: ImageDecoder.Source): Bitmap {
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width.coerceAtLeast(1)
        val h = info.size.height.coerceAtLeast(1)
        val maxEdge = 1600
        if (w > maxEdge || h > maxEdge) {
            val scale = min(maxEdge.toFloat() / w, maxEdge.toFloat() / h)
            decoder.setTargetSize(
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}

private fun formatTimeMsFine(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSec = clamped / 1000L
    val tenths = (clamped % 1000L) / 100L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
}
