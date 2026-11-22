package com.wild0408.headsetpopup.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

@Composable
fun HorizontalPageSwitch(
    state: PageSwitchState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    beyondPageCount: Int = 1,
    pageKey: (Int) -> Any = { it },
    content: @Composable (Int) -> Unit,
) {
    state.pageCount = pageCount.coerceAtLeast(1)

    LaunchedEffect(state.pageCount) {
        if (!state.dragging && state.position.value > state.last) {
            state.snapTo(state.lastPage)
        }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pos: () -> Float = { state.visual }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -pos() * pageWidthPx },
        ) {
            Pages(
                state = state,
                pageCount = pageCount.coerceAtLeast(1),
                beyond = beyondPageCount.coerceAtLeast(0),
                pageKey = pageKey,
                content = content,
            )
        }
    }
}

@Composable
private fun Pages(
    state: PageSwitchState,
    pageCount: Int,
    beyond: Int,
    pageKey: (Int) -> Any,
    content: @Composable (Int) -> Unit,
) {
    val lastPage = (pageCount - 1).coerceAtLeast(0)
    // 当所有页都常驻时不再读取 state.currentPage，避免切页动画过半（0.5）时触发无意义重组。
    val first: Int
    val lastVisible: Int
    if (beyond >= lastPage) {
        first = 0
        lastVisible = lastPage
    } else {
        val anchor = state.currentPage.coerceIn(0, lastPage)
        first = (anchor - beyond).coerceAtLeast(0)
        lastVisible = (anchor + beyond).coerceAtMost(lastPage)
    }
    Layout(
        content = {
            for (index in first..lastVisible) {
                key(pageKey(index)) {
                    Box(Modifier.fillMaxSize()) {
                        content(index)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = constraints.maxWidth
        val unboundedHeight = constraints.maxHeight == Constraints.Infinity
        val placeables = measurables.map {
            if (unboundedHeight) {
                it.measure(Constraints(minWidth = pageWidth, maxWidth = pageWidth))
            } else {
                it.measure(Constraints.fixed(pageWidth, constraints.maxHeight))
            }
        }
        val h = if (unboundedHeight) {
            placeables.maxOfOrNull { it.height } ?: 0
        } else {
            constraints.maxHeight
        }
        layout(pageWidth, h) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place((first + i) * pageWidth, 0)
            }
        }
    }
}
