package com.wild0408.headsetpopup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.ChevronBackward
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overlaydialog.LocalOverlayDialogBackdrop
import com.haooz.nexioui.overscroll.LocalOverScrollState
import com.haooz.nexioui.overscroll.OverScrollState
import com.haooz.nexioui.progressiveblurtopbar.ProgressiveBlurTopBar
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBar
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.rememberSharedScrollBehavior
import com.haooz.nexioui.topbarmaterial.LocalTopBarMaterial
import com.haooz.nexioui.topbarmaterial.rememberTopBarMaterialState
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop

/** NexioSchedule 同款的二级文档页脚手架。 */
@Composable
fun DocumentPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    collapsedOnly: Boolean = false,
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float, backdrop: GlassBackdrop) -> Unit)? = null,
    content: @Composable (
        paddingValues: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
        liquidGlassBackdrop: GlassBackdrop,
    ) -> Unit,
) {
    val scrollBehavior = rememberSharedScrollBehavior()
    if (collapsedOnly) {
        LaunchedEffect(scrollBehavior) {
            scrollBehavior.collapseImmediately()
        }
    }

    val surfaceColor = NexioTheme.colorScheme.surface
    val pageOverScroll = remember { OverScrollState() }
    val liquidGlassBackdrop = rememberLayerBackdrop()

    // 顶部和底部 Insets 由 Scaffold 的 PaddingValues 交给页面内容，横向安全区由
    // 文档页根节点统一处理，避免顶栏与内容重复消费状态栏。
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        topBar = {},
    ) { paddingValues ->
        CompositionLocalProvider(LocalOverScrollState provides pageOverScroll) {
            // 必须在提供 pageOverScroll 之后创建 material，并同时观察 heightOffset 与 contentOffset，
            // 确保设置/外观/关于等短列表页面在折叠大标题或上拉回弹时，返回按钮材质与长列表页完全一致。
            val material = rememberTopBarMaterialState(
                contentOffsetProvider = {
                    minOf(scrollBehavior.state.contentOffset, scrollBehavior.state.heightOffset)
                },
                overScrollState = pageOverScroll,
            )
            CompositionLocalProvider(
                LocalTopBarMaterial provides material,
                LocalOverlayDialogBackdrop provides if (NexioMaterial.isNone()) null else liquidGlassBackdrop,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // background(surfaceColor) 放在 layerBackdrop 之后，使采样层内纯色区域 coverage 恒为 1，
                    // 与 NexioSchedule 二级页在采样层内渲染不透明 Surface 的效果保持一致。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (NexioMaterial.isNone()) {
                                    Modifier
                                } else {
                                    Modifier.layerBackdrop(
                                        backdrop = liquidGlassBackdrop,
                                        mustRecord = rememberDocumentRecordPredicate(scrollBehavior, pageOverScroll),
                                    )
                                },
                            )
                            .background(surfaceColor),
                    ) {
                        content(paddingValues, scrollBehavior, liquidGlassBackdrop)
                    }

                    // 顶栏和返回按钮直接使用 NexioUI 官方实现，不额外绘制按钮背景。
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ProgressiveBlurTopBar(backdrop = liquidGlassBackdrop) {
                            CollapsibleTopAppBar(
                                title = title,
                                largeTitle = title,
                                showLargeTitle = if (collapsedOnly) false else null,
                                showSmallTitle = if (collapsedOnly) true else null,
                                scrollBehavior = scrollBehavior,
                                materialState = material,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    IconButton(
                                        onClick = onBack,
                                        backdrop = liquidGlassBackdrop,
                                        icon = NexioIcons.ChevronBackward,
                                        contentDescription = "返回",
                                        performHapticFeedback = false,
                                        iconSize = 25.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = endAction?.let { action ->
                                    { backdropAlpha, shadowAlpha ->
                                        action(backdropAlpha, shadowAlpha, liquidGlassBackdrop)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 标题折叠、列表滚动或上拉回弹变化时重录采样层，确保返回按钮玻璃采样实时同步。 */
@Composable
private fun rememberDocumentRecordPredicate(
    scrollBehavior: SharedScrollBehavior,
    overScrollState: OverScrollState,
): () -> Boolean {
    val currentHeightOffset = rememberUpdatedState(scrollBehavior.state.heightOffset)
    val currentContentOffset = rememberUpdatedState(scrollBehavior.state.contentOffset)
    val currentOverScroll = rememberUpdatedState(overScrollState.offset)
    return remember(scrollBehavior, overScrollState) {
        var lastHeight = Float.NaN
        var lastContent = Float.NaN
        var lastOverScroll = Float.NaN
        {
            val h = currentHeightOffset.value
            val c = currentContentOffset.value
            val o = currentOverScroll.value
            (h != lastHeight || c != lastContent || o != lastOverScroll).also {
                lastHeight = h
                lastContent = c
                lastOverScroll = o
            }
        }
    }
}
