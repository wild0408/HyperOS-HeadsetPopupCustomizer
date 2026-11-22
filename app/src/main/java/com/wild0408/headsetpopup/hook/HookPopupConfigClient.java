package com.wild0408.headsetpopup.hook;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;

/**
 * Cross-process reader used inside com.xiaomi.bluetooth:ui to fetch live
 * resolved popup customization settings and open private image/video assets.
 */
final class HookPopupConfigClient {
    private static final String AUTHORITY = "com.wild0408.headsetpopup.popupconfig";
    private static final Uri PROVIDER_URI = Uri.parse("content://" + AUTHORITY);
    private static final String METHOD_GET_RESOLVED_CONFIG = "getResolvedConfig";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String PATH_MEDIA = "media";

    private HookPopupConfigClient() {}

    static Uri mediaUri(String assetName) {
        return Uri.parse("content://" + AUTHORITY + "/" + PATH_MEDIA + "/" + assetName);
    }

    static ParcelFileDescriptor openMediaDescriptor(Context context, String assetName) {
        if (context == null || assetName == null || assetName.trim().isEmpty()) {
            return null;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            return resolver.openFileDescriptor(mediaUri(assetName.trim()), "r");
        } catch (Throwable error) {
            HookLog.w("openMediaDescriptor failed asset=" + assetName, error);
            return null;
        }
    }

    static Config loadForDevice(Context context, String deviceId) {
        if (context == null) return Config.EMPTY;
        try {
            ContentResolver resolver = context.getContentResolver();
            String arg = deviceId == null ? "" : deviceId.trim().toUpperCase();
            Bundle reply = resolver.call(PROVIDER_URI, METHOD_GET_RESOLVED_CONFIG, arg, null);
            if (reply == null) {
                HookLog.w("config provider returned null bundle deviceId=" + arg, null);
                return Config.EMPTY;
            }
            String raw = reply.getString(KEY_CONFIG_JSON);
            if (raw == null || raw.trim().isEmpty()) {
                return Config.EMPTY;
            }
            Config parsed = Config.fromJson(new JSONObject(raw));
            HookLog.i("loaded popup config deviceId=" + arg
                    + " active=" + parsed.active
                    + " profile=" + parsed.matchedProfileId
                    + " hasAnyEffect=" + parsed.hasAnyEffect());
            return parsed;
        } catch (Throwable error) {
            HookLog.w("loadForDevice failed deviceId=" + deviceId, error);
            return Config.EMPTY;
        }
    }

    static final class MediaSpec {
        static final MediaSpec NONE = new MediaSpec("none", "", "", "fit_center", true, 0f, 0f, 1f, 1f);

        final String type;
        final String assetName;
        final String displayName;
        final String scaleMode;
        final boolean loop;
        final float cropLeft;
        final float cropTop;
        final float cropRight;
        final float cropBottom;

        MediaSpec(
                String type,
                String assetName,
                String displayName,
                String scaleMode,
                boolean loop,
                float cropLeft,
                float cropTop,
                float cropRight,
                float cropBottom
        ) {
            this.type = type == null ? "none" : type;
            this.assetName = assetName == null ? "" : assetName.trim();
            this.displayName = displayName == null ? "" : displayName;
            this.scaleMode = scaleMode == null ? "fit_center" : scaleMode;
            this.loop = loop;
            float l = clamp(cropLeft, 0f, 0.95f);
            float t = clamp(cropTop, 0f, 0.95f);
            float r = clamp(cropRight, l + 0.05f, 1f);
            float b = clamp(cropBottom, t + 0.05f, 1f);
            this.cropLeft = l;
            this.cropTop = t;
            this.cropRight = r;
            this.cropBottom = b;
        }

        boolean isConfigured() {
            return !"none".equalsIgnoreCase(type) && !assetName.isEmpty();
        }

        boolean isVideo() {
            return "video".equalsIgnoreCase(type);
        }

        boolean isCenterCrop() {
            return "center_crop".equalsIgnoreCase(scaleMode);
        }

        boolean hasCustomCrop() {
            return cropLeft > 0.002f || cropTop > 0.002f || cropRight < 0.998f || cropBottom < 0.998f;
        }

        String signature(boolean forceLoopOverride, boolean forcedLoop) {
            boolean effectiveLoop = forceLoopOverride ? forcedLoop : loop;
            return type + "|" + assetName + "|" + scaleMode + "|" + effectiveLoop
                    + "|" + Math.round(cropLeft * 1000f)
                    + "," + Math.round(cropTop * 1000f)
                    + "," + Math.round(cropRight * 1000f)
                    + "," + Math.round(cropBottom * 1000f);
        }

