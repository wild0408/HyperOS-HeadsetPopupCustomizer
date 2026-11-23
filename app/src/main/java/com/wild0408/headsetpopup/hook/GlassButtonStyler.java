package com.wild0408.headsetpopup.hook;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Applies SoftGlass-inspired glass materials (Liquid, Soft, Frosted, Tint Black, Tint Purple)
 * directly onto native HyperOS PairingDialog action buttons and the top-right close ('X') button.
 * Layers per-preset HyperOS real-time background blur with an API 33+ AGSL optical glass shader
 * (ported from SoftGlass SDF bevel, dual directional rim lighting, liquid chromatic dispersion,
 * and frosted satin grain) and falls back cleanly on older Android versions.
 */
public final class GlassButtonStyler {
    private static final int TAG_APPLIED_STYLE = 0x5F474C53; // "_GLS"

    private static final int KIND_LIQUID = 0;
    private static final int KIND_SOFT = 1;
    private static final int KIND_FROSTED = 2;
    private static final int KIND_TINT_DARK = 3;
    private static final int KIND_TINT_PURPLE = 4;

    private static final String SOFTGLASS_SURFACE_AGSL =
            "uniform float2 uSize;\n" +
            "uniform float  uCornerR;\n" +
            "uniform float  uKind;\n" +
            "uniform float  uDark;\n" +
            "uniform float  uHasBlur;\n" +
            "uniform float  uEdgePx;\n" +
            "uniform float  uPxPerDp;\n" +
            "uniform float4 uBodyTop;\n" +
            "uniform float4 uBodyBottom;\n" +
            "uniform float4 uRimColor;\n" +
            "uniform float  uDispersion;\n" +
            "uniform float  uGrain;\n" +
            "\n" +
            "float sdRoundRect(float2 p, float2 b, float r) {\n" +
            "    float2 q = abs(p) - b + float2(r);\n" +
            "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;\n" +
            "}\n" +
            "\n" +
            "float2 roundRectGrad(float2 p, float2 b, float r) {\n" +
            "    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);\n" +
            "    float2 q = abs(p) - b + float2(r);\n" +
            "    if (q.x > 0.0 && q.y > 0.0) {\n" +
            "        return s * normalize(q);\n" +
            "    }\n" +
            "    return (q.x > q.y) ? float2(s.x, 0.0) : float2(0.0, s.y);\n" +
            "}\n" +
            "\n" +
            "float smooth5Map(float t) {\n" +
            "    t = clamp(mix(0.5, 1.0, t), 0.0, 1.0);\n" +
            "    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);\n" +
            "    return (t - 0.5) * 2.0;\n" +
            "}\n" +
            "\n" +
            "float hash12(float2 p) {\n" +
            "    float3 p3 = fract(float3(p.xyx) * 0.1031);\n" +
            "    p3 += dot(p3, p3.yzx + 33.33);\n" +
            "    return fract((p3.x + p3.y) * p3.z);\n" +
            "}\n" +
            "\n" +
            "half4 main(float2 fragCoord) {\n" +
            "    float2 halfSize = max(uSize * 0.5, float2(1.0));\n" +
            "    float2 p = fragCoord - halfSize;\n" +
            "    float r = min(uCornerR, min(halfSize.x, halfSize.y));\n" +
            "    float sd = sdRoundRect(p, halfSize, r);\n" +
            "    if (sd > 0.5) { return half4(0.0); }\n" +
            "    float edgeAA = clamp(0.5 - sd, 0.0, 1.0);\n" +
            "\n" +
            "    float pxDp = max(uPxPerDp, 1.0);\n" +
            "    float distIn = max(-sd, 0.0);\n" +
            "    float x = clamp(distIn / max(uEdgePx, 1.0), 0.0, 1.0);\n" +
            "    float bevel = smooth5Map(sqrt(x));\n" +
            "    float rimBand = 1.0 - bevel;\n" +
            "    float2 grad = roundRectGrad(p, halfSize, r);\n" +
            "    float slope = rimBand * rimBand;\n" +
            "    float3 normal = normalize(float3(grad.x * slope * 2.4, grad.y * slope * 2.4, 1.0));\n" +
            "\n" +
            "    float vy = clamp(fragCoord.y / max(uSize.y, 1.0), 0.0, 1.0);\n" +
            "    float4 body = mix(uBodyTop, uBodyBottom, vy);\n" +
            "\n" +
            "    float3 lightN = float3(normal.x, -normal.y, normal.z);\n" +
            "    float3 keyDir = normalize(float3(-0.62, 0.72, 0.34));\n" +
            "    float3 oppDir = normalize(float3(0.58, -0.68, 0.44));\n" +
            "    float keySpec = pow(max(dot(lightN, keyDir), 0.0), 2.4);\n" +
            "    float oppSpec = pow(max(dot(lightN, oppDir), 0.0), 2.8) * 0.58;\n" +
            "    float rimMask = 1.0 - smoothstep(0.0, max(uEdgePx * 0.85, 1.5), distIn);\n" +
            "    float outerStroke = 1.0 - smoothstep(0.45 * pxDp, 1.25 * pxDp, distIn);\n" +
            "    float innerHighlight = smoothstep(0.75 * pxDp, 1.55 * pxDp, distIn) * (1.0 - smoothstep(1.65 * pxDp, 3.2 * pxDp, distIn));\n" +
            "    float innerGlint = smoothstep(0.0, 1.2 * pxDp, distIn) * (1.0 - smoothstep(1.2 * pxDp, max(uEdgePx * 0.8, 3.5 * pxDp), distIn));\n" +
            "\n" +
            "    float3 rgb = body.rgb;\n" +
            "    float alpha = body.a;\n" +
            "\n" +
            "    float topDome = pow(1.0 - vy, 2.2) * (uKind < 1.5 ? 0.22 : 0.11);\n" +
            "    rgb += uRimColor.rgb * topDome * (uDark > 0.5 ? 0.75 : 0.65);\n" +
            "\n" +
            "    if (uKind < 1.5) {\n" +
            "        float botLift = vy * vy * (uKind < 0.5 ? 0.14 : 0.10);\n" +
            "        rgb += uRimColor.rgb * botLift * (uDark > 0.5 ? 0.55 : 0.32);\n" +
            "    }\n" +
            "\n" +
            "    if (uDispersion > 0.01 && (uDark > 0.5 || uKind == 4.0)) {\n" +
            "        float diag = (p.x * p.y) / max(halfSize.x * halfSize.y, 1.0);\n" +
            "        float3 cyanTint = float3(0.42, 0.86, 1.00);\n" +
            "        float3 warmTint = float3(1.00, 0.62, 0.88);\n" +
            "        float3 spectrum = mix(cyanTint, warmTint, clamp(diag * 0.9 + 0.5, 0.0, 1.0));\n" +
            "        float dispMask = innerGlint * (0.45 + 0.55 * (keySpec + oppSpec));\n" +
            "        rgb = mix(rgb, spectrum, clamp(dispMask * uDispersion * 0.45, 0.0, 0.55));\n" +
            "        alpha = max(alpha, dispMask * uDispersion * 0.36);\n" +
            "    }\n" +
            "\n" +
            "    if (uGrain > 0.001) {\n" +
            "        float n = (hash12(floor(fragCoord * 0.75)) - 0.5) * uGrain;\n" +
            "        rgb += float3(n);\n" +
            "    }\n" +
            "\n" +
            "    if (uDark > 0.5 || uKind == 4.0) {\n" +
            "        float rimLight = (keySpec + oppSpec) * rimMask * uRimColor.a;\n" +
            "        float borderWeight = outerStroke * (uRimColor.a * (0.42 + 0.58 * (keySpec + oppSpec)));\n" +
            "        rgb = mix(rgb, uRimColor.rgb, clamp(rimLight * 0.55 + borderWeight * 0.85, 0.0, 0.95));\n" +
            "        alpha = clamp(alpha + borderWeight * 0.65 + rimLight * 0.25, 0.0, 0.96);\n" +
            "    } else {\n" +
            "        // Pure neutral achromatic light glass: white inner specular rim + subtle neutral grey\n" +
            "        // outer hairline & soft lower edge depth, with zero color tint on white backgrounds.\n" +
            "        float lowerBevel = (1.0 - smoothstep(0.6 * pxDp, 3.8 * pxDp, distIn)) * smoothstep(0.35, 0.95, vy);\n" +
            "        rgb = mix(rgb, float3(0.0, 0.0, 0.0), lowerBevel * 0.06);\n" +
            "        alpha = clamp(alpha + lowerBevel * 0.07, 0.0, 0.94);\n" +
            "\n" +
            "        float topSpecular = innerHighlight * (0.40 + 0.60 * (1.0 - vy)) * (0.55 + 0.45 * (keySpec + oppSpec));\n" +
            "        rgb = mix(rgb, float3(1.0, 1.0, 1.0), clamp(topSpecular * 0.90, 0.0, 0.95));\n" +
            "        alpha = clamp(alpha + topSpecular * 0.22, 0.0, 0.96);\n" +
            "\n" +
            "        float rimWeight = outerStroke * (0.18 + 0.14 * vy);\n" +
            "        rgb = mix(rgb, float3(0.0, 0.0, 0.0), clamp(rimWeight, 0.0, 0.32));\n" +
            "        alpha = max(alpha, outerStroke * (0.24 + 0.12 * vy));\n" +
            "    }\n" +
            "\n" +
            "    float finalA = alpha * edgeAA;\n" +
            "    rgb = clamp(rgb, float3(0.0), float3(1.0));\n" +
            "    return half4(rgb * finalA, finalA);\n" +
            "}\n";

