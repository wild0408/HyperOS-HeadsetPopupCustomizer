package com.wild0408.headsetpopup.hook;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies in-place native customization directly on HyperOS PairingDialog
 * without replacing the system dialog window, animations, or state machine.
 */
final class NativePopupSession {
    static final int ANIM_PASSTHROUGH = 0;
    static final int ANIM_HANDLED_TRUE = 1;
    static final int ANIM_SKIP_FALSE = 2;

    static final String EXTRA_ACTIVE_TRIGGER_STAGE = "com.wild0408.headsetpopup.extra.ACTIVE_TRIGGER_STAGE";

    private static final String TAG_STAGE_MEDIA = "headsetpopup_stage_media";
    private static final String TAG_HEADSET_MEDIA = "headsetpopup_headset_media";
    private static final String TAG_CASE_MEDIA = "headsetpopup_case_media";
    private static final String TAG_UNIFIED_MEDIA = "headsetpopup_unified_media";

    private final Object controller;
    private final Object dialog;
    private View contentView;
    private final Activity activity;
    private final Runnable onDisposed;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean disposed;
    private boolean activeTriggerStageDispatched;
    private String resolvedDeviceId;
    private HookPopupConfigClient.Config config = HookPopupConfigClient.Config.EMPTY;
    private int currentConnectState = 1;

    private ViewGroup parentPanel;
    private PopupBackgroundLayer backgroundLayer;
    private View.OnLayoutChangeListener parentLayoutListener;
    private ImageView cachedCloseButton;
    private boolean blurPassThroughDisabled;

    private static Method sSetPassWindowBlurMethod;
    private static boolean sSetPassWindowBlurResolved;

    private PopupMediaView stageMediaView;
    private PopupMediaView headsetMediaView;
    private PopupMediaView caseMediaView;
    private PopupMediaView unifiedMediaView;

    NativePopupSession(
            Object controller,
            Object dialog,
            View contentView,
            Activity activity,
            Runnable onDisposed
    ) {
        this.controller = controller;
        this.dialog = dialog;
        this.contentView = contentView;
        this.activity = activity;
        this.onDisposed = onDisposed;
        reloadConfigIfNeeded(true);
    }

    boolean sameDialog(Object candidate) {
        return dialog == candidate;
    }

    boolean belongsTo(Activity candidate) {
        return candidate != null && activity == candidate;
    }

    boolean ownsResource(Object resource) {
        if (resource == null || controller == null) return false;
        Object localRes = FastConnectSessionRegistry.field(controller, "mLocalResource");
        return localRes == resource;
    }

    void updateContentView(View candidate) {
        if (candidate != null) {
            this.contentView = candidate;
        }
    }

    void scheduleAttach(String point) {
        if (disposed) return;
        runOnMain(() -> {
            if (disposed) return;
            reloadConfigIfNeeded(false);
            maybeDispatchActiveTriggerStage();
            if (!config.hasAnyEffect()) {
                HookLog.i(point + " no active customization; preserving stock PairingDialog");
                return;
            }
            ensureBackgroundLayer();
            applyForegroundTone();
            applyButtonMaterial();
            int state = readControllerConnectState();
            if (state > 0) {
                currentConnectState = state;
            }
            applyCurrentStageDecorations(currentConnectState, null);
        });
    }

    void onModifyView(View view, int connectState, int extraState, String originalTitle) {
        if (disposed) return;
        updateContentView(view);
        currentConnectState = connectState;
        runOnMain(() -> {
            if (disposed) return;
            reloadConfigIfNeeded(false);
            if (!config.hasAnyEffect()) return;
            ensureBackgroundLayer();
            applyCurrentStageDecorations(connectState, originalTitle);
            if (connectState != 3) {
                applyNonConnectedStageMedia(connectState);
            } else {
                applyConnectedVisualIfVisible();
            }
            applyForegroundTone();
            applyButtonMaterial();
            // Some handlers update buttons/blur right after modifyView returns.
            mainHandler.post(() -> {
                if (disposed) return;
                applyStageButtonText(currentConnectState);
                applyForegroundTone();
                applyButtonMaterial();
                syncBlurPassThrough();
            });
        });
    }

    void onConnectSuccessLayout(View view) {
        if (disposed) return;
        updateContentView(view);
        currentConnectState = 3;
        runOnMain(() -> {
            if (disposed) return;
            reloadConfigIfNeeded(false);
            if (!config.hasAnyEffect()) return;
            ensureBackgroundLayer();
            applyConnectedVisualIfVisible();
            applyCurrentStageDecorations(3, null);
            applyForegroundTone();
            applyButtonMaterial();
        });
    }

