package com.wild0408.headsetpopup.hook;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Entry point for the single supported BluetoothExtension build. */
public final class FastConnectHookModule extends XposedModule {
    static final String TARGET_PACKAGE = "com.xiaomi.bluetooth";
    private final FastConnectSessionRegistry sessions = new FastConnectSessionRegistry(this);

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        HookLog.set(this);
        HookLog.i("package ready loader=" + param.getClassLoader());
        FastConnectTargetResolver.install(param.getClassLoader(), this, sessions);
    }
}
