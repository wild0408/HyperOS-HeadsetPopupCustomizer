package com.wild0408.headsetpopup.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.bottombar.BottomBar
import com.haooz.nexioui.bottombar.BottomBarDefaults
import com.haooz.nexioui.bottombar.BottomBarItem
import com.haooz.softglass.backdrop.GlassBackdrop

private val BOTTOM_BAR_HEIGHT = BottomBarDefaults.Height
private const val TAB_WIDTH_FRACTION = BottomBarDefaults.TabWidthFraction

@Composable
fun bottomFloatingPadding(): Dp {
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(navBarBottomInset + 2.dp, 22.dp)
}

@Composable
fun appBottomBarHeight(): Dp = BOTTOM_BAR_HEIGHT + bottomFloatingPadding()

/**
 * 悬浮居中动态长度底栏：所有一级页面（含设置）统一收口在胶囊导航栏内，无额外悬浮按钮。
 */
@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    liquidGlassBackdrop: GlassBackdrop,
) {
    if (items.isEmpty()) return
    val bottomPadding = bottomFloatingPadding()
    val tabCount = items.size

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .height(BOTTOM_BAR_HEIGHT),
    ) {
        val maxPillWidth = (maxWidth - 32.dp).coerceAtLeast(160.dp)
        val pillWidth = (maxWidth * (TAB_WIDTH_FRACTION * tabCount)).coerceAtMost(maxPillWidth)
        val effectiveFraction = (pillWidth / maxWidth) / tabCount

        BottomBar(
            items = items,
            selectedIndex = selectedTab.coerceIn(0, tabCount - 1),
            onSelect = onTabSelected,
            backdrop = liquidGlassBackdrop,
            modifier = Modifier.fillMaxWidth(),
            height = BOTTOM_BAR_HEIGHT,
            action = null,
            tabWidthFraction = effectiveFraction,
        )
    }
}
