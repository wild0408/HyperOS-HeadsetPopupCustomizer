package com.wild0408.headsetpopup.hook;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks active native PairingDialog sessions and bridges Hook callbacks
 * directly to in-place NativePopupSession customizations.
 */
final class FastConnectSessionRegistry {
    private static final String EXTRA_ACTIVE_TRIGGER = "com.wild0408.headsetpopup.extra.ACTIVE_TRIGGER";
    private static final Map<ViewGroup, WeakReference<View>> BACKGROUND_LAYERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final io.github.libxposed.api.XposedModule module;
    private final Map<Object, NativePopupSession> sessions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, NativePopupSession> dialogs =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Boolean> activeTriggerCallbacks =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<String> hookedCloseCallbacks =
            Collections.synchronizedSet(new HashSet<>());
    private final Map<Object, WeakReference<Object>> activeTriggerHandlers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<String> hookedHandlerMethods =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<String> hookedDialogMethods =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<Class<?>> hookedActivities =
            Collections.synchronizedSet(new HashSet<>());

    FastConnectSessionRegistry(io.github.libxposed.api.XposedModule module) {
        this.module = module;
    }

    static void registerBackgroundLayer(ViewGroup parentPanel, View layer) {
        if (parentPanel != null && layer != null) {
            BACKGROUND_LAYERS.put(parentPanel, new WeakReference<>(layer));
        }
    }

    static void unregisterBackgroundLayer(ViewGroup parentPanel) {
        if (parentPanel != null) {
            BACKGROUND_LAYERS.remove(parentPanel);
        }
    }

    static boolean hasBackgroundLayer(ViewGroup group) {
        if (group == null) return false;
        WeakReference<View> ref = BACKGROUND_LAYERS.get(group);
        return ref != null && ref.get() != null;
    }

    /**
     * Returns custom drawing index for managed DialogParentPanel2 instances,
     * or null to let ViewGroup run its normal drawing order untouched.
     */
    static Integer resolveChildDrawingOrder(ViewGroup group, int childCount, int drawingPosition) {
        WeakReference<View> ref = BACKGROUND_LAYERS.get(group);
        View bg = ref == null ? null : ref.get();
        if (bg == null) return null;
        int bgIndex = group.indexOfChild(bg);
        if (bgIndex < 0 || bgIndex >= childCount) return null;
        if (drawingPosition == 0) return bgIndex;
        if (drawingPosition <= bgIndex) return drawingPosition - 1;
        return drawingPosition;
    }

    void afterNativeShow(Object controller, String point) {
        if (controller == null) return;
        FastConnectTargetResolver.ensureControllerHooks(controller.getClass(), module, this);
        NativePopupSession session = ensureSession(controller, null, point);
        if (session != null) {
            session.scheduleAttach(point);
        }
    }

    void afterPairingDialogShown(Object dialog, String point) {
        if (dialog == null) return;
        NativePopupSession existing = dialogs.get(dialog);
        if (existing != null) {
            existing.scheduleAttach(point);
            return;
        }
        try {
            Object contextValue = invoke(dialog, "getContext");
            Activity activity = activity(contextValue instanceof Context ? (Context) contextValue : null);
            Object windowValue = invoke(dialog, "getWindow");
            if (!(windowValue instanceof android.view.Window) || activity == null) {
                return;
            }
            View decor = ((android.view.Window) windowValue).getDecorView();
            if (decor == null) return;
            NativePopupSession session = new NativePopupSession(
                    dialog, dialog, decor, activity,
                    () -> remove(dialog, dialog));
            sessions.put(dialog, session);
            dialogs.put(dialog, session);
            installDialogLifecycleHooks(dialog.getClass());
            installActivityDestroyHook(activity.getClass());
            session.scheduleAttach(point);
        } catch (Throwable error) {
            HookLog.w(point + " session creation failed", error);
        }
    }

    void afterModifyView(Object controller, View view, int connectState, int extraState, String title) {
        if (controller == null) return;
        FastConnectTargetResolver.ensureControllerHooks(controller.getClass(), module, this);
        NativePopupSession session = ensureSession(controller, view, "modifyView");
        if (session != null) {
            session.onModifyView(view, connectState, extraState, title);
        }
    }

    void afterConnectSuccessLayout(Object controller, View view) {
        if (controller == null) return;
        NativePopupSession session = ensureSession(controller, view, "setConnectSuccessLayout");
        if (session != null) {
            session.onConnectSuccessLayout(view);
        }
    }

    void afterConnectSuccessLayoutDetail(Object controller, View view) {
        if (controller == null) return;
        NativePopupSession session = ensureSession(controller, view, "setConnectSuccessLayoutDetail");
        if (session != null) {
            session.onConnectSuccessLayoutDetail(view);
        }
    }

