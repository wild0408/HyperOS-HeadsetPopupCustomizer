package com.wild0408.headsetpopup.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves and hooks HyperOS BluetoothExtension FastConnect controllers,
 * PairingDialog, ViewGroup drawing order, and animation resource implementations.
 */
final class FastConnectTargetResolver {
    private static final Set<String> HOOKED = Collections.synchronizedSet(new HashSet<>());

    private static final String[] CONTROLLER_NAMES = {
            "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectController",
            "com.android.bluetooth.ble.app.fastconnect.MiuiControllerCommon",
            "com.android.bluetooth.ble.app.fastconnect.MiuiControllerCommonV2",
            "com.android.bluetooth.ble.app.fastconnect.airpods.MiuiAirPodsFastConnectController",
            "com.android.bluetooth.ble.app.fastconnect.thirdparty.MiuiSonyFastConnectController",
            "com.android.bluetooth.ble.app.C0909r4",
            "G0.i"
    };

    private static final String[] RESOURCE_NAMES = {
            "com.android.bluetooth.ble.app.fastconnect.AnimationDrawableResource",
            "com.android.bluetooth.ble.app.fastconnect.FreeMapResource"
    };

    private FastConnectTargetResolver() {}

    static void install(
            ClassLoader loader,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        installDrawingOrderHook(module);
        installClipToPaddingHook(module);
        installPairingDialogHook(loader, module, sessions);
        for (String name : CONTROLLER_NAMES) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                installController(type, module, sessions);
            } catch (Throwable ignored) {}
        }
        for (String name : RESOURCE_NAMES) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                installResource(type, module, sessions);
            } catch (Throwable ignored) {}
        }
        installClassLoaderFallback(loader, module, sessions);
    }

    static void ensureControllerHooks(
            Class<?> type,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        if (type == null) return;
        for (Class<?> current = type;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            installController(current, module, sessions);
        }
    }

    private static void installDrawingOrderHook(io.github.libxposed.api.XposedModule module) {
        try {
            Method method = ViewGroup.class.getDeclaredMethod("getChildDrawingOrder", int.class, int.class);
            if (!HOOKED.add("drawing-order:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("native-popup.drawing-order")
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object self = chain.getThisObject();
                        if (self instanceof ViewGroup) {
                            List<?> args = chain.getArgs();
                            if (args.size() >= 2 && args.get(0) instanceof Integer && args.get(1) instanceof Integer) {
                                Integer custom = FastConnectSessionRegistry.resolveChildDrawingOrder(
                                        (ViewGroup) self,
                                        (Integer) args.get(0),
                                        (Integer) args.get(1)
                                );
                                if (custom != null) return custom;
                            }
                        }
                        return chain.proceed();
                    });
            HookLog.i("installed ViewGroup.getChildDrawingOrder hook");
        } catch (Throwable error) {
            HookLog.w("ViewGroup.getChildDrawingOrder hook failed", error);
        }
    }

    private static void installClipToPaddingHook(io.github.libxposed.api.XposedModule module) {
        try {
            Method method = ViewGroup.class.getDeclaredMethod("setClipToPadding", boolean.class);
            if (!HOOKED.add("clip-to-padding:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("native-popup.clip-to-padding")
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object self = chain.getThisObject();
                        List<?> args = chain.getArgs();
                        if (self instanceof ViewGroup
                                && args.size() >= 1
                                && Boolean.TRUE.equals(args.get(0))
                                && FastConnectSessionRegistry.hasBackgroundLayer((ViewGroup) self)) {
                            // HyperOS AlertController.applyFullScrollBottomPaddingLayout resets
                            // DialogParentPanel2.setClipToPadding(true) right after show(), which
                            // clips PopupBackgroundLayer above the 32dp bottom padding strip.
                            return null;
                        }
                        return chain.proceed();
                    });
            HookLog.i("installed ViewGroup.setClipToPadding hook");
        } catch (Throwable error) {
            HookLog.w("ViewGroup.setClipToPadding hook failed", error);
        }
    }

    private static void installPairingDialogHook(
            ClassLoader loader,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        if (!HOOKED.add("pairing-install-loader:" + System.identityHashCode(loader))) return;
        try {
            Class<?> type = Class.forName("miuix.appcompat.app.PairingDialog", false, loader);
            Method show = findMethod(type, "show");
            if (show == null || !HOOKED.add("pairing-show:" + show.toGenericString())) return;
            show.setAccessible(true);
            module.hook(show)
                    .setId("native-pairing-dialog.show." + show.toGenericString())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        sessions.afterPairingDialogShown(chain.getThisObject(), "PairingDialog.show");
                        return result;
                    });
            HookLog.i("installed PairingDialog.show hook " + type.getClassLoader());
        } catch (Throwable error) {
            HookLog.w("PairingDialog.show hook unavailable", error);
        }
    }

    private static void installClassLoaderFallback(
            ClassLoader loader,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        try {
            Method loadClass = findMethod(loader.getClass(), "loadClass", String.class, boolean.class);
            if (loadClass == null) loadClass = findMethod(loader.getClass(), "loadClass", String.class);
            if (loadClass == null || !HOOKED.add("loader:" + loadClass.toGenericString())) return;
            loadClass.setAccessible(true);
            module.hook(loadClass)
                    .setId("fast-connect-class-loader." + loadClass.toGenericString())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (!(result instanceof Class)) return result;
                        String name = String.valueOf(chain.getArgs().get(0));
                        if (isControllerName(name)) {
                            installController((Class<?>) result, module, sessions);
                        } else if (isResourceName(name)) {
                            installResource((Class<?>) result, module, sessions);
                        } else if ("miuix.appcompat.app.PairingDialog".equals(name)) {
                            installPairingDialogHook((ClassLoader) chain.getThisObject(), module, sessions);
                        }
                        return result;
                    });
            HookLog.i("installed split ClassLoader fallback " + loadClass.getDeclaringClass().getName());
        } catch (Throwable error) {
            HookLog.w("ClassLoader fallback failed", error);
        }
    }

    private static boolean isControllerName(String name) {
        if (name == null || name.contains("$")) return false;
        for (String candidate : CONTROLLER_NAMES) {
            if (candidate.equals(name)) return true;
        }
        return name.contains(".fastconnect.") && name.contains("Controller");
    }

    private static boolean isResourceName(String name) {
        if (name == null || name.contains("$")) return false;
        for (String candidate : RESOURCE_NAMES) {
            if (candidate.equals(name)) return true;
        }
        return name.endsWith("AnimationDrawableResource") || name.endsWith("FreeMapResource");
    }

    private static void installController(
            Class<?> type,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        if (!HOOKED.add("controller-class:" + type.getName() + "@" + System.identityHashCode(type.getClassLoader()))) {
            return;
        }
        hookAfterShow(type, "setFindDeviceDialog", module, sessions);
        hookAfterShow(type, "onActivityCreate", module, sessions);
        hookAfterShow(type, "setDiscoveryDialog", module, sessions);
        hookAfterShow(type, "setConnectDeviceDialog", module, sessions);
        hookAfterShow(type, "updateConnectSuccessDilog", module, sessions);
        hookAfterShow(type, "i0", module, sessions);
        hookAfterShow(type, "s1", module, sessions);

        hookModifyView(type, module, sessions);
        hookConnectSuccessLayout(type, module, sessions);
        hookConnectSuccessLayoutDetail(type, module, sessions);
        hookOnActivityDestroy(type, module, sessions);

        HookLog.i("resolved controller hooks for " + type.getName());
    }

    private static void installResource(
            Class<?> type,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        if (!HOOKED.add("resource-class:" + type.getName() + "@" + System.identityHashCode(type.getClassLoader()))) {
            return;
        }
        try {
            Method startAnim = type.getDeclaredMethod("startAnimation", ImageView.class, int.class);
            if (HOOKED.add("method:" + startAnim.toGenericString())) {
                startAnim.setAccessible(true);
                module.hook(startAnim)
                        .setId("resource.startAnimation." + type.getSimpleName())
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            List<?> args = chain.getArgs();
                            ImageView imageView = args.size() >= 1 && args.get(0) instanceof ImageView
                                    ? (ImageView) args.get(0) : null;
                            int animType = args.size() >= 2 && args.get(1) instanceof Integer
                                    ? (Integer) args.get(1) : 0;
                            int decision = sessions.beforeStartAnimation(chain.getThisObject(), imageView, animType);
                            if (decision == NativePopupSession.ANIM_HANDLED_TRUE) {
                                return Boolean.TRUE;
                            }
                            if (decision == NativePopupSession.ANIM_SKIP_FALSE) {
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        });
                HookLog.i("installed startAnimation hook on " + type.getName());
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            HookLog.w("failed to hook startAnimation on " + type.getName(), error);
        }

        try {
            Method checkStop = findMethod(type, "checkAnimationStop", int.class);
            if (checkStop != null && HOOKED.add("method:" + checkStop.toGenericString())) {
                checkStop.setAccessible(true);
                module.hook(checkStop)
                        .setId("resource.checkAnimationStop." + checkStop.getDeclaringClass().getSimpleName())
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            List<?> args = chain.getArgs();
                            int animType = args.size() >= 1 && args.get(0) instanceof Integer
                                    ? (Integer) args.get(0) : 0;
                            if (sessions.shouldSuppressCheckAnimationStop(chain.getThisObject(), animType)) {
                                return null;
                            }
                            return chain.proceed();
                        });
                HookLog.i("installed checkAnimationStop hook on " + checkStop.getDeclaringClass().getName());
            }
        } catch (Throwable error) {
            HookLog.w("failed to hook checkAnimationStop on " + type.getName(), error);
        }
    }

    private static void hookModifyView(
            Class<?> owner,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        try {
            Method method = owner.getDeclaredMethod("modifyView", View.class, int.class, int.class, String.class);
            if (!HOOKED.add("method:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("controller.modifyView." + owner.getSimpleName())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        List<?> args = chain.getArgs();
                        View view = args.size() >= 1 && args.get(0) instanceof View ? (View) args.get(0) : null;
                        int state = args.size() >= 2 && args.get(1) instanceof Integer ? (Integer) args.get(1) : 1;
                        int extra = args.size() >= 3 && args.get(2) instanceof Integer ? (Integer) args.get(2) : -1;
                        String title = args.size() >= 4 && args.get(3) instanceof String ? (String) args.get(3) : null;
                        sessions.afterModifyView(chain.getThisObject(), view, state, extra, title);
                        return result;
                    });
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            HookLog.w("failed to hook modifyView on " + owner.getName(), error);
        }
    }

    private static void hookConnectSuccessLayout(
            Class<?> owner,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        try {
            Method method = owner.getDeclaredMethod("setConnectSuccessLayout", View.class);
            if (!HOOKED.add("method:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("controller.setConnectSuccessLayout." + owner.getSimpleName())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        List<?> args = chain.getArgs();
                        View view = args.size() >= 1 && args.get(0) instanceof View ? (View) args.get(0) : null;
                        if (Boolean.TRUE.equals(result)) {
                            sessions.afterConnectSuccessLayout(chain.getThisObject(), view);
                        }
                        return result;
                    });
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            HookLog.w("failed to hook setConnectSuccessLayout on " + owner.getName(), error);
        }
    }

    private static void hookConnectSuccessLayoutDetail(
            Class<?> owner,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        try {
            Method method = owner.getDeclaredMethod("setConnectSuccessLayoutDetail", View.class);
            if (!HOOKED.add("method:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("controller.setConnectSuccessLayoutDetail." + owner.getSimpleName())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        List<?> args = chain.getArgs();
                        View view = args.size() >= 1 && args.get(0) instanceof View ? (View) args.get(0) : null;
                        sessions.afterConnectSuccessLayoutDetail(chain.getThisObject(), view);
                        return result;
                    });
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            HookLog.w("failed to hook setConnectSuccessLayoutDetail on " + owner.getName(), error);
        }
    }

    private static void hookOnActivityDestroy(
            Class<?> owner,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        try {
            Method method = owner.getDeclaredMethod("onActivityDestroy");
            if (!HOOKED.add("method:" + method.toGenericString())) return;
            method.setAccessible(true);
            module.hook(method)
                    .setId("controller.onActivityDestroy." + owner.getSimpleName())
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        sessions.onControllerDestroy(chain.getThisObject());
                        return result;
                    });
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable error) {
            HookLog.w("failed to hook onActivityDestroy on " + owner.getName(), error);
        }
    }

    private static void hookAfterShow(
            Class<?> owner,
            String name,
            io.github.libxposed.api.XposedModule module,
            FastConnectSessionRegistry sessions
    ) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!name.equals(method.getName())) continue;
            try {
                if (!HOOKED.add("method:" + method.toGenericString())) continue;
                method.setAccessible(true);
                String id = owner.getSimpleName() + "." + name;
                module.hook(method)
                        .setId(id + "." + method.toGenericString())
                        .setPriority(XposedInterface.PRIORITY_DEFAULT)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            sessions.afterNativeShow(chain.getThisObject(), id);
                            return result;
                        });
            } catch (Throwable error) {
                HookLog.w("failed to hook " + owner.getName() + "." + name, error);
            }
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