    private GlassButtonStyler() {}

    private static final class ResolvedPreset {
        final int kind;
        final boolean dark;
        final float blurDp;
        final String hyperToken;
        final String signature;

        ResolvedPreset(int kind, boolean dark, float blurDp, String hyperToken, String signature) {
            this.kind = kind;
            this.dark = dark;
            this.blurDp = blurDp;
            this.hyperToken = hyperToken;
            this.signature = signature;
        }
    }

    static void apply(Button button, String buttonStyle, String foregroundTone) {
        if (button == null || buttonStyle == null || "native".equalsIgnoreCase(buttonStyle)) {
            return;
        }
        ResolvedPreset preset = resolvePreset(button.getContext(), buttonStyle, foregroundTone);
        Object existing = button.getTag(TAG_APPLIED_STYLE);
        if (preset.signature.equals(existing)) {
            return;
        }

        boolean hardwareGlassApplied = tryApplyHyperOsGlass(button, preset);
        boolean blurApplied = !hardwareGlassApplied && tryApplyHyperOsBlur(button, preset);

        button.setBackgroundTintList(null);
        button.setBackground(createGlassDrawable(
                button.getContext(), preset, hardwareGlassApplied, blurApplied, false));
        button.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w > 0 && h > 0) {
                    outline.setRoundRect(0, 0, w, h, h * 0.5f);
                }
            }
        });
        button.setClipToOutline(true);
        enableSmoothCorners(button);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);

        int textColor = resolveForegroundColor(preset);
        button.setTextColor(textColor);
        button.setTag(TAG_APPLIED_STYLE, preset.signature);
        button.invalidateOutline();
        button.invalidate();
        HookLog.i("applied glass button style=" + preset.signature
                + " hwGlass=" + hardwareGlassApplied
                + " hwBlur=" + blurApplied);
    }

    static void applyCloseButton(ImageView closeButton, String buttonStyle, String foregroundTone) {
        if (closeButton == null || buttonStyle == null || "native".equalsIgnoreCase(buttonStyle)) {
            return;
        }
        ResolvedPreset preset = resolvePreset(closeButton.getContext(), buttonStyle, foregroundTone);
        String sig = "close|" + preset.signature;
        Object existing = closeButton.getTag(TAG_APPLIED_STYLE);
        if (sig.equals(existing) && closeButton.getDrawable() instanceof CloseGlyphDrawable) {
            return;
        }

        boolean hardwareGlassApplied = tryApplyHyperOsGlass(closeButton, preset);
        boolean blurApplied = !hardwareGlassApplied && tryApplyHyperOsBlur(closeButton, preset);

        closeButton.setBackgroundTintList(null);
        closeButton.setImageTintList(null);
        closeButton.setBackground(createGlassDrawable(
                closeButton.getContext(), preset, hardwareGlassApplied, blurApplied, true));
        closeButton.setImageDrawable(new CloseGlyphDrawable(resolveForegroundColor(preset)));
        closeButton.setScaleType(ImageView.ScaleType.FIT_XY);
        closeButton.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w > 0 && h > 0) {
                    outline.setOval(0, 0, w, h);
                }
            }
        });
        closeButton.setClipToOutline(true);
        enableSmoothCorners(closeButton);
        closeButton.setStateListAnimator(null);
        closeButton.setElevation(0f);
        closeButton.setTranslationZ(0f);

        closeButton.setTag(TAG_APPLIED_STYLE, sig);
        closeButton.invalidateOutline();
        closeButton.invalidate();
        HookLog.i("applied glass close button style=" + preset.signature
                + " hwGlass=" + hardwareGlassApplied
                + " hwBlur=" + blurApplied);
    }

    private static int resolveForegroundColor(ResolvedPreset preset) {
        if (preset.kind == KIND_TINT_DARK) return 0xF5FFFFFF;
        if (preset.kind == KIND_TINT_PURPLE) return 0xFAFFFFFF;
        return preset.dark ? 0xF5FFFFFF : 0xEB14161A;
    }

    private static ResolvedPreset resolvePreset(Context context, String rawStyle, String foregroundTone) {
        String style = rawStyle == null ? "native" : rawStyle.trim().toLowerCase(Locale.US);
        boolean autoDark = resolveAutoDark(context, foregroundTone);

        int kind;
        boolean dark;
        switch (style) {
            case "liquid_light":
            case "glass_light":
                kind = KIND_LIQUID;
                dark = false;
                break;
            case "liquid_dark":
            case "glass_dark":
                kind = KIND_LIQUID;
                dark = true;
                break;
            case "soft_auto":
                kind = KIND_SOFT;
                dark = autoDark;
                break;
            case "soft_light":
                kind = KIND_SOFT;
                dark = false;
                break;
            case "soft_dark":
                kind = KIND_SOFT;
                dark = true;
                break;
            case "frosted_auto":
                kind = KIND_FROSTED;
                dark = autoDark;
                break;
            case "tint_dark":
                kind = KIND_TINT_DARK;
                dark = true;
                break;
            case "tint_purple":
                kind = KIND_TINT_PURPLE;
                dark = true;
                break;
            case "liquid_auto":
            case "glass_auto":
            default:
                kind = KIND_LIQUID;
                dark = autoDark;
                break;
        }

        float blurDp;
        String hyperToken;
        switch (kind) {
            case KIND_LIQUID:
                blurDp = 10f;
                hyperToken = dark ? "Glass_Common_Medium_Regular_Dark" : "Glass_Common_Small_Regular";
                break;
            case KIND_SOFT:
                blurDp = 22f;
                hyperToken = dark ? "Glass_Common_Medium_Regular_Dark" : "Glass_Bionic_Medium_Thin";
                break;
            case KIND_FROSTED:
                blurDp = 38f;
                hyperToken = "Glass_Common_Large_Regular";
                break;
            case KIND_TINT_DARK:
                blurDp = 24f;
                hyperToken = "Glass_Tint_Black_Regular";
                break;
            case KIND_TINT_PURPLE:
                blurDp = 20f;
                hyperToken = "Glass_Tint_Purple";
                break;
            default:
                blurDp = 16f;
                hyperToken = "Glass_Common_Small_Regular";
                break;
        }
        String sig = style + "|k=" + kind + "|" + (dark ? "dark" : "light");
        return new ResolvedPreset(kind, dark, blurDp, hyperToken, sig);
    }

    private static boolean resolveAutoDark(Context context, String foregroundTone) {
        if ("light".equalsIgnoreCase(foregroundTone)) return true;
        if ("dark".equalsIgnoreCase(foregroundTone)) return false;
        if (context == null) return false;
        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Builds the exact same glass surface Drawable (AGSL shader on API 33+, fallback below)
     * used on the real HyperOS PairingDialog so the in-app preview renders identically.
     */
    public static Drawable createSurfaceDrawable(
            Context context,
            String buttonStyle,
            String foregroundTone,
            boolean hasRealBlur,
            boolean circular
    ) {
        if (context == null || buttonStyle == null || "native".equalsIgnoreCase(buttonStyle)) {
            return null;
        }
        ResolvedPreset preset = resolvePreset(context, buttonStyle, foregroundTone);
        Drawable content = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            content = SoftGlassSurfaceDrawable.tryCreate(context, preset, hasRealBlur, circular);
        }
        if (content == null) {
            content = createFallbackDrawable(context, preset, hasRealBlur, false, circular);
        }
        return content;
    }

    private static Drawable createGlassDrawable(
            Context context,
            ResolvedPreset preset,
            boolean hardwareGlassApplied,
            boolean blurApplied,
            boolean circular
    ) {
        boolean hasRealBlur = hardwareGlassApplied || blurApplied;
        Drawable content = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            content = SoftGlassSurfaceDrawable.tryCreate(context, preset, hasRealBlur, circular);
        }
        if (content == null) {
            content = createFallbackDrawable(context, preset, hardwareGlassApplied, blurApplied, circular);
        }

        int rippleColor = preset.dark ? Color.argb(68, 255, 255, 255) : Color.argb(44, 0, 0, 0);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(circular ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!circular) {
            mask.setCornerRadius(dp(context, 999f));
        }
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private static Drawable createFallbackDrawable(
            Context context,
            ResolvedPreset preset,
            boolean hardwareGlassApplied,
            boolean blurApplied,
            boolean circular
    ) {
        float radius = dp(context, 999f);
        int strokeWidth = Math.max(1, Math.round(dp(context, 1.0f)));
        Palette p = paletteFor(preset, hardwareGlassApplied || blurApplied);
        int topHighlight = Color.argb(
                Math.round(Color.alpha(p.rimColor) * 0.45f),
                Color.red(p.rimColor),
                Color.green(p.rimColor),
                Color.blue(p.rimColor)
        );

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{p.topFill, p.bottomFill}
        );
        body.setShape(circular ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!circular) body.setCornerRadius(radius);
        int strokeColor = (!preset.dark && preset.kind != KIND_TINT_PURPLE)
                ? Color.argb(46, 0, 0, 0)
                : p.rimColor;
        body.setStroke(strokeWidth, strokeColor);

        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topHighlight, Color.TRANSPARENT, Color.TRANSPARENT}
        );
        gloss.setShape(circular ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!circular) gloss.setCornerRadius(radius);

        LayerDrawable content = new LayerDrawable(new Drawable[]{body, gloss});
        int inset = strokeWidth;
        content.setLayerInset(1, inset, inset, inset, inset);
        return content;
    }

    private static final class Palette {
        final int topFill;
        final int bottomFill;
        final int rimColor;
        final float edgeDp;
        final float dispersion;
        final float grain;

        Palette(int topFill, int bottomFill, int rimColor, float edgeDp, float dispersion, float grain) {
            this.topFill = topFill;
            this.bottomFill = bottomFill;
            this.rimColor = rimColor;
            this.edgeDp = edgeDp;
            this.dispersion = dispersion;
            this.grain = grain;
        }
    }

    private static Palette paletteFor(ResolvedPreset preset, boolean hasRealBlur) {
        float alphaBoost = hasRealBlur ? 1.0f : 1.42f;
        switch (preset.kind) {
            case KIND_LIQUID:
                if (preset.dark) {
                    return new Palette(
                            scaleAlpha(Color.argb(58, 36, 42, 54), alphaBoost),
                            scaleAlpha(Color.argb(36, 18, 22, 30), alphaBoost),
                            Color.argb(175, 255, 255, 255),
                            10.0f,
                            0.85f,
                            0.0f
                    );
                } else {
                    return new Palette(
                            scaleAlpha(Color.argb(122, 244, 244, 244), alphaBoost),
                            scaleAlpha(Color.argb(104, 228, 228, 228), alphaBoost),
                            Color.argb(230, 255, 255, 255),
                            10.0f,
                            0.65f,
                            0.0f
                    );
                }
            case KIND_SOFT:
                if (preset.dark) {
                    return new Palette(
                            scaleAlpha(Color.argb(92, 42, 46, 56), alphaBoost),
                            scaleAlpha(Color.argb(66, 22, 25, 33), alphaBoost),
                            Color.argb(150, 245, 248, 255),
                            13.0f,
                            0.0f,
                            0.0f
                    );
                } else {
                    return new Palette(
                            scaleAlpha(Color.argb(160, 244, 244, 244), alphaBoost),
                            scaleAlpha(Color.argb(142, 228, 228, 228), alphaBoost),
                            Color.argb(215, 255, 255, 255),
                            13.0f,
                            0.0f,
                            0.0f
                    );
                }
            case KIND_FROSTED:
                if (preset.dark) {
                    return new Palette(
                            scaleAlpha(Color.argb(136, 36, 40, 48), alphaBoost),
                            scaleAlpha(Color.argb(122, 24, 27, 34), alphaBoost),
                            Color.argb(115, 235, 240, 250),
                            6.0f,
                            0.0f,
                            0.045f
                    );
                } else {
                    return new Palette(
                            scaleAlpha(Color.argb(192, 242, 242, 242), alphaBoost),
                            scaleAlpha(Color.argb(180, 230, 230, 230), alphaBoost),
                            Color.argb(195, 255, 255, 255),
                            6.0f,
                            0.0f,
                            0.042f
                    );
                }
            case KIND_TINT_DARK:
                return new Palette(
                        scaleAlpha(Color.argb(156, 20, 22, 28), alphaBoost),
                        scaleAlpha(Color.argb(138, 10, 12, 16), alphaBoost),
                        Color.argb(145, 255, 255, 255),
                        11.0f,
                        0.15f,
                        0.015f
                );
            case KIND_TINT_PURPLE:
                return new Palette(
                        scaleAlpha(Color.argb(118, 124, 92, 220), alphaBoost),
                        scaleAlpha(Color.argb(88, 72, 54, 154), alphaBoost),
                        Color.argb(195, 232, 222, 255),
                        11.5f,
                        0.32f,
                        0.0f
                );
            default:
                return new Palette(
                        Color.argb(110, 255, 255, 255),
                        Color.argb(80, 240, 242, 248),
                        Color.argb(180, 255, 255, 255),
                        10.0f,
                        0.0f,
                        0.0f
                );
        }
    }

    private static int scaleAlpha(int color, float factor) {
        int a = Math.min(235, Math.max(0, Math.round(Color.alpha(color) * factor)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static boolean tryApplyHyperOsGlass(View view, ResolvedPreset preset) {
        try {
            ClassLoader loader = view.getClass().getClassLoader();
            Class<?> hyperUtils = Class.forName("miuix.core.util.HyperMaterialUtils", false, loader);
            boolean supported = Boolean.TRUE.equals(hyperUtils.getMethod("isSupportGlass").invoke(null));
            if (!supported) return false;

            Class<?> glassTokenClass = Class.forName("miuix.theme.token.GlassToken", false, loader);
            Object token;
            try {
                token = glassTokenClass.getField(preset.hyperToken).get(null);
            } catch (NoSuchFieldException ignored) {
                String fallbackName = preset.dark ? "Glass_Common_Medium_Regular_Dark" : "Glass_Common_Small_Regular";
                token = glassTokenClass.getField(fallbackName).get(null);
            }
            if (token == null) return false;

            float[] shaderParams = (float[]) glassTokenClass.getMethod("toShaderParams").invoke(token);
            if (shaderParams == null || shaderParams.length == 0) return false;

            int smallBlur = 24;
            int bigBlur = 240;
            try {
                Object blurRadiusObj = glassTokenClass.getField("blurRadius").get(token);
                if (blurRadiusObj != null) {
                    Field smallField = blurRadiusObj.getClass().getField("smallBlurRadius");
                    Field bigField = blurRadiusObj.getClass().getField("bigBlurRadius");
                    smallBlur = smallField.getInt(blurRadiusObj);
                    bigBlur = bigField.getInt(blurRadiusObj);
                }
            } catch (Throwable ignored) {}

            Class<?> glassConfigClass = Class.forName("miuix.core.util.MaterialConfig$GlassConfig", false, loader);
            Constructor<?> ctor = glassConfigClass.getConstructor(float[].class, int.class, int.class);
            Object glassConfig = ctor.newInstance(shaderParams, smallBlur, bigBlur);

            Method applyGlass = hyperUtils.getMethod("tryApplyContainerWithGlass", View.class, glassConfigClass);
            boolean applied = Boolean.TRUE.equals(applyGlass.invoke(null, view, glassConfig));
            if (applied && preset.dark) {
                applyBloomStroke(view, loader, true);
            }
            return applied;
        } catch (Throwable error) {
            HookLog.w("HyperOS hardware glass unavailable for view, falling back", error);
            return false;
        }
    }

    private static boolean tryApplyHyperOsBlur(View view, ResolvedPreset preset) {
        try {
            ClassLoader loader = view.getClass().getClassLoader();
            Class<?> blurUtils = Class.forName("miuix.core.util.MiuiBlurUtils", false, loader);
            int radiusPx = Math.round(dp(view.getContext(), preset.blurDp));
            Method setBgBlur = blurUtils.getMethod("setBackgroundBlur", View.class, int.class, boolean.class);
            boolean ok = Boolean.TRUE.equals(setBgBlur.invoke(null, view, radiusPx, true));
            if (ok && preset.dark && preset.kind != KIND_FROSTED) {
                applyBloomStroke(view, loader, true);
            }
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyBloomStroke(View view, ClassLoader loader, boolean darkGlass) {
        try {
            Class<?> strokeTokenClass = Class.forName("miuix.theme.token.BloomStrokeToken", false, loader);
            float[] stroke = (float[]) strokeTokenClass
                    .getField(darkGlass ? "Glass_Stroke_Small_Dark" : "Glass_Stroke_Small_Light")
                    .get(null);
            if (stroke == null) return;
            Class<?> strokeUtils = Class.forName("miuix.core.util.HyperBloomStrokeUtils", false, loader);
            Method setStroke = strokeUtils.getMethod("setBloomStrokeWithDp", View.class, float[].class);
            setStroke.invoke(null, view, stroke);
        } catch (Throwable ignored) {
        }
    }

    private static void enableSmoothCorners(View view) {
        try {
            ClassLoader loader = view.getClass().getClassLoader();
            Class<?> smooth = Class.forName("miuix.smooth.SmoothCornerHelper", false, loader);
            Method setEnable = smooth.getMethod("setViewSmoothCornerEnable", View.class, boolean.class);
            setEnable.invoke(null, view, true);
        } catch (Throwable ignored) {
        }
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class SoftGlassSurfaceDrawable extends Drawable {
        private final RuntimeShader shader;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ResolvedPreset preset;
        private final Palette palette;
        private final boolean hasRealBlur;
        private final boolean circular;
        private final float edgePx;
        private final float pxPerDp;

        private SoftGlassSurfaceDrawable(
                Context context,
                RuntimeShader shader,
                ResolvedPreset preset,
                Palette palette,
                boolean hasRealBlur,
                boolean circular
        ) {
            this.shader = shader;
            this.preset = preset;
            this.palette = palette;
            this.hasRealBlur = hasRealBlur;
            this.circular = circular;
            this.pxPerDp = Math.max(1f, context.getResources().getDisplayMetrics().density);
            this.edgePx = dp(context, circular ? Math.min(palette.edgeDp, 8.5f) : palette.edgeDp);
            this.paint.setShader(shader);
        }

        static SoftGlassSurfaceDrawable tryCreate(
                Context context,
                ResolvedPreset preset,
                boolean hasRealBlur,
                boolean circular
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null;
            try {
                RuntimeShader shader = new RuntimeShader(SOFTGLASS_SURFACE_AGSL);
                Palette palette = paletteFor(preset, hasRealBlur);
                return new SoftGlassSurfaceDrawable(context, shader, preset, palette, hasRealBlur, circular);
            } catch (Throwable error) {
                HookLog.w("SoftGlass AGSL shader creation failed; using fallback drawable", error);
                return null;
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

            float cornerR = circular ? (Math.min(w, h) * 0.5f) : (h * 0.5f);
            shader.setFloatUniform("uSize", (float) w, (float) h);
            shader.setFloatUniform("uCornerR", cornerR);
            shader.setFloatUniform("uKind", (float) preset.kind);
            shader.setFloatUniform("uDark", preset.dark ? 1.0f : 0.0f);
            shader.setFloatUniform("uHasBlur", hasRealBlur ? 1.0f : 0.0f);
            shader.setFloatUniform("uEdgePx", Math.min(edgePx, Math.min(w, h) * 0.45f));
            shader.setFloatUniform("uPxPerDp", pxPerDp);
            setColorUniform("uBodyTop", palette.topFill);
            setColorUniform("uBodyBottom", palette.bottomFill);
            setColorUniform("uRimColor", palette.rimColor);
            shader.setFloatUniform("uDispersion", palette.dispersion);
            shader.setFloatUniform("uGrain", palette.grain);

            canvas.save();
            canvas.translate(b.left, b.top);
            canvas.drawRect(0f, 0f, w, h, paint);
            canvas.restore();
        }

        private void setColorUniform(String name, int argb) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
            shader.setFloatUniform(
                    name,
                    Color.red(argb) / 255f,
                    Color.green(argb) / 255f,
                    Color.blue(argb) / 255f,
                    Color.alpha(argb) / 255f
            );
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Transparent-background 'X' glyph matching HyperOS miuix_appcompat_circle_closable_icon
     * (28x28 viewport, rounded caps) so circular glass material renders cleanly behind the glyph.
     */
    private static final class CloseGlyphDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseGlyphDrawable(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) return;
            float size = Math.min(w, h);
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float halfSpan = size * 0.145f;
            paint.setStrokeWidth(Math.max(2f, size * (2.25f / 28f)));
            canvas.drawLine(cx - halfSpan, cy - halfSpan, cx + halfSpan, cy + halfSpan, paint);
            canvas.drawLine(cx + halfSpan, cy - halfSpan, cx - halfSpan, cy + halfSpan, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
