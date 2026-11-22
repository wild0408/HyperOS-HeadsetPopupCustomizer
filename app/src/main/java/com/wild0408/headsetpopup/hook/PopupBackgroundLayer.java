package com.wild0408.headsetpopup.hook;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Zero-flow background layer attached as the last child of DialogParentPanel2.
 * Contributes (0, 0) to LinearLayout measurement while restoring full parent bounds
 * during onLayout() without re-measuring or resizing unchanged media children so
 * parent relayout passes during stage switches never interrupt video/GIF playback.
 */
final class PopupBackgroundLayer extends FrameLayout {
    private final PopupMediaView mediaView;
    private final View scrimView;
    private boolean layingOutToParent;
    private String boundConfigSignature;
    private int lastScrimColor = Color.TRANSPARENT;

    PopupBackgroundLayer(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        mediaView = new PopupMediaView(context);
        addView(mediaView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrimView = new View(context);
        scrimView.setClickable(false);
        scrimView.setFocusable(false);
        scrimView.setBackgroundColor(Color.TRANSPARENT);
        addView(scrimView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void bind(HookPopupConfigClient.BackgroundConfig config) {
        if (config == null || !config.isActive()) {
            boundConfigSignature = null;
            release();
            setVisibility(GONE);
            return;
        }
        String sig = config.media.signature(false, true)
                + "|a=" + Math.round(config.alpha * 1000f)
                + "|s=" + Math.round(config.scrimAlpha * 1000f);
        if (sig.equals(boundConfigSignature) && getVisibility() == VISIBLE) {
            syncToParentBounds();
            return;
        }
        boundConfigSignature = sig;
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        if (Math.abs(getAlpha() - config.alpha) > 0.001f) {
            setAlpha(config.alpha);
        }
        int scrimA = Math.round(config.scrimAlpha * 255f);
        int scrimColor = scrimA > 0 ? Color.argb(scrimA, 0, 0, 0) : Color.TRANSPARENT;
        if (scrimColor != lastScrimColor) {
            lastScrimColor = scrimColor;
            scrimView.setBackgroundColor(scrimColor);
        }
        mediaView.bind(config.media, false, true, null);
        syncToParentBounds();
    }

    void syncToParentBounds() {
        if (!(getParent() instanceof View)) return;
        View parent = (View) getParent();
        int pw = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int ph = parent.getHeight() > 0 ? parent.getHeight() : parent.getMeasuredHeight();
        if (pw <= 0 || ph <= 0) return;
        if (getLeft() == 0 && getTop() == 0 && getRight() == pw && getBottom() == ph
                && mediaView.getWidth() == pw && mediaView.getHeight() == ph
                && scrimView.getWidth() == pw && scrimView.getHeight() == ph) {
            return;
        }
        layingOutToParent = true;
        try {
            if (mediaView.getMeasuredWidth() != pw || mediaView.getMeasuredHeight() != ph
                    || scrimView.getMeasuredWidth() != pw || scrimView.getMeasuredHeight() != ph) {
                measure(
                        MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(ph, MeasureSpec.EXACTLY)
                );
            }
            layout(0, 0, pw, ph);
            mediaView.refreshTransform();
        } finally {
            layingOutToParent = false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (layingOutToParent) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            // Keep LinearLayout flow height/width completely untouched.
            setMeasuredDimension(0, 0);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!layingOutToParent && getParent() instanceof View) {
            View parent = (View) getParent();
            int pw = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
            int ph = parent.getHeight() > 0 ? parent.getHeight() : parent.getMeasuredHeight();
            if (pw > 0 && ph > 0) {
                layingOutToParent = true;
                try {
                    boolean sizeChanged = mediaView.getMeasuredWidth() != pw
                            || mediaView.getMeasuredHeight() != ph
                            || scrimView.getMeasuredWidth() != pw
                            || scrimView.getMeasuredHeight() != ph;
                    if (sizeChanged) {
                        measure(
                                MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(ph, MeasureSpec.EXACTLY)
                        );
                    }
                    layout(0, 0, pw, ph);
                    if (sizeChanged) {
                        mediaView.refreshTransform();
                    }
                } finally {
                    layingOutToParent = false;
                }
                return;
            }
        }
        super.onLayout(changed, l, t, r, b);
    }

    void release() {
        boundConfigSignature = null;
        mediaView.release();
    }
}
