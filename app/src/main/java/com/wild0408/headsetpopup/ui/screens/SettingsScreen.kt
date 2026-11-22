package com.wild0408.headsetpopup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.wild0408.headsetpopup.ui.navigation.AppRoute
import com.wild0408.headsetpopup.ui.navigation.LocalAppRouter
import com.wild0408.headsetpopup.ui.state.LocalAppSettings
import com.wild0408.headsetpopup.ui.theme.SettingsAccents
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Info
import com.haooz.nexioui.icon.extended.Theme
import com.haooz.nexioui.icon.extended.Tune
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop

@Composable
fun SettingsScreen(
    contentBackdrop: LayerBackdrop,
    chromeBackdrop: GlassBackdrop,
) {
    val settings = LocalAppSettings.current
    val router = LocalAppRouter.current
    val listState = rememberLazyListState()

    MainTabPage(
        title = "设置",
        contentBackdrop = contentBackdrop,
        chromeBackdrop = chromeBackdrop,
    ) { contentPadding, scrollBehavior, _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(text = "个性化")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "外观与材质",
                            summary = settings.materialLabel(),
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Purple,
                                    icon = NexioIcons.Theme,
                                )
                            },
                            onClick = { router.navigate(AppRoute.Appearance) },
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "模块与诊断")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "调试中心",
                            summary = null,
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Green,
                                    icon = NexioIcons.Tune,
                                )
                            },
                            onClick = { router.navigate(AppRoute.Debug) },
                        )
                        ArrowPreference(
                            title = "关于",
                            summary = "2.0.0-hook-foundation",
                            startAction = {
                                SquareAccentIcon(
                                    accent = SettingsAccents.Gray,
                                    icon = NexioIcons.Info,
                                )
                            },
                            onClick = { router.navigate(AppRoute.About) },
                        )
                    }
                }
            }
        }
    }
}