    int beforeStartAnimation(Object resource, ImageView imageView, int animType) {
        NativePopupSession session = findSessionForResource(resource);
        if (session == null) return NativePopupSession.ANIM_PASSTHROUGH;
        return session.beforeStartAnimation(resource, imageView, animType);
    }

    boolean shouldSuppressCheckAnimationStop(Object resource, int animType) {
        NativePopupSession session = findSessionForResource(resource);
        return session != null && session.shouldSuppressCheckAnimationStop(animType);
    }

    void onControllerDestroy(Object controller) {
        if (controller == null) return;
        NativePopupSession session = sessions.get(controller);
        if (session != null) {
            session.dispose("controller onActivityDestroy");
        }
    }

    private NativePopupSession ensureSession(Object controller, View explicitContent, String point) {
        Object dialog = firstField(controller, "mAlert", "f11849e", "pairingDialog", "mPairingDialog", "dialog", "alert", "mDialog");
        Object content = explicitContent != null
                ? explicitContent
                : firstField(controller, "mView", "f11845c", "contentView", "mContentView", "view");
        Object activityValue = firstField(controller, "mActivityContext", "f11843b", "mActivity", "activity", "mContext", "context");
        Activity activity = activity(activityValue instanceof Context ? (Context) activityValue : null);
        if (dialog == null) dialog = findFieldValue(controller, Dialog.class);
        if (content == null) content = findFieldValue(controller, View.class);
        if (activity == null) activity = findActivityField(controller);
        if (activity == null && dialog != null) {
            Object context = invoke(dialog, "getContext");
            activity = activity(context instanceof Context ? (Context) context : null);
        }
        if (!(content instanceof View) && dialog != null) {
            Object window = invoke(dialog, "getWindow");
            if (window instanceof android.view.Window) {
                content = ((android.view.Window) window).getDecorView();
            }
        }
        if (dialog == null || !(content instanceof View) || activity == null) {
            return null;
        }

        final Object sessionDialog = dialog;
        final View sessionContent = (View) content;
        final Activity sessionActivity = activity;
        guardActiveTriggerCallback(controller, sessionActivity);

        NativePopupSession existingByController = sessions.get(controller);
        if (existingByController != null && existingByController.sameDialog(sessionDialog)) {
            existingByController.updateContentView(sessionContent);
            return existingByController;
        }
        if (existingByController != null) {
            existingByController.dispose("dialog replaced");
        }
        NativePopupSession existingByDialog = dialogs.get(sessionDialog);
        if (existingByDialog != null) {
            existingByDialog.dispose("dialog promoted to controller session");
        }

        HookLog.i(point + " attached native session dialog=" + sessionDialog.getClass().getName()
                + " controller=" + controller.getClass().getSimpleName());
        NativePopupSession session = new NativePopupSession(
                controller, sessionDialog, sessionContent, sessionActivity,
                () -> remove(controller, sessionDialog));
        sessions.put(controller, session);
        dialogs.put(sessionDialog, session);
        installDialogLifecycleHooks(sessionDialog.getClass());
        installActivityDestroyHook(sessionActivity.getClass());
        return session;
    }

    private NativePopupSession findSessionForResource(Object resource) {
        if (resource == null) return null;
        List<NativePopupSession> snapshot;
        synchronized (sessions) {
            snapshot = new ArrayList<>(sessions.values());
        }
        for (NativePopupSession session : snapshot) {
            if (session != null && session.ownsResource(resource)) {
                return session;
            }
        }
        return null;
    }

