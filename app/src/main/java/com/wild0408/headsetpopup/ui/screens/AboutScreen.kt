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
import com.wild0408.headsetpopup.ui.components.DocumentPageScaffold
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()

    DocumentPageScaffold(
        title = "关于",
        onBack = onBack,
    ) { paddingValues, scrollBehavior, _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(text = "应用信息")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "耳机弹窗自定义工具",
                            summary = "版本 2.0.0-hook-foundation",
                        )
                        BasicComponent(
                            title = "工作方式",
                            summary = "HyperOS 原生 PairingDialog 原位 Hook",
                        )
                        BasicComponent(
                            title = "作用域",
                            summary = "com.xiaomi.bluetooth",
                        )
                        BasicComponent(
                            title = "界面框架",
                            summary = "NexioUI 0.1.1 · SoftGlass 0.1.3",
                        )
                    }
                }
            }
        }
    }
}