    void onConnectSuccessLayoutDetail(View view) {
        if (disposed) return;
        updateContentView(view);
        currentConnectState = 3;
        runOnMain(() -> {
            if (disposed) return;
            reloadConfigIfNeeded(false);
            if (!config.hasAnyEffect()) return;
            ensureBackgroundLayer();
            applyConnectedVisualIfVisible();
            applyStageButtonText(3);
            applyForegroundTone();
            applyButtonMaterial();
        });
    }

    int beforeStartAnimation(Object resource, ImageView imageView, int animType) {
        if (disposed) return ANIM_PASSTHROUGH;
        reloadConfigIfNeeded(false);
        if (!config.hasAnyEffect()) return ANIM_PASSTHROUGH;

        if (animType == 3) {
            HookPopupConfigClient.ConnectedConfig connected = config.connectedStage;
            if (connected.isSkipTransition()) {
                HookLog.i("stage 3 transition skipped by config; proceeding straight to battery card");
                stopNativeFreeMap(resource);
                removeStageMediaView();
                return ANIM_SKIP_FALSE;
            }
            if (connected.isCustomTransition()) {
                HookLog.i("stage 3 custom transition playing asset=" + connected.transitionMedia.assetName);
                stopNativeFreeMap(resource);
                ViewGroup container = findImageFindHeadsetLayout(imageView);
                if (container == null) return ANIM_PASSTHROUGH;
                hideNativeFindVisuals(container, imageView);
                mountStageMedia(container, connected.transitionMedia, true, false, () -> {
                    if (disposed) return;
                    HookLog.i("stage 3 custom transition finished; firing native completion callback");
                    removeStageMediaView();
                    notifyTransitionCompleted(resource);
                });
                return ANIM_HANDLED_TRUE;
            }
            removeStageMediaView();
            restoreNativeFindVisuals(findImageFindHeadsetLayout(imageView));
            return ANIM_PASSTHROUGH;
        }

        int stageState = resolveConnectStateForAnimType(animType);
        HookPopupConfigClient.StageConfig stage = config.stageForConnectState(stageState);
        if (stage.enabled && stage.media.isConfigured()) {
            HookLog.i("intercepted startAnimation animType=" + animType
                    + " state=" + stageState
                    + " asset=" + stage.media.assetName);
            stopNativeFreeMap(resource);
            ViewGroup container = findImageFindHeadsetLayout(imageView);
            if (container == null) return ANIM_PASSTHROUGH;
            hideNativeFindVisuals(container, imageView);
            mountStageMedia(container, stage.media, false, stage.media.loop, null);
            return ANIM_HANDLED_TRUE;
        }

        // Stage not customized -> restore native visuals and let stock play.
        removeStageMediaView();
        restoreNativeFindVisuals(findImageFindHeadsetLayout(imageView));
        return ANIM_PASSTHROUGH;
    }

    boolean shouldSuppressCheckAnimationStop(int animType) {
        if (disposed || !config.hasAnyEffect()) return false;
        if (animType == 3) {
            return config.connectedStage.isCustomTransition() || config.connectedStage.isSkipTransition();
        }
        int state = resolveConnectStateForAnimType(animType);
        HookPopupConfigClient.StageConfig stage = config.stageForConnectState(state);
        return stage.enabled && stage.media.isConfigured();
    }

    void dispose(String reason) {
        if (disposed) return;
        disposed = true;
        HookLog.i("disposing native popup session reason=" + reason);
        mainHandler.removeCallbacksAndMessages(null);
        Runnable cleanup = () -> {
            if (parentPanel != null) {
                FastConnectSessionRegistry.unregisterBackgroundLayer(parentPanel);
                if (parentLayoutListener != null) {
                    parentPanel.removeOnLayoutChangeListener(parentLayoutListener);
                    parentLayoutListener = null;
                }
            }
            if (backgroundLayer != null) {
                backgroundLayer.release();
                detachFromParent(backgroundLayer);
                backgroundLayer = null;
            }
            removeStageMediaView();
            releaseViewRef(headsetMediaView);
            headsetMediaView = null;
            releaseViewRef(caseMediaView);
            caseMediaView = null;
            releaseViewRef(unifiedMediaView);
            unifiedMediaView = null;
            parentPanel = null;
            cachedCloseButton = null;
            blurPassThroughDisabled = false;
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
        } else {
            mainHandler.post(cleanup);
        }
        if (onDisposed != null) {
            onDisposed.run();
        }
    }

    private void reloadConfigIfNeeded(boolean force) {
        String deviceId = resolveDeviceId();
        if (!force && deviceId.equals(resolvedDeviceId)) {
            return;
        }
        resolvedDeviceId = deviceId;
        config = HookPopupConfigClient.loadForDevice(activity, deviceId);
    }

