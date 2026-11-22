package com.wild0408.headsetpopup.hook;

import io.github.libxposed.api.XposedModule;

final class HookLog {
    private static volatile XposedModule module;

    private HookLog() {}

    static void set(XposedModule value) { module = value; }

    static void i(String message) {
        XposedModule value = module;
        if (value != null) value.log(4, "HeadsetPopup", message);
    }

    static void w(String message, Throwable error) {
        XposedModule value = module;
        if (value != null) value.log(5, "HeadsetPopup", message, error);
    }
}
