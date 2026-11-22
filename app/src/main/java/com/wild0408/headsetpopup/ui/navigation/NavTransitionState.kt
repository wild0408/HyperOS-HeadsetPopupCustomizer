package com.wild0408.headsetpopup.ui.navigation

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.haooz.nexioui.window.ScreenCornerRadiiPx
import kotlin.math.roundToInt

@Stable
class NavTransitionState(initialValue: Float = 0f) {
    private val raw = mutableFloatStateOf(initialValue)
    val value: Float get() = raw.floatValue
    private var mutation = 0

    suspend fun animateTo(target: Float, spec: AnimationSpec<Float>): Boolean {
        val token = ++mutation
        animate(
            initialValue = raw.floatValue,
            targetValue = target,
            initialVelocity = 0f,
            animationSpec = spec,
        ) { value, _ -> if (token == mutation) raw.floatValue = value }
        return token == mutation
    }

    fun snapTo(target: Float) {
        mutation++
        raw.floatValue = target.coerceIn(0f, 1f)
    }
}

const val NAV_PARALLAX = 0.24f

@Composable
fun rememberNavTransitionState(initialValue: Float = 0f): NavTransitionState =
    remember { NavTransitionState(initialValue) }

@Composable
fun MainLayerTransition(
    state: NavTransitionState,
    modifier: Modifier = Modifier,
    paused: () -> Boolean = { state.value >= 0.999f },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .skipDrawWhen(paused)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val dx = (-NAV_PARALLAX * state.value * placeable.width).roundToInt()
                layout(placeable.width, placeable.height) {
                    placeable.place(dx, 0)
                }
            },
    ) { content() }
}

internal fun Modifier.skipDrawWhen(paused: () -> Boolean): Modifier = drawWithContent {
    if (!paused()) drawContent()
}

internal fun Modifier.clipDuringTransition(
    clipShape: Shape?,
    inFlight: () -> Boolean,
): Modifier = if (clipShape == null) {
    this
} else {
    graphicsLayer {
        if (inFlight()) {
            shape = clipShape
            clip = true
        } else {
            clip = false
        }
    }
}

@Composable
internal fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx {
    val context = LocalContext.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(context, density, containerSize) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                ScreenCornerRadiiPx.uniform(20f * density.density)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.currentWindowMetrics?.bounds
                val maxBounds = wm?.maximumWindowMetrics?.bounds
                val isSmallWindow = bounds != null && maxBounds != null &&
                    (bounds.width() < maxBounds.width() * 0.95f ||
                        bounds.height() < maxBounds.height() * 0.95f)
                if (isSmallWindow) {
                    ScreenCornerRadiiPx.uniform(20f * density.density)
                } else {
                    val insets = wm?.currentWindowMetrics?.windowInsets
                    fun radius(position: Int): Float =
                        insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
                    ScreenCornerRadiiPx(
                        topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
                        topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
                        bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                        bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
                    )
                }
            } else {
                ScreenCornerRadiiPx.Zero
            }
        } catch (_: Exception) {
            ScreenCornerRadiiPx.Zero
        }
    }
}