    private void installDialogLifecycleHooks(Class<?> type) {
        for (String name : new String[]{"dismiss", "cancel", "hide"}) {
            Method method = noArgMethod(type, name);
            if (method == null || !hookedDialogMethods.add(method.toGenericString())) continue;
            try {
                method.setAccessible(true);
                module.hook(method)
                        .setId("native-dialog." + name + "." + method.toGenericString())
                        .setPriority(io.github.libxposed.api.XposedInterface.PRIORITY_DEFAULT)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            NativePopupSession session = dialogs.get(chain.getThisObject());
                            if (session != null) session.dispose("native dialog " + name);
                            return result;
                        });
            } catch (Throwable error) {
                HookLog.w("native lifecycle hook failed " + method, error);
            }
        }
    }

    private void installActivityDestroyHook(Class<?> type) {
        if (!hookedActivities.add(type)) return;
        Method method = noArgMethod(type, "onDestroy");
        if (method == null) return;
        try {
            method.setAccessible(true);
            module.hook(method)
                    .setId("fast-connect-activity.onDestroy." + type.getName())
                    .setPriority(io.github.libxposed.api.XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity destroyed = chain.getThisObject() instanceof Activity
                                ? (Activity) chain.getThisObject() : null;
                        List<NativePopupSession> snapshot;
                        synchronized (sessions) {
                            snapshot = new ArrayList<>(sessions.values());
                        }
                        for (NativePopupSession session : snapshot) {
                            if (session != null && destroyed != null && session.belongsTo(destroyed)) {
                                session.dispose("activity onDestroy");
                            }
                        }
                        return result;
                    });
        } catch (Throwable error) {
            HookLog.w("activity onDestroy hook failed " + type, error);
        }
    }

    private void guardActiveTriggerCallback(Object controller, Activity activity) {
        if (!isActiveTrigger(activity)) return;
        Object callback = field(controller, "mFastConnectClientCallBack");
        if (callback != null) {
            activeTriggerCallbacks.put(callback, Boolean.TRUE);
            Method closeDialog = noArgMethod(callback.getClass(), "closeDialog");
            if (closeDialog != null && hookedCloseCallbacks.add(closeDialog.toGenericString())) {
                try {
                    closeDialog.setAccessible(true);
                    module.hook(closeDialog)
                            .setId("fast-connect.active-trigger.closeDialog." + closeDialog.toGenericString())
                            .setPriority(io.github.libxposed.api.XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (Boolean.TRUE.equals(activeTriggerCallbacks.get(chain.getThisObject()))) {
                                    HookLog.i("suppressed service closeDialog for active trigger");
                                    return null;
                                }
                                return chain.proceed();
                            });
                    HookLog.i("installed active trigger closeDialog guard " + callback.getClass().getName());
                } catch (Throwable error) {
                    HookLog.w("active trigger closeDialog guard failed", error);
                }
            }
        }

        Object handler = field(controller, "mMainHandler");
        if (handler != null) {
            activeTriggerHandlers.put(handler, new WeakReference<>(controller));
            Method handleMessage = methodWithParams(handler.getClass(), "handleMessage", Message.class);
            if (handleMessage != null && hookedHandlerMethods.add(handleMessage.toGenericString())) {
                try {
                    handleMessage.setAccessible(true);
                    module.hook(handleMessage)
                            .setId("fast-connect.active-trigger.handleMessage." + handleMessage.toGenericString())
                            .setPriority(io.github.libxposed.api.XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                WeakReference<Object> ownerRef = activeTriggerHandlers.get(chain.getThisObject());
                                Object ownerController = ownerRef == null ? null : ownerRef.get();
                                Object arg = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                                if (ownerController != null && arg instanceof Message) {
                                    int what = ((Message) arg).what;
                                    if (what == 8) {
                                        HookLog.i("suppressed bind-service timeout msg=8 for active trigger");
                                        return null;
                                    }
                                    if (what == 5 && field(ownerController, "mFastConnectService") == null) {
                                        HookLog.i("suppressed unbound startPair msg=5 for active trigger");
                                        return null;
                                    }
                                    if (what == 9) {
                                        HookLog.i("suppressed connecting timeout msg=9 for active trigger");
                                        return null;
                                    }
                                }
                                return chain.proceed();
                            });
                    HookLog.i("installed active trigger handler guard " + handler.getClass().getName());
                } catch (Throwable error) {
                    HookLog.w("active trigger handler guard failed", error);
                }
            }
        }
    }

    private static Object firstField(Object object, String... names) {
        for (String name : names) {
            Object value = field(object, name);
            if (value != null) return value;
        }
        return null;
    }

    private static Object findFieldValue(Object object, Class<?> expected) {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value != null && expected.isInstance(value)) return value;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static Activity findActivityField(Object object) {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    Activity activity = activity(value instanceof Context ? (Context) value : null);
                    if (activity != null) return activity;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static Method methodWithParams(Class<?> type, String name, Class<?>... params) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static boolean isActiveTrigger(Activity activity) {
        try {
            Intent intent = activity == null ? null : activity.getIntent();
            return intent != null && intent.getBooleanExtra(EXTRA_ACTIVE_TRIGGER, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method noArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name); }
            catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private void remove(Object controller, Object dialog) {
        sessions.remove(controller);
        dialogs.remove(dialog);
    }

    private static Object invoke(Object receiver, String name) {
        try { return receiver.getClass().getMethod(name).invoke(receiver); }
        catch (Throwable ignored) { return null; }
    }

    static Object field(Object object, String name) {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    static Activity activity(Context context) {
        for (Context current = context; current instanceof ContextWrapper;
             current = ((ContextWrapper) current).getBaseContext()) {
            if (current instanceof Activity) return (Activity) current;
        }
        return context instanceof Activity ? (Activity) context : null;
    }
}
