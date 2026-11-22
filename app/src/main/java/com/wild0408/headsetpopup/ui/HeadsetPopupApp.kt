package com.wild0408.headsetpopup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wild0408.headsetpopup.config.LocalPopupConfig
import com.wild0408.headsetpopup.config.rememberPopupConfigState
import com.wild0408.headsetpopup.ui.components.AppBottomBar
import com.wild0408.headsetpopup.ui.components.HorizontalPageSwitch
import com.wild0408.headsetpopup.ui.components.rememberPageSwitchState
import com.wild0408.headsetpopup.ui.navigation.AppNavHost
import com.wild0408.headsetpopup.ui.navigation.AppRoute
import com.wild0408.headsetpopup.ui.navigation.LocalAppRouter
import com.wild0408.headsetpopup.ui.navigation.MainLayerTransition
import com.wild0408.headsetpopup.ui.navigation.rememberAppRouter
import com.wild0408.headsetpopup.ui.navigation.rememberNavTransitionState
import com.wild0408.headsetpopup.ui.screens.AboutScreen
import com.wild0408.headsetpopup.ui.screens.AppearanceScreen
import com.wild0408.headsetpopup.ui.screens.DebugScreen
import com.wild0408.headsetpopup.ui.screens.HomeScreen
import com.wild0408.headsetpopup.ui.screens.LocalMediaCropCoordinator
import com.wild0408.headsetpopup.ui.screens.MediaCropScreen
import com.wild0408.headsetpopup.ui.screens.SettingsScreen
import com.wild0408.headsetpopup.ui.screens.StageEditorScreen
import com.wild0408.headsetpopup.ui.screens.StylePreviewScreen
import com.wild0408.headsetpopup.ui.screens.rememberMediaCropCoordinator
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.state.rememberAppSettingsState
import com.wild0408.headsetpopup.ui.theme.HeadsetTheme
import com.haooz.nexioui.bottombar.BottomBarItem
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Home
import com.haooz.nexioui.icon.extended.Image
import com.haooz.nexioui.icon.extended.Settings
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overlaydialog.LocalOverlayDialogBackdrop
import com.haooz.nexioui.overlaydialog.NexioPopupHost
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCombinedBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private enum class MainTab {
    Home,
    Style,
    Settings,
}

@Composable
fun HeadsetPopupApp() {
    val settings = rememberAppSettingsState()
    val popupConfig = rememberPopupConfigState()
    val router = rememberAppRouter()
    val cropCoordinator = rememberMediaCropCoordinator(router)
    val mainTransition = rememberNavTransitionState(
        initialValue = if (router.hasOverlay) 1f else 0f,
    )

    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalPopupConfig provides popupConfig,
        LocalAppRouter provides router,
        LocalMediaCropCoordinator provides cropCoordinator,
    ) {
        HeadsetTheme(settings = settings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexioTheme.colorScheme.surface),
            ) {
                MainLayerTransition(state = mainTransition) {
                    MainShell()
                }

                AppNavHost(
                    router = router,
                    mainTransition = mainTransition,
                ) { _, route ->
                    when (route) {
                        AppRoute.Appearance -> AppearanceScreen(onBack = { router.popBack() })
                        AppRoute.Debug -> DebugScreen(onBack = { router.popBack() })
                        AppRoute.About -> AboutScreen(onBack = { router.popBack() })
                        is AppRoute.StageEditor -> StageEditorScreen(
                            profileId = route.profileId,
                            stageKey = route.stageKey,
                            onBack = { router.popBack() },
                        )
                        is AppRoute.MediaCrop -> MediaCropScreen(
                            requestId = route.requestId,
                            onBack = { router.popBack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainShell() {
    val tabs = remember { listOf(MainTab.Home, MainTab.Style, MainTab.Settings) }
    val navItems = remember {
        listOf(
            BottomBarItem(icon = NexioIcons.Home, label = "首页"),
            BottomBarItem(icon = NexioIcons.Image, label = "自定义"),
            BottomBarItem(icon = NexioIcons.Settings, label = "设置"),
        )
    }

    var activeTab by remember { mutableStateOf(MainTab.Home) }
    val selectedIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
    val pageSwitchState = rememberPageSwitchState(initialPage = selectedIndex)
    val scope = rememberCoroutineScope()

    val surfaceColor = NexioTheme.colorScheme.surface
    val surfaceBackdrop = rememberLayerBackdrop()
    val homeBackdrop = rememberLayerBackdrop()
    val styleBackdrop = rememberLayerBackdrop()
    val settingsBackdrop = rememberLayerBackdrop()
    val bottomBarSampleBackdrop = rememberLayerBackdrop()

    // 与 NexioSchedule 一致：屏幕坐标纯色垫底 + 各页内容层组成 mainContentBackdrop 给底栏采样；
    // 底栏单独录一层并入 overlayGlassBackdrop，供 OverlayDialog / 下拉菜单在底栏之上连续采样。
    val mainContentBackdrop = rememberCombinedBackdrop(surfaceBackdrop, homeBackdrop, styleBackdrop, settingsBackdrop)
    val overlayGlassBackdrop = rememberCombinedBackdrop(mainContentBackdrop, bottomBarSampleBackdrop)

    CompositionLocalProvider(
        LocalOverlayDialogBackdrop provides if (NexioMaterial.isNone()) null else overlayGlassBackdrop,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = surfaceColor,
            // NexioUI 官方弹层宿主，OverlayDialog 会在这里统一处理 dim、返回键和退场动画。
            popupHost = { NexioPopupHost() },
            bottomBar = {
                Box(
                    modifier = Modifier.then(
                        if (NexioMaterial.isNone()) {
                            Modifier
                        } else {
                            Modifier.layerBackdrop(backdrop = bottomBarSampleBackdrop)
                        },
                    ),
                ) {
                    AppBottomBar(
                        items = navItems,
                        selectedTab = selectedIndex,
                        onTabSelected = { index ->
                            val target = index.coerceIn(0, tabs.lastIndex)
                            activeTab = tabs[target]
                            scope.launch { pageSwitchState.animateTo(target) }
                        },
                        liquidGlassBackdrop = mainContentBackdrop,
                    )
                }
            },
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 屏幕坐标不透明底色层：保证切页、边缘留白与纯色区域采样 coverage 恒为 1。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (NexioMaterial.isNone()) {
                                Modifier
                            } else {
                                Modifier.layerBackdrop(
                                    backdrop = surfaceBackdrop,
                                    recordKey = surfaceColor.value.toLong(),
                                )
                            },
                        )
                        .background(surfaceColor),
                )

                HorizontalPageSwitch(
                    state = pageSwitchState,
                    pageCount = tabs.size,
                    beyondPageCount = tabs.size,
                    pageKey = { tabs[it] },
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    when (tabs[pageIndex]) {
                        MainTab.Home -> HomeScreen(
                            contentBackdrop = homeBackdrop,
                            chromeBackdrop = overlayGlassBackdrop,
                        )
                        MainTab.Style -> StylePreviewScreen(
                            contentBackdrop = styleBackdrop,
                            chromeBackdrop = overlayGlassBackdrop,
                        )
                        MainTab.Settings -> SettingsScreen(
                            contentBackdrop = settingsBackdrop,
                            chromeBackdrop = overlayGlassBackdrop,
                        )
                    }
                }
            }
        }
    }
}
