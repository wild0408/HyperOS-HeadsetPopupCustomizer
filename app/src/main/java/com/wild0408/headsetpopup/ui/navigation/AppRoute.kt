package com.wild0408.headsetpopup.ui.navigation

import androidx.compose.runtime.Immutable

@Immutable
sealed class AppRoute(val id: String) {
    data object Appearance : AppRoute("appearance")
    data object Debug : AppRoute("debug")
    data object About : AppRoute("about")
    data class StageEditor(
        val profileId: String,
        val stageKey: String,
    ) : AppRoute("stage_editor:$profileId:$stageKey")
    data class MediaCrop(
        val requestId: String,
    ) : AppRoute("media_crop:$requestId")

    companion object {
        fun fromId(id: String): AppRoute? {
            if (id.startsWith("stage_editor:")) {
                val parts = id.split(":")
                if (parts.size == 3 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
                    return StageEditor(profileId = parts[1], stageKey = parts[2])
                }
            }
            if (id.startsWith("media_crop:")) {
                val requestId = id.removePrefix("media_crop:")
                if (requestId.isNotBlank()) {
                    return MediaCrop(requestId = requestId)
                }
            }
            return when (id) {
                Appearance.id -> Appearance
                Debug.id -> Debug
                About.id -> About
                else -> null
            }
        }
    }
}
