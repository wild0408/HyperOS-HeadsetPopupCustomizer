package com.wild0408.headsetpopup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wild0408.headsetpopup.ui.components.appBottomBarHeight
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overscroll.LocalOverScrollState
import com.haooz.nexioui.overscroll.OverScrollState
import com.haooz.nexioui.progressiveblurtopbar.ProgressiveBlurTopBar
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBar
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.rememberSharedScrollBehavior
import com.haooz.nexioui.topbarmaterial.LocalTopBarMaterial
import com.haooz.nexioui.topbarmaterial.rememberTopBarMaterialState
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop

/**
 * 主界面单个页签容器：独立管理本页的折叠大标题顶栏、渐进模糊采样层与弹簧回弹状态。
 * 采样层只包裹滚动内容，避免顶栏与底栏玻璃采样形成循环。
 */
@Composable
fun MainTabPage(
    title: String,
    contentBackdrop: LayerBackdrop,
    chromeBackdrop: GlassBackdrop,
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    content: @Composable (
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
        backdrop: GlassBackdrop,
    ) -> Unit,
) {
    val scrollBehavior = rememberSharedScrollBehavior()
    val pageOverScroll = remember { OverScrollState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomBarHeight = appBottomBarHeight()

    CompositionLocalProvider(LocalOverScrollState provides pageOverScroll) {
        val material = rememberTopBarMaterialState(scrollBehavior)
        val contentMustRecord = rememberDocumentRecordPredicate(scrollBehavior)
        val contentLayerModifier = if (NexioMaterial.isNone()) {
            Modifier
        } else {
            Modifier.layerBackdrop(
                backdrop = contentBackdrop,
                mustRecord = contentMustRecord,
            )
        }
        val listPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarTop + CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
            bottom = bottomBarHeight + 24.dp,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentLayerModifier)
                    .background(NexioTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                CompositionLocalProvider(LocalTopBarMaterial provides material) {
                    content(listPadding, scrollBehavior, chromeBackdrop)
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                ProgressiveBlurTopBar(backdrop = contentBackdrop) {
                    CollapsibleTopAppBar(
                        title = title,
                        largeTitle = title,
                        scrollBehavior = scrollBehavior,
                        materialState = material,
                        endAction = endAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDocumentRecordPredicate(scrollBehavior: SharedScrollBehavior): () -> Boolean {
    val currentOffset = androidx.compose.runtime.rememberUpdatedState(scrollBehavior.state.heightOffset)
    return remember(scrollBehavior) {
        var lastOffset = Float.NaN
        {
            val offset = currentOffset.value
            (offset != lastOffset).also { lastOffset = offset }
        }
    }
}