    private String resolveDeviceId() {
        Object id = FastConnectSessionRegistry.field(controller, "mDeviceId");
        if (id instanceof String && !((String) id).trim().isEmpty()) {
            return ((String) id).trim().toUpperCase();
        }
        Object record = FastConnectSessionRegistry.field(controller, "mCloudResRecord");
        Object recordId = FastConnectSessionRegistry.field(record, "mDeviceId");
        if (recordId instanceof String && !((String) recordId).trim().isEmpty()) {
            return ((String) recordId).trim().toUpperCase();
        }
        Object headsetInfo = FastConnectSessionRegistry.field(controller, "mHeadsetInfo");
        Object infoId = invoke(headsetInfo, "getDeviceId");
        if (infoId instanceof String && !((String) infoId).trim().isEmpty()) {
            return ((String) infoId).trim().toUpperCase();
        }
        return "";
    }

    private String resolveDeviceName(String fallback) {
        Object name = FastConnectSessionRegistry.field(controller, "mDeviceNameForDialog");
        if (name instanceof String && !((String) name).trim().isEmpty()) {
            return ((String) name).trim();
        }
        return fallback == null ? "" : fallback;
    }

    private int readControllerConnectState() {
        Object value = FastConnectSessionRegistry.field(controller, "mDialogConnectState");
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return currentConnectState;
    }

