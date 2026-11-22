package com.wild0408.headsetpopup.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.haooz.nexioui.motion.OobeQuartOutSoftStartEasing
import com.haooz.nexioui.motion.SecondaryPageExitEasing
import com.haooz.nexioui.navigationback.LocalNexioPredictiveBackEnabled
import com.haooz.nexioui.navigationback.rememberNavigationBack
import com.haooz.nexioui.window.toShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    router: AppRouter,
    mainTransition: NavTransitionState,
    modifier: Modifier = Modifier,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val progress = rememberNavTransitionState(initialValue = 1f)
    val predictiveBackEnabled = LocalNexioPredictiveBackEnabled.current

    var outgoing by remember { mutableStateOf<ExitingLayer?>(null) }
    val back = rememberNavigationBack()

    fun destroyExiting(exiting: ExitingLayer) {
        outgoing = null
        progress.snapTo(1f)
        stateHolder.removeState(exiting.key)
    }

    suspend fun settleExit() {
        val exiting = outgoing ?: return
        val (dur, easing) = settleOnGlobalCurve(
            fromProgress = progress.value,
            toProgress = 0f,
            fullEasing = NAV_EXIT_EASING,
            totalDuration = NAV_EXIT_DURATION,
            invertGlobal = true,
            minDuration = MIN_GESTURE_SETTLE_MS,
        )
        val finished = coroutineScope {
            val exit = async { progress.animateTo(0f, tween(dur, easing = easing)) }
            if (router.depth == 0) launch { mainTransition.animateTo(0f, tween(dur, easing = easing)) }
            exit.await()
        }
        if (outgoing !== exiting) return
        if (finished) {
            destroyExiting(exiting)
        } else if (progress.value <= 0f && !back.isTransitioningBack()) {
            destroyExiting(exiting)
        }
    }

    var lastRoute by remember { mutableStateOf(router.current) }
    var renderedRoutes by remember { mutableStateOf(router.routes.toList()) }
    if (lastRoute !== router.current) {
        lastRoute = router.current
        val before = renderedRoutes
        renderedRoutes = router.routes.toList()
        when (router.lastDirection) {
            NavDirection.Push -> {
                val interrupted = outgoing
                outgoing = null
                interrupted?.let { stateHolder.removeState(it.key) }
                if (interrupted?.route?.id != router.current?.id) progress.snapTo(0f)
            }
            NavDirection.Pop -> {
                outgoing?.let { stateHolder.removeState(it.key) }
                val removedKeys = if (before.size > router.routes.size) {
                    layerKeys(before, null).first.drop(router.routes.size)
                } else {
                    emptyList()
                }
                removedKeys.dropLast(1).forEach { stateHolder.removeState(it) }
                outgoing = router.lastPopped?.let { popped ->
                    ExitingLayer(
                        popped,
                        removedKeys.lastOrNull() ?: poppedLayerKey(router.routes + popped),
                    )
                }
            }
        }
    }

    back.handler(
        enabled = router.hasOverlay,
        onBackCompleted = { router.popBack() },
        onBackCancelled = {
            if (outgoing != null) {
                scope.launch { settleExit() }
            } else {
                val (dur, easing) = enterSettle(progress.value, MIN_GESTURE_RESTORE_MS)
                scope.launch { playEnter(progress, mainTransition, router.depth, dur, easing) }
            }
        },
    )

    LaunchedEffect(predictiveBackEnabled) {
        var gestureStartProgress: Float? = null
        snapshotFlow { back.backProgress() }.collect { bp ->
            if (!predictiveBackEnabled) {
                gestureStartProgress = null
                return@collect
            }
            if (bp == null) {
                gestureStartProgress = null
            } else {
                val start = gestureStartProgress ?: progress.value.also { gestureStartProgress = it }
                val next = (start * (1f - bp)).coerceIn(0f, 1f)
                progress.snapTo(next)
                if (router.depth == 1 && outgoing == null) {
                    mainTransition.snapTo(next)
                }
            }
        }
    }

    LaunchedEffect(router.current, router.depth) {
        when (router.lastDirection) {
            NavDirection.Push -> {
                if (router.current != null) {
                    val (dur, easing) = enterSettle(progress.value, MIN_GESTURE_SETTLE_MS)
                    playEnter(progress, mainTransition, router.depth, dur, easing)
                }
            }
            NavDirection.Pop -> {
                if (outgoing != null) {
                    settleExit()
                } else if (router.depth == 0) {
                    mainTransition.snapTo(0f)
                }
            }
        }
    }

    if (router.routes.isEmpty() && outgoing == null) return

    NavLayerStack(
        routes = router.routes,
        exiting = outgoing,
        progress = progress,
        stateHolder = stateHolder,
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

private suspend fun playEnter(
    progress: NavTransitionState,
    mainTransition: NavTransitionState,
    depth: Int,
    durationMs: Int,
    easing: Easing,
) = coroutineScope {
    val enter = async { progress.animateTo(1f, tween(durationMs, easing = easing)) }
    if (depth == 1) launch { mainTransition.animateTo(1f, tween(durationMs, easing = easing)) }
    enter.await()
}

private fun enterSettle(from: Float, minDuration: Int): Pair<Int, Easing> =
    settleOnGlobalCurve(
        fromProgress = from,
        toProgress = 1f,
        fullEasing = NAV_ENTER_EASING,
        totalDuration = NAV_ENTER_DURATION,
        invertGlobal = false,
        minDuration = minDuration,
    )

@Composable
private fun NavLayerStack(
    routes: List<AppRoute>,
    exiting: ExitingLayer?,
    progress: NavTransitionState,
    stateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (layerKey: String, AppRoute) -> Unit,
) {
    val (stackKeys, exitKey) = layerKeys(routes, exiting)
    val layers: List<AppRoute> = if (exiting != null) routes + exiting.route else routes
    val renderKeys: List<String> = if (exitKey != null) stackKeys + exitKey else stackKeys

    val cornerRadiusPx = rememberScreenCornerRadiiPx()
    val density = LocalDensity.current
    val clipShape = remember(cornerRadiusPx, density) {
        if (cornerRadiusPx.maxRadius > 0f) cornerRadiusPx.toShape(density) else null
    }
    val inFlight: () -> Boolean = { progress.value > 0f && progress.value < 1f }
    val scrimAlpha: () -> Float = {
        if (progress.value < 0.999f) NAV_BG_DIM * progress.value else 0f
    }

    Box(modifier) {
        val topIndex = layers.lastIndex
        for (index in layers.indices) {
            val isTop = index == topIndex
            val isDirectUnder = index == topIndex - 1
            val fraction: () -> Float = when {
                isTop -> ({ 1f - progress.value })
                isDirectUnder -> ({ -NAV_PARALLAX * progress.value })
                else -> ({ -NAV_PARALLAX })
            }
            val paused: () -> Boolean = when {
                isTop -> ({ false })
                isDirectUnder -> ({ progress.value >= 1f })
                else -> ({ true })
            }
            PageLayer(
                fraction = fraction,
                paused = paused,
                clipShape = if (isTop) clipShape else null,
                scrimAlpha = if (isTop) scrimAlpha else null,
                inFlight = inFlight,
            ) {
                stateHolder.SaveableStateProvider(renderKeys[index]) {
                    content(renderKeys[index], layers[index])
                }
            }
        }
    }
}

internal class ExitingLayer(val route: AppRoute, val key: String)

internal fun layerKeys(routes: List<AppRoute>, exiting: ExitingLayer?): Pair<List<String>, String?> {
    val used = HashSet<String>(routes.size + 1)
    val stackKeys = routes.map { route ->
        var candidate = route.id
        var n = 2
        while (!used.add(candidate)) candidate = "${route.id}#${n++}"
        candidate
    }
    return stackKeys to exiting?.key
}

internal fun poppedLayerKey(stackBeforePop: List<AppRoute>): String =
    layerKeys(stackBeforePop, null).first.last()

@Composable
private fun PageLayer(
    fraction: () -> Float,
    paused: () -> Boolean,
    clipShape: Shape? = null,
    scrimAlpha: (() -> Float)? = null,
    inFlight: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .skipDrawWhen(paused)
            .then(
                if (scrimAlpha == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val a = scrimAlpha()
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    }
                },
            )
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place((fraction() * placeable.width).roundToInt(), 0)
                }
            }
            .clipDuringTransition(clipShape, inFlight),
    ) { content() }
}

