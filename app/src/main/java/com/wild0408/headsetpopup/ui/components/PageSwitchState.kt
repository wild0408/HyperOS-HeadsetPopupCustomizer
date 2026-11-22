package com.wild0408.headsetpopup.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.haooz.nexioui.overscroll.OverscrollMath
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class PageSwitchState(initialPage: Int) {
    internal val position = Animatable(initialPage.toFloat())
    internal var edgeExtra by mutableFloatStateOf(0f)
    internal var dragRaw by mutableFloatStateOf(initialPage.toFloat())
    internal var dragging by mutableStateOf(false)
    internal var settleJob by mutableStateOf<Job?>(null)
    internal var commanded by mutableIntStateOf(initialPage)
    internal var scope: CoroutineScope? = null
    var pageCount: Int = 1

    private var settledBacking by mutableIntStateOf(initialPage)
    val settledPage: Int get() = settledBacking

    internal val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)
    internal val last: Float get() = lastPage.toFloat()

    internal val visual: Float
        get() = if (dragging) resistEdge(dragRaw, last) else position.value + edgeExtra

    private val pageIndexState = derivedStateOf {
        visual.roundToInt().coerceIn(0, lastPage)
    }
    private val animatingState = derivedStateOf {
        dragging || settleJob?.isActive == true || position.isRunning
    }

    val currentPage: Int get() = pageIndexState.value
    val isAnimating: Boolean get() = animatingState.value

    suspend fun animateTo(page: Int, animationSpec: AnimationSpec<Float> = SwitchSpecPages) {
        val target = page.coerceIn(0, lastPage)
        if (commanded == target && position.isRunning) return
        commanded = target
        settleJob?.cancel()
        settleJob = null
        if (edgeExtra != 0f) {
            position.snapTo(position.value + edgeExtra)
            edgeExtra = 0f
        }
        if (position.value != target.toFloat()) {
            position.animateTo(target.toFloat(), animationSpec)
        }
        if (!dragging) {
            settledBacking = position.value.roundToInt().coerceIn(0, lastPage)
        }
    }

    suspend fun snapTo(page: Int) {
        val target = page.coerceIn(0, lastPage)
        commanded = target
        settleJob?.cancel()
        settleJob = null
        edgeExtra = 0f
        dragging = false
        position.snapTo(target.toFloat())
        settledBacking = target
    }

    internal fun beginDrag() {
        settleJob?.cancel()
        settleJob = null
        scope?.launch { position.stop() }
        dragRaw = rawFromDisplay(position.value + edgeExtra, last)
        edgeExtra = 0f
        dragging = true
    }

    internal fun dragBy(deltaPages: Float) {
        dragRaw = (dragRaw + deltaPages).coerceIn(-PAGE_RANGE, last + PAGE_RANGE)
    }

    internal fun release(velocityPages: Float, pageWidthPx: Float): Int {
        if (!dragging) return currentPage
        val last = last
        val widthPx = pageWidthPx.coerceAtLeast(1f)
        val releasedPos = resistEdge(dragRaw, last)
        val thisScope = scope
        val edgePage = when {
            releasedPos < 0f -> 0
            releasedPos > last -> lastPage
            else -> -1
        }
        settleJob?.cancel()
        if (edgePage >= 0) {
            commanded = edgePage
            settleJob = thisScope?.launch {
                position.snapTo(edgePage.toFloat())
                edgeExtra = releasedPos - edgePage
                dragging = false
                OverscrollMath.settle(
                    from = edgeExtra * widthPx,
                    initialVelocity = OverscrollMath.settleVelocity(velocityPages * widthPx),
                ) { offsetPx -> edgeExtra = offsetPx / widthPx }
                edgeExtra = 0f
                settledBacking = edgePage
            }
            return edgePage
        }
        val anchor = dragRaw.roundToInt().coerceIn(0, lastPage)
        val projected = releasedPos + velocityPages * FLING_PROJECTION_SECONDS
        val target = projected.roundToInt().coerceIn(
            (anchor - 1).coerceAtLeast(0),
            (anchor + 1).coerceAtMost(lastPage),
        )
        commanded = target
        settleJob = thisScope?.launch {
            position.snapTo(releasedPos)
            dragging = false
            val limit = abs(target - releasedPos) * SETTLE_VELOCITY_PER_PAGE
            position.animateTo(
                targetValue = target.toFloat(),
                animationSpec = SnapSpecPages,
                initialVelocity = velocityPages.coerceIn(-limit, limit),
            )
            settledBacking = target
        }
        return target
    }
}

@Composable
fun rememberPageSwitchState(initialPage: Int = 0): PageSwitchState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PageSwitchState(initialPage).also { it.scope = scope } }
}

private fun resistEdge(raw: Float, lastPage: Float): Float {
    val over = when {
        raw < 0f -> raw
        raw > lastPage -> raw - lastPage
        else -> return raw
    }
    val damped = OverscrollMath.dampedDistance(over, PAGE_RANGE)
    return if (over < 0f) damped else lastPage + damped
}

private fun rawFromDisplay(shown: Float, lastPage: Float): Float = when {
    shown < 0f -> OverscrollMath.rawDistance(shown, PAGE_RANGE)
    shown > lastPage -> lastPage + OverscrollMath.rawDistance(shown - lastPage, PAGE_RANGE)
    else -> shown
}

private val SwitchSpecPages: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
    visibilityThreshold = 5e-5f,
)

private val SnapSpecPages: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 300f,
    visibilityThreshold = 5e-5f,
)

private const val PAGE_RANGE = 1f
private const val FLING_PROJECTION_SECONDS = 0.35f
private const val SETTLE_VELOCITY_PER_PAGE = 3.2f