    private int resolveConnectStateForAnimType(int animType) {
        switch (animType) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 4:
                int state = readControllerConnectState();
                return state == 5 ? 5 : 4;
            default:
                return readControllerConnectState();
        }
    }

    private void maybeDispatchActiveTriggerStage() {
        if (activeTriggerStageDispatched || activity == null || controller == null) return;
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String stage = intent.getStringExtra(EXTRA_ACTIVE_TRIGGER_STAGE);
        if (stage == null || stage.trim().isEmpty()) return;

        final int what;
        if ("connecting".equalsIgnoreCase(stage)) {
            what = 4;
        } else if ("fail".equalsIgnoreCase(stage)) {
            what = 3;
        } else {
            return;
        }
        activeTriggerStageDispatched = true;
        mainHandler.postDelayed(() -> {
            if (disposed) return;
            try {
                Object handler = FastConnectSessionRegistry.field(controller, "mMainHandler");
                if (handler instanceof Handler) {
                    HookLog.i("dispatching active trigger stage=" + stage + " via mMainHandler what=" + what);
                    ((Handler) handler).sendEmptyMessage(what);
                }
            } catch (Throwable error) {
                HookLog.w("failed to dispatch active trigger stage=" + stage, error);
            }
        }, 120L);
    }

    private void ensureBackgroundLayer() {
        HookPopupConfigClient.BackgroundConfig bg = config.background;
        ViewGroup panel = resolveParentPanel();
        if (panel == null) return;
        parentPanel = panel;

        if (!bg.isActive()) {
            if (backgroundLayer != null) {
                FastConnectSessionRegistry.unregisterBackgroundLayer(panel);
                backgroundLayer.release();
                detachFromParent(backgroundLayer);
                backgroundLayer = null;
            }
            return;
        }

        boolean newlyAttached = false;
        if (backgroundLayer == null || backgroundLayer.getParent() != panel) {
            if (backgroundLayer != null) {
                backgroundLayer.release();
                detachFromParent(backgroundLayer);
            }
            newlyAttached = true;
            blurPassThroughDisabled = false;
            backgroundLayer = new PopupBackgroundLayer(panel.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0);
            panel.addView(backgroundLayer, lp);
            panel.setClipToPadding(false);
            panel.setClipToOutline(true);
            enableChildrenDrawingOrder(panel);
            FastConnectSessionRegistry.registerBackgroundLayer(panel, backgroundLayer);

            if (parentLayoutListener != null) {
                panel.removeOnLayoutChangeListener(parentLayoutListener);
            }
            parentLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!disposed && backgroundLayer != null) {
                    if (v instanceof ViewGroup && ((ViewGroup) v).getClipToPadding()) {
                        ((ViewGroup) v).setClipToPadding(false);
                    }
                    if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                        backgroundLayer.syncToParentBounds();
                    }
                }
            };
            panel.addOnLayoutChangeListener(parentLayoutListener);
            HookLog.i("attached PopupBackgroundLayer to " + panel.getClass().getName()
                    + " asset=" + bg.media.assetName + " type=" + bg.media.type);
        }

        backgroundLayer.bind(bg);
        if (panel.getClipToPadding()) {
            panel.setClipToPadding(false);
        }
        if (newlyAttached) {
            panel.invalidate();
            syncBlurPassThrough(true);
            // AlertController.setupMaterial() posts a runnable that enables blur shortly after show().
            mainHandler.postDelayed(() -> {
                if (disposed) return;
                if (parentPanel != null && backgroundLayer != null) {
                    if (parentPanel.getClipToPadding()) {
                        parentPanel.setClipToPadding(false);
                    }
                    backgroundLayer.syncToParentBounds();
                }
                syncBlurPassThrough(true);
                applyButtonMaterial();
            }, 60L);
            mainHandler.postDelayed(() -> {
                if (disposed) return;
                if (parentPanel != null && backgroundLayer != null) {
                    if (parentPanel.getClipToPadding()) {
                        parentPanel.setClipToPadding(false);
                    }
                    backgroundLayer.syncToParentBounds();
                }
                syncBlurPassThrough(true);
                applyButtonMaterial();
            }, 220L);
        } else {
            syncBlurPassThrough(false);
        }
    }

    private void syncBlurPassThrough() {
        syncBlurPassThrough(false);
    }

    private void syncBlurPassThrough(boolean force) {
        if (disposed || parentPanel == null || !config.background.isActive()) return;
        if (config.background.alpha < 0.98f) return;
        if (!force && blurPassThroughDisabled) return;
        try {
            if (!sSetPassWindowBlurResolved) {
                sSetPassWindowBlurResolved = true;
                ClassLoader loader = parentPanel.getClass().getClassLoader();
                Class<?> blurUtils = Class.forName("miuix.core.util.MiuiBlurUtils", false, loader);
                sSetPassWindowBlurMethod = blurUtils.getMethod("setPassWindowBlurEnabled", View.class, boolean.class);
            }
            if (sSetPassWindowBlurMethod != null) {
                sSetPassWindowBlurMethod.invoke(null, parentPanel, false);
                blurPassThroughDisabled = true;
            }
        } catch (Throwable ignored) {
            // Best-effort; some builds may not use MiuiBlurUtils.
        }
    }

    private void applyCurrentStageDecorations(int connectState, String originalTitle) {
        if (connectState == 3) {
            HookPopupConfigClient.ConnectedConfig connected = config.connectedStage;
            if (connected.enabled) {
                if (!connected.title.isEmpty()) {
                    applyTitleText(formatTemplate(connected.title, originalTitle));
                }
                if (!connected.buttonText.isEmpty()) {
                    applyVisibleButtonText(connected.buttonText);
                }
            }
            return;
        }

        HookPopupConfigClient.StageConfig stage = config.stageForConnectState(connectState);
        if (!stage.enabled) return;

        if (!stage.title.isEmpty()) {
            applyTitleText(formatTemplate(stage.title, originalTitle));
        }
        if (!stage.subtitle.isEmpty()) {
            applySubtitleText(connectState, formatTemplate(stage.subtitle, originalTitle));
        }
        if (!stage.buttonText.isEmpty()) {
            applyVisibleButtonText(stage.buttonText);
        }
    }

    private void applyStageButtonText(int connectState) {
        if (connectState == 3) {
            if (config.connectedStage.enabled && !config.connectedStage.buttonText.isEmpty()) {
                applyVisibleButtonText(config.connectedStage.buttonText);
            }
            return;
        }
        HookPopupConfigClient.StageConfig stage = config.stageForConnectState(connectState);
        if (stage.enabled && !stage.buttonText.isEmpty()) {
            applyVisibleButtonText(stage.buttonText);
        }
    }

    private String formatTemplate(String template, String fallbackDeviceName) {
        String deviceName = resolveDeviceName(fallbackDeviceName);
        return template.replace("{device}", deviceName);
    }

    private void applyTitleText(String text) {
        TextView titleView = getPairingTextView("getTitleView");
        if (titleView != null && !text.contentEquals(titleView.getText())) {
            titleView.setText(text);
        }
        View legacyTitle = findByEntryName(contentView, "textTitle");
        if (legacyTitle instanceof TextView && legacyTitle.getVisibility() == View.VISIBLE) {
            TextView legacyTv = (TextView) legacyTitle;
            if (!text.contentEquals(legacyTv.getText())) {
                legacyTv.setText(text);
            }
        }
    }

    private void applySubtitleText(int connectState, String text) {
        if (connectState == 2 || connectState == 5) {
            TextView feedback = getPairingTextView("getFeedbackMessageView");
            if (feedback != null && !text.contentEquals(feedback.getText())) {
                feedback.setText(text);
            }
            View legacyChars = findByEntryName(contentView, "textCharacters");
            if (legacyChars instanceof TextView) {
                TextView legacyTv = (TextView) legacyChars;
                if (!text.contentEquals(legacyTv.getText())) {
                    legacyTv.setText(text);
                }
            }
            return;
        }
        // Stage 1 (FIND) and Stage 4 (FAIL) keep the button panel visible;
        // PairingDialog.getMessageView() sits directly under the title without hiding buttons.
        TextView messageView = getPairingTextView("getMessageView");
        if (messageView != null) {
            if (!text.contentEquals(messageView.getText())) {
                messageView.setText(text);
            }
            if (messageView.getVisibility() != View.VISIBLE) {
                messageView.setVisibility(View.VISIBLE);
            }
            return;
        }
        View legacyLayout = findByEntryName(contentView, "textConnectingLayout");
        View legacyChars = findByEntryName(contentView, "textCharacters");
        if (legacyLayout != null && legacyChars instanceof TextView) {
            if (legacyLayout.getVisibility() != View.VISIBLE) {
                legacyLayout.setVisibility(View.VISIBLE);
            }
            TextView legacyTv = (TextView) legacyChars;
            if (!text.contentEquals(legacyTv.getText())) {
                legacyTv.setText(text);
            }
        }
    }

    private void applyVisibleButtonText(String text) {
        List<Button> visibleButtons = new ArrayList<>();
        int[] whichList = new int[]{
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
        };
        for (int which : whichList) {
            Object btn = invokeWithInt(dialog, "getButton", which);
            if (btn instanceof Button && ((Button) btn).getVisibility() == View.VISIBLE) {
                visibleButtons.add((Button) btn);
            }
        }
        if (!visibleButtons.isEmpty()) {
            Button primary = visibleButtons.get(0);
            if (!text.contentEquals(primary.getText())) {
                primary.setText(text);
            }
        }
    }

    private void applyButtonMaterial() {
        if (disposed || !config.hasGlassButtons()) return;
        int[] whichList = new int[]{
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
        };
        for (int which : whichList) {
            Object btn = invokeWithInt(dialog, "getButton", which);
            if (btn instanceof Button) {
                GlassButtonStyler.apply((Button) btn, config.buttonStyle, config.background.foregroundTone);
            }
        }
        ImageView closeBtn = findPairingClosableView();
        if (closeBtn != null) {
            GlassButtonStyler.applyCloseButton(closeBtn, config.buttonStyle, config.background.foregroundTone);
        }
    }

    private ImageView findPairingClosableView() {
        if (cachedCloseButton != null && cachedCloseButton.isAttachedToWindow()) {
            return cachedCloseButton;
        }
        View root = parentPanel != null ? parentPanel : resolveParentPanel();
        View found = findByEntryName(root, "pairingClosable");
        if (found instanceof ImageView) {
            cachedCloseButton = (ImageView) found;
            return cachedCloseButton;
        }
        Object windowObj = invoke(dialog, "getWindow");
        if (windowObj instanceof Window) {
            View decor = ((Window) windowObj).getDecorView();
            View byDecor = findByEntryName(decor, "pairingClosable");
            if (byDecor instanceof ImageView) {
                cachedCloseButton = (ImageView) byDecor;
                return cachedCloseButton;
            }
        }
        return null;
    }

    private void applyNonConnectedStageMedia(int connectState) {
        HookPopupConfigClient.StageConfig stage = config.stageForConnectState(connectState);
        ViewGroup container = findImageFindHeadsetLayout(null);
        if (container == null) return;

        if (stage.enabled && stage.media.isConfigured()) {
            View nativeImg = findByEntryName(container, "imageViewFindHeadset");
            hideNativeFindVisuals(container, nativeImg);
            mountStageMedia(container, stage.media, false, stage.media.loop, null);
        } else {
            removeStageMediaView();
            restoreNativeFindVisuals(container);
        }
    }

    private void applyConnectedVisualIfVisible() {
        View groupSuccess = findByEntryName(contentView, "groupConnectSuccess");
        if (groupSuccess == null || groupSuccess.getVisibility() != View.VISIBLE) {
            return;
        }
        removeStageMediaView();
        HookPopupConfigClient.ConnectedConfig connected = config.connectedStage;
        if (!connected.enabled) {
            restoreSeparateNativeViews();
            return;
        }

        ViewGroup boxContainer = asViewGroup(findByEntryName(groupSuccess, "imageViewHeadsetBox"));
        View headsetLayout = findByEntryName(groupSuccess, "imageViewHeadsetLayout");
        View boxLayout = findByEntryName(groupSuccess, "imageViewBoxLayout");
        View headsetImage = findByEntryName(groupSuccess, "imageViewHeadset");
        View boxImage = findByEntryName(groupSuccess, "imageViewBox");

        if (connected.isUnifiedVisual()) {
            // Clean up any separate overlays first.
            releaseViewRef(headsetMediaView);
            headsetMediaView = null;
            releaseViewRef(caseMediaView);
            caseMediaView = null;

            if (boxContainer != null) {
                if (headsetLayout != null) headsetLayout.setVisibility(View.INVISIBLE);
                if (boxLayout != null && boxLayout.getVisibility() != View.GONE) {
                    boxLayout.setVisibility(View.INVISIBLE);
                }
                boolean hasCaseColumn = boxLayout != null && boxLayout.getVisibility() != View.GONE;
                int targetW = dp(boxContainer, hasCaseColumn ? 280 : 160);
                int targetH = dp(boxContainer, 128);
                if (unifiedMediaView == null || unifiedMediaView.getParent() != boxContainer) {
                    releaseViewRef(unifiedMediaView);
                    unifiedMediaView = new PopupMediaView(boxContainer.getContext());
                    unifiedMediaView.setTag(TAG_UNIFIED_MEDIA);
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(targetW, targetH);
                    lp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                    boxContainer.addView(unifiedMediaView, lp);
                } else {
                    ViewGroup.LayoutParams lp = unifiedMediaView.getLayoutParams();
                    if (lp != null && (lp.width != targetW || lp.height != targetH)) {
                        lp.width = targetW;
                        lp.height = targetH;
                        unifiedMediaView.setLayoutParams(lp);
                    }
                }
                unifiedMediaView.bind(connected.unifiedMedia, false, connected.unifiedMedia.loop, null);
                HookLog.i("applied stage 3 unified visual asset=" + connected.unifiedMedia.assetName);
            } else if (headsetImage != null && headsetImage.getParent() instanceof ViewGroup) {
                // Fallback for non-TWS single-image layouts without imageViewHeadsetBox.
                headsetImage.setVisibility(View.INVISIBLE);
                unifiedMediaView = mountSlotMedia(
                        (ViewGroup) headsetImage.getParent(),
                        unifiedMediaView,
                        TAG_UNIFIED_MEDIA,
                        headsetImage,
                        connected.unifiedMedia
                );
            }
            return;
        }

        // Separate mode (or unified with no media -> fall back cleanly to native).
        releaseViewRef(unifiedMediaView);
        unifiedMediaView = null;
        if (headsetLayout != null && headsetLayout.getVisibility() == View.INVISIBLE) {
            headsetLayout.setVisibility(View.VISIBLE);
        }
        if (boxLayout != null && boxLayout.getVisibility() == View.INVISIBLE) {
            boxLayout.setVisibility(View.VISIBLE);
        }

        if (connected.isSeparateVisual() && connected.headsetMedia.isConfigured()
                && headsetImage != null && headsetImage.getParent() instanceof ViewGroup) {
            headsetImage.setVisibility(View.INVISIBLE);
            headsetMediaView = mountSlotMedia(
                    (ViewGroup) headsetImage.getParent(),
                    headsetMediaView,
                    TAG_HEADSET_MEDIA,
                    headsetImage,
                    connected.headsetMedia
            );
            HookLog.i("applied stage 3 separate headset visual asset=" + connected.headsetMedia.assetName);
        } else {
            releaseViewRef(headsetMediaView);
            headsetMediaView = null;
            if (headsetImage != null && headsetImage.getVisibility() == View.INVISIBLE) {
                headsetImage.setVisibility(View.VISIBLE);
            }
        }

        if (connected.isSeparateVisual() && connected.caseMedia.isConfigured()
                && boxImage != null && boxImage.getParent() instanceof ViewGroup) {
            boxImage.setVisibility(View.INVISIBLE);
            caseMediaView = mountSlotMedia(
                    (ViewGroup) boxImage.getParent(),
                    caseMediaView,
                    TAG_CASE_MEDIA,
                    boxImage,
                    connected.caseMedia
            );
            HookLog.i("applied stage 3 separate case visual asset=" + connected.caseMedia.assetName);
        } else {
            releaseViewRef(caseMediaView);
            caseMediaView = null;
            if (boxImage != null && boxImage.getVisibility() == View.INVISIBLE) {
                boxImage.setVisibility(View.VISIBLE);
            }
        }
    }

    private void restoreSeparateNativeViews() {
        releaseViewRef(unifiedMediaView);
        unifiedMediaView = null;
        releaseViewRef(headsetMediaView);
        headsetMediaView = null;
        releaseViewRef(caseMediaView);
        caseMediaView = null;
        View groupSuccess = findByEntryName(contentView, "groupConnectSuccess");
        if (groupSuccess == null) return;
        for (String id : new String[]{"imageViewHeadsetLayout", "imageViewBoxLayout", "imageViewHeadset", "imageViewBox"}) {
            View v = findByEntryName(groupSuccess, id);
            if (v != null && v.getVisibility() == View.INVISIBLE) {
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    private PopupMediaView mountSlotMedia(
            ViewGroup container,
            PopupMediaView existing,
            String tag,
            View referenceView,
            HookPopupConfigClient.MediaSpec spec
    ) {
        PopupMediaView target = existing;
        if (target == null || target.getParent() != container) {
            releaseViewRef(target);
            target = new PopupMediaView(container.getContext());
            target.setTag(tag);
            int width = referenceView.getLayoutParams() != null && referenceView.getLayoutParams().width > 0
                    ? referenceView.getLayoutParams().width
                    : dp(container, 128);
            int height = referenceView.getLayoutParams() != null && referenceView.getLayoutParams().height > 0
                    ? referenceView.getLayoutParams().height
                    : dp(container, 128);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            container.addView(target, lp);
        }
        target.bind(spec, false, spec.loop, null);
        return target;
    }

    private void mountStageMedia(
            ViewGroup container,
            HookPopupConfigClient.MediaSpec spec,
            boolean forceLoopOverride,
            boolean forcedLoop,
            Runnable onOneShotComplete
    ) {
        if (stageMediaView == null || stageMediaView.getParent() != container) {
            removeStageMediaView();
            stageMediaView = new PopupMediaView(container.getContext());
            stageMediaView.setTag(TAG_STAGE_MEDIA);
            int size = dp(container, 200);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
            lp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            container.addView(stageMediaView, lp);
        }
        stageMediaView.setVisibility(View.VISIBLE);
        stageMediaView.bind(spec, forceLoopOverride, forcedLoop, onOneShotComplete);
    }

    private void removeStageMediaView() {
        if (stageMediaView != null) {
            releaseViewRef(stageMediaView);
            stageMediaView = null;
        }
    }

    private void hideNativeFindVisuals(ViewGroup container, View explicitImageView) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (TAG_STAGE_MEDIA.equals(child.getTag())) continue;
            child.setVisibility(View.INVISIBLE);
        }
        if (explicitImageView != null && !TAG_STAGE_MEDIA.equals(explicitImageView.getTag())) {
            explicitImageView.setVisibility(View.INVISIBLE);
        }
    }

    private void restoreNativeFindVisuals(ViewGroup container) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (TAG_STAGE_MEDIA.equals(child.getTag())) continue;
            String entry = entryNameOf(child);
            if ("imageViewFindHeadsetBackGround".equals(entry)) continue;
            if (child.getVisibility() == View.INVISIBLE) {
                child.setVisibility(View.VISIBLE);
            }
        }
    }

    private void stopNativeFreeMap(Object resource) {
        if (resource == null) return;
        try {
            Method setVis = findMethod(resource.getClass(), "setFreeMapViewVisibility", int.class);
            if (setVis != null) {
                setVis.setAccessible(true);
                setVis.invoke(resource, View.INVISIBLE);
            }
        } catch (Throwable ignored) {}
        try {
            Object fm = FastConnectSessionRegistry.field(resource, "mFreeMapView");
            if (fm instanceof View) {
                ((View) fm).setVisibility(View.INVISIBLE);
                Method pause = findMethod(fm.getClass(), "pause");
                if (pause != null) {
                    pause.setAccessible(true);
                    pause.invoke(fm);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void notifyTransitionCompleted(Object resource) {
        if (resource == null) return;
        try {
            Method setAlpha = findMethod(resource.getClass(), "setAlphaAnimationState", int.class, ViewGroup.class);
            if (setAlpha != null) {
                setAlpha.setAccessible(true);
                setAlpha.invoke(resource, 2, null);
            }
        } catch (Throwable error) {
            HookLog.w("setAlphaAnimationState(2, null) failed", error);
        }
        try {
            Object callback = FastConnectSessionRegistry.field(resource, "mAnimationCallBack");
            if (callback != null) {
                Method finish = findMethod(callback.getClass(), "onAnimationRunFinish", int.class);
                if (finish != null) {
                    finish.setAccessible(true);
                    finish.invoke(callback, 3);
                }
            }
        } catch (Throwable error) {
            HookLog.w("onAnimationRunFinish(3) failed", error);
        }
    }

    private void applyForegroundTone() {
        if (!config.background.isActive()) return;
        String tone = config.background.foregroundTone;
        if ("auto".equalsIgnoreCase(tone)) return;

        int primaryColor;
        int secondaryColor;
        if ("light".equalsIgnoreCase(tone)) {
            primaryColor = 0xFFFFFFFF;
            secondaryColor = 0xD9FFFFFF;
        } else if ("dark".equalsIgnoreCase(tone)) {
            primaryColor = 0xE6000000;
            secondaryColor = 0xA6000000;
        } else {
            return;
        }

        TextView title = getPairingTextView("getTitleView");
        if (title != null && title.getCurrentTextColor() != primaryColor) title.setTextColor(primaryColor);
        TextView message = getPairingTextView("getMessageView");
        if (message != null && message.getCurrentTextColor() != secondaryColor) message.setTextColor(secondaryColor);
        TextView feedback = getPairingTextView("getFeedbackMessageView");
        if (feedback != null && feedback.getCurrentTextColor() != secondaryColor) feedback.setTextColor(secondaryColor);

        String[] primaryIds = new String[]{
                "textTitle", "textCharacters",
                "textViewHeadsetLBatteryPercent", "textViewHeadsetRBatteryPercent",
                "textViewBoxBatteryPercent", "textViewHeadsetBatteryPercent"
        };
        for (String id : primaryIds) {
            View v = findByEntryName(contentView, id);
            if (v instanceof TextView && ((TextView) v).getCurrentTextColor() != primaryColor) {
                ((TextView) v).setTextColor(primaryColor);
            }
        }
        String[] secondaryIds = new String[]{
                "textViewHeadsetL", "textViewHeadsetR", "textViewBox", "textViewHeadset"
        };
        for (String id : secondaryIds) {
            View v = findByEntryName(contentView, id);
            if (v instanceof TextView && ((TextView) v).getCurrentTextColor() != secondaryColor) {
                ((TextView) v).setTextColor(secondaryColor);
            }
        }
    }

    private ViewGroup resolveParentPanel() {
        if (parentPanel != null && parentPanel.isAttachedToWindow()) {
            return parentPanel;
        }
        Object fieldPanel = FastConnectSessionRegistry.field(dialog, "mDialogParentPanel");
        if (fieldPanel instanceof ViewGroup) {
            return (ViewGroup) fieldPanel;
        }
        Object alert = FastConnectSessionRegistry.field(dialog, "mAlert");
        Object alertPanel = FastConnectSessionRegistry.field(alert, "mParentPanel");
        if (alertPanel instanceof ViewGroup) {
            return (ViewGroup) alertPanel;
        }
        Object windowObj = invoke(dialog, "getWindow");
        if (windowObj instanceof Window) {
            View decor = ((Window) windowObj).getDecorView();
            View byName = findByEntryName(decor, "parentPanel");
            if (byName instanceof ViewGroup) {
                return (ViewGroup) byName;
            }
        }
        return null;
    }

    private ViewGroup findImageFindHeadsetLayout(View hint) {
        if (hint != null) {
            View cur = hint;
            while (cur != null) {
                if ("imageFindHeadsetLayout".equals(entryNameOf(cur)) && cur instanceof ViewGroup) {
                    return (ViewGroup) cur;
                }
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
            }
        }
        View found = findByEntryName(contentView, "imageFindHeadsetLayout");
        return found instanceof ViewGroup ? (ViewGroup) found : null;
    }

    private TextView getPairingTextView(String methodName) {
        Object result = invoke(dialog, methodName);
        return result instanceof TextView ? (TextView) result : null;
    }

    private static void enableChildrenDrawingOrder(ViewGroup group) {
        try {
            Method setOrder = ViewGroup.class.getDeclaredMethod("setChildrenDrawingOrderEnabled", boolean.class);
            setOrder.setAccessible(true);
            setOrder.invoke(group, true);
        } catch (Throwable error) {
            HookLog.w("setChildrenDrawingOrderEnabled failed", error);
        }
    }

    private static void releaseViewRef(PopupMediaView view) {
        if (view == null) return;
        view.release();
        detachFromParent(view);
    }

    private static void detachFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static ViewGroup asViewGroup(View view) {
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private static int dp(View view, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                view.getResources().getDisplayMetrics()
        ));
    }

    private static View findByEntryName(View root, String targetName) {
        if (root == null || targetName == null) return null;
        if (targetName.equals(entryNameOf(root))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findByEntryName(group.getChildAt(i), targetName);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static String entryNameOf(View view) {
        if (view == null || view.getId() == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
    }

    private static Object invoke(Object receiver, String methodName) {
        if (receiver == null) return null;
        try {
            Method method = findMethod(receiver.getClass(), methodName);
            if (method == null) return null;
            method.setAccessible(true);
            return method.invoke(receiver);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeWithInt(Object receiver, String methodName, int arg) {
        if (receiver == null) return null;
        try {
            Method method = findMethod(receiver.getClass(), methodName, int.class);
            if (method == null) return null;
            method.setAccessible(true);
            return method.invoke(receiver, arg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        for (Class<?> cur = type; cur != null; cur = cur.getSuperclass()) {
            try {
                return cur.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }
}