        static MediaSpec fromJson(JSONObject obj, String defaultScale) {
            if (obj == null) return new MediaSpec("none", "", "", defaultScale, true, 0f, 0f, 1f, 1f);
            JSONObject cropObj = obj.optJSONObject("crop");
            float l = cropObj != null ? (float) cropObj.optDouble("left", 0.0) : 0f;
            float t = cropObj != null ? (float) cropObj.optDouble("top", 0.0) : 0f;
            float r = cropObj != null ? (float) cropObj.optDouble("right", 1.0) : 1f;
            float b = cropObj != null ? (float) cropObj.optDouble("bottom", 1.0) : 1f;
            return new MediaSpec(
                    obj.optString("type", "none"),
                    obj.optString("assetName", ""),
                    obj.optString("displayName", ""),
                    obj.optString("scaleMode", defaultScale),
                    obj.optBoolean("loop", true),
                    l,
                    t,
                    r,
                    b
            );
        }
    }

    static final class BackgroundConfig {
        static final BackgroundConfig NONE = new BackgroundConfig(false, MediaSpec.NONE, 1.0f, 0.0f, "auto");

        final boolean enabled;
        final MediaSpec media;
        final float alpha;
        final float scrimAlpha;
        final String foregroundTone;

        BackgroundConfig(boolean enabled, MediaSpec media, float alpha, float scrimAlpha, String foregroundTone) {
            this.enabled = enabled;
            this.media = media == null ? MediaSpec.NONE : media;
            this.alpha = clamp(alpha, 0.15f, 1.0f);
            this.scrimAlpha = clamp(scrimAlpha, 0.0f, 0.80f);
            this.foregroundTone = foregroundTone == null ? "auto" : foregroundTone;
        }

        boolean isActive() {
            return enabled && media.isConfigured();
        }

        static BackgroundConfig fromJson(JSONObject obj) {
            if (obj == null) return NONE;
            return new BackgroundConfig(
                    obj.optBoolean("enabled", false),
                    MediaSpec.fromJson(obj.optJSONObject("media"), "center_crop"),
                    (float) obj.optDouble("alpha", 1.0),
                    (float) obj.optDouble("scrimAlpha", 0.0),
                    obj.optString("foregroundTone", "auto")
            );
        }
    }

    static final class StageConfig {
        static final StageConfig NONE = new StageConfig(false, "", "", "", MediaSpec.NONE);

        final boolean enabled;
        final String title;
        final String subtitle;
        final String buttonText;
        final MediaSpec media;

        StageConfig(boolean enabled, String title, String subtitle, String buttonText, MediaSpec media) {
            this.enabled = enabled;
            this.title = title == null ? "" : title.trim();
            this.subtitle = subtitle == null ? "" : subtitle.trim();
            this.buttonText = buttonText == null ? "" : buttonText.trim();
            this.media = media == null ? MediaSpec.NONE : media;
        }

        boolean hasAnyOverride() {
            return enabled && (!title.isEmpty() || !subtitle.isEmpty() || !buttonText.isEmpty() || media.isConfigured());
        }

        static StageConfig fromJson(JSONObject obj) {
            if (obj == null) return NONE;
            return new StageConfig(
                    obj.optBoolean("enabled", false),
                    obj.optString("title", ""),
                    obj.optString("subtitle", ""),
                    obj.optString("buttonText", ""),
                    MediaSpec.fromJson(obj.optJSONObject("media"), "fit_center")
            );
        }
    }

    static final class ConnectedConfig {
        static final ConnectedConfig NONE = new ConnectedConfig(
                false, "", "", "native", MediaSpec.NONE, "separate", MediaSpec.NONE, MediaSpec.NONE, MediaSpec.NONE);

        final boolean enabled;
        final String title;
        final String buttonText;
        final String transitionMode;
        final MediaSpec transitionMedia;
        final String visualMode;
        final MediaSpec headsetMedia;
        final MediaSpec caseMedia;
        final MediaSpec unifiedMedia;

        ConnectedConfig(
                boolean enabled,
                String title,
                String buttonText,
                String transitionMode,
                MediaSpec transitionMedia,
                String visualMode,
                MediaSpec headsetMedia,
                MediaSpec caseMedia,
                MediaSpec unifiedMedia
        ) {
            this.enabled = enabled;
            this.title = title == null ? "" : title.trim();
            this.buttonText = buttonText == null ? "" : buttonText.trim();
            this.transitionMode = transitionMode == null ? "native" : transitionMode;
            this.transitionMedia = transitionMedia == null ? MediaSpec.NONE : transitionMedia;
            this.visualMode = visualMode == null ? "separate" : visualMode;
            this.headsetMedia = headsetMedia == null ? MediaSpec.NONE : headsetMedia;
            this.caseMedia = caseMedia == null ? MediaSpec.NONE : caseMedia;
            this.unifiedMedia = unifiedMedia == null ? MediaSpec.NONE : unifiedMedia;
        }

        boolean isSkipTransition() {
            return enabled && "skip".equalsIgnoreCase(transitionMode);
        }

