package com.wild0408.headsetpopup.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppRouter = staticCompositionLocalOf<AppRouter> {
    error("LocalAppRouter not provided")
}

enum class NavDirection { Push, Pop }

@Stable
class AppRouter {
    private val stack = mutableStateListOf<AppRoute>()

    val hasOverlay: Boolean get() = stack.isNotEmpty()
    val current: AppRoute? get() = stack.lastOrNull()
    val depth: Int get() = stack.size

    var lastDirection: NavDirection = NavDirection.Push
        private set

    var lastPopped: AppRoute? = null
        private set

    val routes: List<AppRoute> get() = stack

    fun navigate(route: AppRoute) {
        if (stack.lastOrNull() == route) return
        stack.add(route)
        lastDirection = NavDirection.Push
    }

    fun popBack(): Boolean {
        if (stack.isEmpty()) return false
        lastPopped = stack.removeAt(stack.lastIndex)
        lastDirection = NavDirection.Pop
        return true
    }

    internal fun stackIds(): List<String> = stack.map { it.id }
}

@Composable
fun rememberAppRouter(): AppRouter {
    return rememberSaveable(saver = appRouterSaver) { AppRouter() }
}

private val appRouterSaver: Saver<AppRouter, Any> = listSaver(
    save = { it.stackIds() },
    restore = { ids -> AppRouter().also { r -> ids.mapNotNull(AppRoute::fromId).forEach(r::navigate) } },
)