internal fun settleOnGlobalCurve(
    fromProgress: Float,
    toProgress: Float,
    fullEasing: Easing,
    totalDuration: Int,
    invertGlobal: Boolean,
    minDuration: Int,
): Pair<Int, Easing> {
    val from = fromProgress.coerceIn(0f, 1f)
    val to = toProgress.coerceIn(0f, 1f)
    val span = to - from
    if (abs(span) < 1e-4f) {
        return minDuration.coerceAtMost(totalDuration) to fullEasing
    }
    val eAtFrom = if (invertGlobal) 1f - from else from
    val u0 = inverseEasingTime(fullEasing, eAtFrom)
    val durationMs = ((1f - u0) * totalDuration).toInt().coerceIn(minDuration, totalDuration)

    val local = Easing { fraction ->
        val u = u0 + (1f - u0) * fraction.coerceIn(0f, 1f)
        val e = fullEasing.transform(u)
        val global = if (invertGlobal) 1f - e else e
        ((global - from) / span).coerceIn(0f, 1f)
    }
    return durationMs to local
}

private fun inverseEasingTime(easing: Easing, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    repeat(18) {
        val mid = (lo + hi) * 0.5f
        if (easing.transform(mid) < p) lo = mid else hi = mid
    }
    return (lo + hi) * 0.5f
}

internal const val NAV_ENTER_DURATION = 600
internal const val NAV_EXIT_DURATION = 320
internal const val NAV_BG_DIM = 0.42f
internal val NAV_ENTER_EASING = OobeQuartOutSoftStartEasing
internal val NAV_EXIT_EASING = SecondaryPageExitEasing
private const val MIN_GESTURE_SETTLE_MS = 100
private const val MIN_GESTURE_RESTORE_MS = 200
