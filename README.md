# HyperOS Headset Popup Customizer

基于 LSPosed / `libxposed` API 102 的小米 HyperOS 原生耳机快连弹窗（`com.xiaomi.bluetooth` `PairingDialog`）原位自定义工具。

## 功能特性

- **原位 Hook 保留系统体验**：直接在 HyperOS 原生 `PairingDialog` 上叠加自定义背景、按钮材质与各阶段素材，不替换系统弹窗窗口、动画与状态机。
- **自定义卡片背景**：支持图片、GIF/WebP 动图与静音循环视频（MP4/WebM），支持裁剪区域、不透明度、暗角遮罩与前景色调自适应。
- **玻璃材质按钮**：支持原生 / 液态玻璃 / 柔和玻璃 / 磨砂玻璃 / 曜黑 / 柔紫等按钮与关闭键材质（API 33+ AGSL 着色器 + HyperOS 实时模糊）。
- **全阶段独立配置**：支持发现设备、连接中、已连接（独立过渡动画、分体耳机/充电盒或合体视觉）、连接失败、重置提示五个阶段的文案与媒体自定义。
- **全局默认 + 按设备型号覆写**：未单独启用覆写时自动继承全局配置，启用后可针对特定设备 ID（如小米耳机型号）精细调整或还原系统默认样式。
- **应用内实时预览与主动触发**：使用 Jetpack Compose + NexioUI + SoftGlass 构建，支持 1:1 真实弹窗比例预览及一键唤起真实系统弹窗测试。

## 环境要求

- Android 9+（推荐 HyperOS / Android 13+ 以启用完整 AGSL 玻璃着色器与实时模糊）
- LSPosed（支持 `libxposed` API 102）
- 作用域勾选：`com.xiaomi.bluetooth`
- 构建环境：JDK 17、Android SDK（`compileSdk 37`）、Gradle 9.5+

## 构建说明

```bash
gradle :app:assembleDebug
```

安装 APK 后在 LSPosed 中启用模块 `com.wild0408.headsetpopup`，勾选作用域 `com.xiaomi.bluetooth`，并重启蓝牙扩展进程后即可生效。
