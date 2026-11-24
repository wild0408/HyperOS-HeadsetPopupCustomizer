package com.wild0408.headsetpopup.hook;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders either static/animated images (PNG/JPEG/WebP/GIF via ImageDecoder)
 * or muted looping/one-shot video (MP4/WebM via TextureView + MediaPlayer).
 */
final class PopupMediaView extends FrameLayout {
    private static final ExecutorService DECODE_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final ExecutorService MEDIA_EXECUTOR = Executors.newCachedThreadPool();
    private static final long STATIC_ONE_SHOT_DELAY_MS = 850L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String boundSignature;
    private volatile int generation;
    private volatile boolean released;
    private boolean oneShotFired;
    private Runnable oneShotCallback;

    private ImageView imageView;
    private AnimatedImageDrawable animatedDrawable;
    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private Surface videoSurface;
    private int videoWidth;
    private int videoHeight;
    private int metadataDisplayWidth;
    private int metadataDisplayHeight;
    private boolean centerCrop;
    private float cropLeft = 0f;
    private float cropTop = 0f;
    private float cropRight = 1f;
    private float cropBottom = 1f;
    private boolean hasCustomCrop;
    private String lastImageTransformKey;
    private String lastVideoTransformKey;

    PopupMediaView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
    }

    void bind(
            HookPopupConfigClient.MediaSpec spec,
            boolean forceLoopOverride,
            boolean forcedLoop,
            Runnable onOneShotComplete
    ) {
        if (spec == null || !spec.isConfigured()) {
            release();
            return;
        }
        boolean effectiveLoop = forceLoopOverride ? forcedLoop : spec.loop;
        String sig = spec.signature(forceLoopOverride, forcedLoop);
        if (!released && sig.equals(boundSignature) && onOneShotComplete == null) {
            return;
        }
        resetContent();
        released = false;
        oneShotFired = false;
        boundSignature = sig;
        oneShotCallback = onOneShotComplete;
        centerCrop = spec.isCenterCrop();
        cropLeft = spec.cropLeft;
        cropTop = spec.cropTop;
        cropRight = spec.cropRight;
        cropBottom = spec.cropBottom;
        hasCustomCrop = spec.hasCustomCrop();
        final int token = ++generation;

        if (spec.isVideo()) {
            bindVideo(spec, effectiveLoop, token);
        } else {
            bindImage(spec, effectiveLoop, token);
        }
    }

    private void bindImage(HookPopupConfigClient.MediaSpec spec, boolean effectiveLoop, int token) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(hasCustomCrop
                ? ImageView.ScaleType.MATRIX
                : (spec.isCenterCrop() ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER));
        view.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateImageTransform());
        addView(view);
        imageView = view;

        final Context appContext = getContext().getApplicationContext() != null
                ? getContext().getApplicationContext()
                : getContext();
        final String assetName = spec.assetName;
        DECODE_EXECUTOR.execute(() -> {
            Drawable decoded = decodeImageDrawable(appContext, assetName);
            mainHandler.post(() -> {
                if (released || token != generation || imageView != view) {
                    stopDrawable(decoded);
                    return;
                }
                if (decoded == null) {
                    HookLog.w("failed to decode image asset=" + assetName, null);
                    if (!effectiveLoop) fireOneShotOnce();
                    return;
                }
                view.setImageDrawable(decoded);
                updateImageTransform();
                if (decoded instanceof AnimatedImageDrawable) {
                    AnimatedImageDrawable anim = (AnimatedImageDrawable) decoded;
                    animatedDrawable = anim;
                    anim.setRepeatCount(effectiveLoop ? AnimatedImageDrawable.REPEAT_INFINITE : 0);
                    if (!effectiveLoop) {
                        anim.registerAnimationCallback(new Animatable2.AnimationCallback() {
                            @Override
                            public void onAnimationEnd(Drawable drawable) {
                                if (!released && token == generation) {
                                    fireOneShotOnce();
                                }
                            }
                        });
                    }
                    anim.start();
                } else if (!effectiveLoop) {
                    mainHandler.postDelayed(() -> {
                        if (!released && token == generation) {
                            fireOneShotOnce();
                        }
                    }, STATIC_ONE_SHOT_DELAY_MS);
                }
            });
        });
    }

    private static Drawable decodeImageDrawable(Context context, String assetName) {
        if (context == null || assetName == null || assetName.trim().isEmpty()) {
            return null;
        }
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(
                    context.getContentResolver(),
                    HookPopupConfigClient.mediaUri(assetName.trim())
            );
            return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_DEFAULT);
            });
        } catch (Throwable error) {
            HookLog.w("ImageDecoder failed asset=" + assetName, error);
            return null;
        }
    }

    private void bindVideo(HookPopupConfigClient.MediaSpec spec, boolean effectiveLoop, int token) {
        TextureView tv = new TextureView(getContext());
        tv.setOpaque(false);
        tv.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(tv);
        textureView = tv;

        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                if (released || token != generation) return;
                startMediaPlayer(surfaceTexture, spec.assetName, effectiveLoop, token);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                updateVideoTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                releaseMediaPlayerOnly();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });

        if (tv.isAvailable() && tv.getSurfaceTexture() != null) {
            startMediaPlayer(tv.getSurfaceTexture(), spec.assetName, effectiveLoop, token);
        }
    }

    private void startMediaPlayer(SurfaceTexture surfaceTexture, String assetName, boolean effectiveLoop, int token) {
        releaseMediaPlayerOnly();
        final Context appContext = getContext().getApplicationContext() != null
                ? getContext().getApplicationContext()
                : getContext();
        final Surface surface = new Surface(surfaceTexture);
        videoSurface = surface;

        MEDIA_EXECUTOR.execute(() -> {
            if (released || token != generation) {
                releaseSurfaceQuietly(surface);
                return;
            }
            ParcelFileDescriptor pfd = HookPopupConfigClient.openMediaDescriptor(appContext, assetName);
            if (pfd == null) {
                HookLog.w("video asset open failed asset=" + assetName, null);
                releaseSurfaceQuietly(surface);
                mainHandler.post(() -> {
                    if (videoSurface == surface) videoSurface = null;
                    if (!released && token == generation && !effectiveLoop) fireOneShotOnce();
                });
                return;
            }
            MediaPlayer player = new MediaPlayer();
            try {
                final int[] dims = readVideoDisplayDimensions(pfd);
                if (released || token != generation) {
                    releasePlayerQuietly(player, surface);
                    return;
                }
                player.setDataSource(pfd.getFileDescriptor());
                player.setSurface(surface);
                player.setVolume(0f, 0f);
                player.setLooping(effectiveLoop);
                player.setOnVideoSizeChangedListener((mp, width, height) -> mainHandler.post(() -> {
                    if (released || token != generation || mediaPlayer != mp) return;
                    applyVideoDimensions(width, height);
                    updateVideoTransform();
                }));
                player.setOnPreparedListener(mp -> mainHandler.post(() -> {
                    if (released || token != generation || mediaPlayer != mp) {
                        MEDIA_EXECUTOR.execute(() -> releasePlayerQuietly(mp, null));
                        return;
                    }
                    applyVideoDimensions(mp.getVideoWidth(), mp.getVideoHeight());
                    updateVideoTransform();
                    try {
                        mp.start();
                        HookLog.i("started video playback asset=" + assetName
                                + " size=" + videoWidth + "x" + videoHeight
                                + " loop=" + effectiveLoop);
                    } catch (Throwable error) {
                        HookLog.w("video start failed asset=" + assetName, error);
                        if (!effectiveLoop) fireOneShotOnce();
                    }
                }));
                player.setOnCompletionListener(mp -> mainHandler.post(() -> {
                    if (!released && token == generation && mediaPlayer == mp && !effectiveLoop) {
                        fireOneShotOnce();
                    }
                }));
                player.setOnErrorListener((mp, what, extra) -> {
                    HookLog.w("MediaPlayer error asset=" + assetName + " what=" + what + " extra=" + extra, null);
                    mainHandler.post(() -> {
                        if (released || token != generation || mediaPlayer != mp) {
                            MEDIA_EXECUTOR.execute(() -> releasePlayerQuietly(mp, null));
                            return;
                        }
                        if (!effectiveLoop) {
                            fireOneShotOnce();
                        }
                    });
                    return true;
                });
                mainHandler.post(() -> {
                    if (released || token != generation || videoSurface != surface) {
                        MEDIA_EXECUTOR.execute(() -> releasePlayerQuietly(player, surface));
                        return;
                    }
                    metadataDisplayWidth = dims[0];
                    metadataDisplayHeight = dims[1];
                    mediaPlayer = player;
                });
                player.prepareAsync();
            } catch (Throwable error) {
                HookLog.w("MediaPlayer setup failed asset=" + assetName, error);
                releasePlayerQuietly(player, surface);
                mainHandler.post(() -> {
                    if (videoSurface == surface) videoSurface = null;
                    if (!released && token == generation && !effectiveLoop) fireOneShotOnce();
                });
            } finally {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        });
    }

    void refreshTransform() {
        updateImageTransform();
        updateVideoTransform();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshTransform();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            refreshTransform();
        }
    }

    private static int[] readVideoDisplayDimensions(ParcelFileDescriptor pfd) {
        int outW = 0;
        int outH = 0;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(pfd.getFileDescriptor());
            int w = parsePositiveInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = parsePositiveInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot = parsePositiveInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (w > 0 && h > 0) {
                if (rot == 90 || rot == 270) {
                    outW = h;
                    outH = w;
                } else {
                    outW = w;
                    outH = h;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
        }
        return new int[]{outW, outH};
    }

    private void applyVideoDimensions(int mpWidth, int mpHeight) {
        if (metadataDisplayWidth > 0 && metadataDisplayHeight > 0) {
            videoWidth = metadataDisplayWidth;
            videoHeight = metadataDisplayHeight;
        } else {
            videoWidth = mpWidth;
            videoHeight = mpHeight;
        }
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(0, value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void updateImageTransform() {
        ImageView iv = imageView;
        if (iv == null) return;
        if (!hasCustomCrop) {
            iv.setClipBounds(null);
            iv.setScaleType(centerCrop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            return;
        }
        Drawable drawable = iv.getDrawable();
        if (drawable == null) return;
        int dw = drawable.getIntrinsicWidth();
        int dh = drawable.getIntrinsicHeight();
        int vw = iv.getWidth();
        int vh = iv.getHeight();
        if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return;

        String key = dw + "x" + dh + "|" + vw + "x" + vh + "|" + centerCrop
                + "|" + cropLeft + "," + cropTop + "," + cropRight + "," + cropBottom;
        if (key.equals(lastImageTransformKey)) return;
        lastImageTransformKey = key;

        float roiW = Math.max(1f, (cropRight - cropLeft) * dw);
        float roiH = Math.max(1f, (cropBottom - cropTop) * dh);
        float roiCx = (cropLeft + cropRight) * 0.5f * dw;
        float roiCy = (cropTop + cropBottom) * 0.5f * dh;
        float scale = centerCrop
                ? Math.max(vw / roiW, vh / roiH)
                : Math.min(vw / roiW, vh / roiH);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-roiCx, -roiCy);
        matrix.postScale(scale, scale);
        matrix.postTranslate(vw * 0.5f, vh * 0.5f);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        iv.setImageMatrix(matrix);

        if (!centerCrop) {
            float dstW = roiW * scale;
            float dstH = roiH * scale;
            int cl = Math.max(0, Math.round((vw - dstW) * 0.5f));
            int ct = Math.max(0, Math.round((vh - dstH) * 0.5f));
            int cr = Math.min(vw, Math.round((vw + dstW) * 0.5f));
            int cb = Math.min(vh, Math.round((vh + dstH) * 0.5f));
            iv.setClipBounds(new Rect(cl, ct, cr, cb));
        } else {
            iv.setClipBounds(null);
        }
    }

    private void updateVideoTransform() {
        TextureView tv = textureView;
        if (tv == null || videoWidth <= 0 || videoHeight <= 0) return;
        int viewWidth = tv.getWidth();
        int viewHeight = tv.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        String key = videoWidth + "x" + videoHeight + "|" + viewWidth + "x" + viewHeight + "|" + centerCrop
                + "|" + cropLeft + "," + cropTop + "," + cropRight + "," + cropBottom;
        if (key.equals(lastVideoTransformKey)) return;
        lastVideoTransformKey = key;

        float normW = Math.max(0.05f, cropRight - cropLeft);
        float normH = Math.max(0.05f, cropBottom - cropTop);
        float normCx = (cropLeft + cropRight) * 0.5f;
        float normCy = (cropTop + cropBottom) * 0.5f;

        float roiPxW = Math.max(1f, normW * videoWidth);
        float roiPxH = Math.max(1f, normH * videoHeight);
        float scale = centerCrop
                ? Math.max(viewWidth / roiPxW, viewHeight / roiPxH)
                : Math.min(viewWidth / roiPxW, viewHeight / roiPxH);
        float dstRoiW = roiPxW * scale;
        float dstRoiH = roiPxH * scale;

        float scaleX = dstRoiW / (normW * viewWidth);
        float scaleY = dstRoiH / (normH * viewHeight);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-normCx * viewWidth, -normCy * viewHeight);
        matrix.postScale(scaleX, scaleY);
        matrix.postTranslate(viewWidth * 0.5f, viewHeight * 0.5f);
        tv.setTransform(matrix);

        if (hasCustomCrop && !centerCrop) {
            int cl = Math.max(0, Math.round((viewWidth - dstRoiW) * 0.5f));
            int ct = Math.max(0, Math.round((viewHeight - dstRoiH) * 0.5f));
            int cr = Math.min(viewWidth, Math.round((viewWidth + dstRoiW) * 0.5f));
            int cb = Math.min(viewHeight, Math.round((viewHeight + dstRoiH) * 0.5f));
            tv.setClipBounds(new Rect(cl, ct, cr, cb));
        } else {
            tv.setClipBounds(null);
        }
    }

    private void fireOneShotOnce() {
        if (oneShotFired) return;
        oneShotFired = true;
        Runnable cb = oneShotCallback;
        oneShotCallback = null;
        if (cb != null) {
            mainHandler.post(cb);
        }
    }

    private void releaseMediaPlayerOnly() {
        final MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        final Surface surface = videoSurface;
        videoSurface = null;
        videoWidth = 0;
        videoHeight = 0;
        metadataDisplayWidth = 0;
        metadataDisplayHeight = 0;
        if (mp != null || surface != null) {
            MEDIA_EXECUTOR.execute(() -> releasePlayerQuietly(mp, surface));
        }
    }

    private static void releasePlayerQuietly(MediaPlayer mp, Surface surface) {
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignored) {}
            try { mp.release(); } catch (Throwable ignored) {}
        }
        releaseSurfaceQuietly(surface);
    }

    private static void releaseSurfaceQuietly(Surface surface) {
        if (surface != null) {
            try { surface.release(); } catch (Throwable ignored) {}
        }
    }

    private static void stopDrawable(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) {
            try {
                ((AnimatedImageDrawable) drawable).stop();
            } catch (Throwable ignored) {}
        }
    }

    private void resetContent() {
        generation++;
        mainHandler.removeCallbacksAndMessages(null);
        oneShotCallback = null;
        boundSignature = null;
        lastImageTransformKey = null;
        lastVideoTransformKey = null;
        if (animatedDrawable != null) {
            stopDrawable(animatedDrawable);
            animatedDrawable = null;
        }
        if (imageView != null) {
            imageView.setImageDrawable(null);
            imageView = null;
        }
        releaseMediaPlayerOnly();
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView = null;
        }
        removeAllViews();
    }

    void release() {
        released = true;
        resetContent();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