        boolean isCustomTransition() {
            return enabled && "custom".equalsIgnoreCase(transitionMode) && transitionMedia.isConfigured();
        }

        boolean isUnifiedVisual() {
            return enabled && "unified".equalsIgnoreCase(visualMode) && unifiedMedia.isConfigured();
        }

        boolean isSeparateVisual() {
            return enabled && "separate".equalsIgnoreCase(visualMode)
                    && (headsetMedia.isConfigured() || caseMedia.isConfigured());
        }

        boolean hasAnyOverride() {
            return enabled && (
                    !title.isEmpty()
                            || !buttonText.isEmpty()
                            || isSkipTransition()
                            || isCustomTransition()
                            || isUnifiedVisual()
                            || isSeparateVisual()
            );
        }

        static ConnectedConfig fromJson(JSONObject obj) {
            if (obj == null) return NONE;
            return new ConnectedConfig(
                    obj.optBoolean("enabled", false),
                    obj.optString("title", ""),
                    obj.optString("buttonText", ""),
                    obj.optString("transitionMode", "native"),
                    MediaSpec.fromJson(obj.optJSONObject("transitionMedia"), "fit_center"),
                    obj.optString("visualMode", "separate"),
                    MediaSpec.fromJson(obj.optJSONObject("headsetMedia"), "fit_center"),
                    MediaSpec.fromJson(obj.optJSONObject("caseMedia"), "fit_center"),
                    MediaSpec.fromJson(obj.optJSONObject("unifiedMedia"), "fit_center")
            );
        }
    }

    static final class Config {
        static final Config EMPTY = new Config(
                false, "", "",
                BackgroundConfig.NONE,
                "native",
                StageConfig.NONE,
                StageConfig.NONE,
                ConnectedConfig.NONE,
                StageConfig.NONE,
                StageConfig.NONE
        );

        final boolean active;
        final String matchedProfileId;
        final String matchedProfileName;
        final BackgroundConfig background;
        final String buttonStyle;
        final StageConfig findStage;
        final StageConfig connectingStage;
        final ConnectedConfig connectedStage;
        final StageConfig failStage;
        final StageConfig resetStage;

        Config(
                boolean active,
                String matchedProfileId,
                String matchedProfileName,
                BackgroundConfig background,
                String buttonStyle,
                StageConfig findStage,
                StageConfig connectingStage,
                ConnectedConfig connectedStage,
                StageConfig failStage,
                StageConfig resetStage
        ) {
            this.active = active;
            this.matchedProfileId = matchedProfileId == null ? "" : matchedProfileId;
            this.matchedProfileName = matchedProfileName == null ? "" : matchedProfileName;
            this.background = background == null ? BackgroundConfig.NONE : background;
            this.buttonStyle = buttonStyle == null ? "native" : buttonStyle;
            this.findStage = findStage == null ? StageConfig.NONE : findStage;
            this.connectingStage = connectingStage == null ? StageConfig.NONE : connectingStage;
            this.connectedStage = connectedStage == null ? ConnectedConfig.NONE : connectedStage;
            this.failStage = failStage == null ? StageConfig.NONE : failStage;
            this.resetStage = resetStage == null ? StageConfig.NONE : resetStage;
        }

        boolean hasGlassButtons() {
            return !"native".equalsIgnoreCase(buttonStyle)
                    && !"inherit".equalsIgnoreCase(buttonStyle)
                    && !buttonStyle.trim().isEmpty();
        }

        boolean hasAnyEffect() {
            return active && (
                    background.isActive()
                            || hasGlassButtons()
                            || findStage.hasAnyOverride()
                            || connectingStage.hasAnyOverride()
                            || connectedStage.hasAnyOverride()
                            || failStage.hasAnyOverride()
                            || resetStage.hasAnyOverride()
            );
        }

        StageConfig stageForConnectState(int connectState) {
            switch (connectState) {
                case 1:
                    return findStage;
                case 2:
                    return connectingStage;
                case 4:
                    return failStage;
                case 5:
                    return resetStage;
                default:
                    return StageConfig.NONE;
            }
        }

        static Config fromJson(JSONObject obj) {
            if (obj == null) return EMPTY;
            return new Config(
                    obj.optBoolean("active", false),
                    obj.optString("matchedProfileId", ""),
                    obj.optString("matchedProfileName", ""),
                    BackgroundConfig.fromJson(obj.optJSONObject("background")),
                    obj.optString("buttonStyle", "native"),
                    StageConfig.fromJson(obj.optJSONObject("findStage")),
                    StageConfig.fromJson(obj.optJSONObject("connectingStage")),
                    ConnectedConfig.fromJson(obj.optJSONObject("connectedStage")),
                    StageConfig.fromJson(obj.optJSONObject("failStage")),
                    StageConfig.fromJson(obj.optJSONObject("resetStage"))
            );
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
