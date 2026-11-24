# AGENT.md — 项目交接与开发指南

> 本文档记录 **HyperOS Headset Popup Customizer** 的当前项目状态、架构实现、关键约束与本地构建调试方法，供后续 Agent 或开发者直接无缝接手。

---

## 1. 项目概览与当前状态

- **项目名称**：耳机弹窗自定义工具（`HyperOS-HeadsetPopupCustomizer`）
- **定位**：基于 LSPosed / `libxposed` API 102 的小米 HyperOS 原生耳机快连弹窗（`com.xiaomi.bluetooth` `PairingDialog`）原位自定义工具。不替换系统弹窗窗口、入场动画与连接状态机，直接在原生弹窗视图树上原位叠加自定义背景、玻璃材质按钮与各阶段文案/媒体。
- **Android 包名（`namespace` / `applicationId`）**：`com.wild0408.headsetpopup`
- **当前版本**：基于 `version.properties` + Git 提交数自动推导语义化版本（每次 `git commit` 自动将 `versionCode` 与补丁号 `+1`，基线为 `2.0.0` / `versionCode = 10`；可通过 `gradle :app:printVersion` 查看，或通过 `:app:bumpMinor` / `:app:bumpMajor` 推进次/主版本）
- **目标 SDK**：`minSdk = 28`，`targetSdk = 36`，`compileSdk = 37`（JDK 17，AGP `9.1.0`，Kotlin `2.4.10`）
- **GitHub 仓库**：`https://github.com/wild0408/HyperOS-HeadsetPopupCustomizer`（Public，`main` 分支）
- **GitHub Release**：`v2.0.0-hook-foundation`（已附带构建完成的 `HyperOS-HeadsetPopupCustomizer-v2.0.0-release.apk`）
- **最新提交**：
  - `c0cc59d` — `fix: refine neutral light glass on white backgrounds and unify preview shader`
  - `a572b4c` — `feat: add root Bluetooth restart, polish About page, and add vector app icon`
  - `7d0cb69` — `Initial release: HyperOS Headset Popup Customizer (v2.0)`
- **当前工作区状态**：工作区干净（`main` 与 `origin/main` 同步），最新 Release APK 已安装至测试机 `afa64220`。

---

## 2. 技术栈与目录结构

### 依赖栈
- **Xposed 框架**：`compileOnly 'io.github.libxposed:api:102.0.0'`
- **UI 框架**：Jetpack Compose（BOM `2025.06.01`）+ `NexioUI 0.1.1`（`com.github.HaoZai000.NexioUI:nexioui-android:0.1.1`）+ `SoftGlass 0.1.3`（`com.github.HaoZai000.SoftGlass:softglass-android:0.1.3`）

### 源码与资源映射

```text
app/src/main/
├── AndroidManifest.xml                       # 模块声明、PopupConfigProvider、自适应图标
├── resources/META-INF/xposed/
│   ├── java_init.list                        # com.wild0408.headsetpopup.hook.FastConnectHookModule
│   ├── module.prop                           # libxposed API 102 (staticScope=true, protective)
│   └── scope.list                            # com.xiaomi.bluetooth
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_background.xml        # 满幅 108dp 磨砂蓝渐变矢量背景（Adaptive Icon）
│   │   ├── ic_launcher_foreground.xml        # 安全区内双层柔光 + 白色线条耳机/齿轮前景
│   │   ├── ic_launcher_monochrome.xml        # Android 13+ 主题单色前景
│   │   └── ic_app_logo.xml                   # 独立方圆矢量 Logo（关于页使用）
│   ├── mipmap-anydpi-v26/                    # ic_launcher.xml / ic_launcher_round.xml
│   └── mipmap-{mdpi..xxxhdpi}/               # 去背精裁后的兜底位图图标
└── java/com/wild0408/headsetpopup/
    ├── MainActivity.kt                       # Compose 入口，Edge-to-Edge
    ├── config/                               # 配置模型、本地存储与跨进程 Provider
    │   ├── PopupConfigModels.kt              # 全局/按型号配置模型、Resolve 继承逻辑、按钮材质枚举
    │   ├── PopupConfigRepository.kt          # JSON 持久化、媒体导入/裁剪保存、Compose State
    │   └── PopupConfigProvider.kt            # ContentProvider (authority: com.wild0408.headsetpopup.popupconfig)
    ├── hook/                                 # 运行于 com.xiaomi.bluetooth 进程内的 Hook 层（Java）
    │   ├── FastConnectHookModule.java        # libxposed 入口，仅挂载 com.xiaomi.bluetooth
    │   ├── FastConnectTargetResolver.java    # Hook 控制器/资源类/PairingDialog/ViewGroup 绘制序与裁剪
    │   ├── FastConnectSessionRegistry.java   # 会话生命周期管理、主动触发防超时守卫
    │   ├── NativePopupSession.java           # 单次弹窗原位定制核心（背景/按钮/文案/5阶段媒体）
    │   ├── PopupBackgroundLayer.java         # 零占位背景层（挂载于 DialogParentPanel2）
    │   ├── PopupMediaView.java               # 图片/GIF/WebP (ImageDecoder) 与静音视频 (TextureView+MediaPlayer)
    │   ├── GlassButtonStyler.java            # 按钮与关闭键玻璃材质（API 33+ AGSL 着色器 + HyperOS 实时模糊）
    │   ├── HookPopupConfigClient.java        # 跨进程读取 ResolvedPopupConfig 与媒体 FD
    │   └── HookLog.java                      # 统一日志封装
    ├── trigger/                              # 主动唤起弹窗与 Root 进程控制
    │   ├── RealFastConnectTrigger.kt         # 构造 MIOT V1 广播 Intent 直调 MiuiFastConnectActivity
    │   └── BluetoothProcessController.kt     # 校验 Root (uid=0) 后强行停止 com.xiaomi.bluetooth
    └── ui/                                   # 应用 UI（3-Tab 架构 + 二级文档页）
        ├── HeadsetPopupApp.kt                # 顶层路由、底部 3-Tab（首页 / 自定义 / 设置）
        ├── navigation/                       # AppRoute / AppRouter / AppNavHost / NavTransitionState
        ├── state/AppSettingsState.kt         # 主题模式、材质档位、触感、预览预设持久化
        ├── theme/HeadsetTheme.kt             # NexioTheme + SoftGlass GlassEnvironment 同步
        ├── components/
        │   ├── AppBottomBar.kt               # 统一液态玻璃底部导航栏（无独立悬浮按钮）
        │   ├── DocumentPageScaffold.kt       # 二级页面统一脚手架与顶栏返回按钮玻璃状态
        │   ├── HorizontalPageSwitch.kt       # 主 Tab 水平切换容器
        │   ├── OverlayDropdownMenu.kt        # 液态玻璃下拉菜单
        │   ├── PageSwitchState.kt            # 切换状态机
        │   └── PopupPreviewCard.kt           # 1:1 真实比例弹窗预览卡 + 新增型号方案弹窗
        └── screens/
            ├── HomeScreen.kt                 # 首页：总开关、实时预览、快速触发真实弹窗
            ├── StylePreviewScreen.kt         # 自定义页：型号切换、卡片背景、按钮材质、弹窗阶段入口
            ├── StageEditorScreen.kt          # 阶段编辑器（5 阶段文案/媒体/已连接过渡与分体合体模式）
            ├── MediaCropScreen.kt            # 图片/视频交互式裁切与视频进度条预览
            ├── SettingsScreen.kt             # 设置页：外观与材质、重启蓝牙进程(Root)、调试中心、关于
            ├── AppearanceScreen.kt           # 主题模式、玻璃材质档位、渐进模糊、触感与预测返回
            ├── DebugScreen.kt                # 主动触发器调试面板、Provider/Hook 状态信息
            ├── AboutScreen.kt                # 品牌头卡、GitHub/Issues/开发者链接、包名复制、开源致谢
            └── MainTabPage.kt                # 主 Tab 公共顶栏与滚动背景框架

art/ic_launcher.svg                           # 1024×1024 纯矢量 SVG 源图标
```

---

## 3. HyperOS 原生弹窗 Hook 原理与布局规格

### 原生 `PairingDialog` 实测布局基准（来自 HyperOS `BluetoothExtension` 反编译）
- 卡片比例与圆角：**`368dp × 422dp`**，圆角 **`32dp`**
- 右上角关闭按钮（`pairingClosable`）：`28dp × 28dp`，距顶/距右 `20dp`
- 标题（`pairingTitle`）：距顶 `32dp`，字号 `20sp`
- 中部阶段动图区（`imageFindHeadsetLayout` / `imageViewFindHeadset`）：居中 `200dp × 200dp`
- 底部操作按钮：高 `50dp`，左右边距 `24dp`，底部间距 `28dp~32dp`

### 关键 Hook 点（`FastConnectTargetResolver`）
1. **`ViewGroup.getChildDrawingOrder`**：将挂载在 `DialogParentPanel2` 末尾的 `PopupBackgroundLayer` 调整到绘制序号 `0`（最底层），保证背景完整位于原生前景之下。
2. **`ViewGroup.setClipToPadding`**：拦截 HyperOS `AlertController` 事后重新开启 `clipToPadding(true)` 的行为，防止背景在底部 `32dp` padding 区域被裁掉。
3. **`PairingDialog.show` & FastConnect Controller**：Hook `modifyView`、`setConnectSuccessLayout`、`setConnectSuccessLayoutDetail`、`onActivityDestroy` 等方法。
4. **`AnimationDrawableResource` / `FreeMapResource`**：Hook `startAnimation(ImageView, int)` 与 `checkAnimationStop(int)`，对自定义阶段或已连接过渡动画接管播放，未自定义阶段完全透传回系统原生动画。
5. **跨进程通信**：Hook 侧通过 `content://com.wild0408.headsetpopup.popupconfig` 调用 `getResolvedConfig` 获取合并后的 JSON 配置，并通过 `openFileDescriptor("content://.../media/<assetName>")` 零拷贝读取宿主私有媒体文件。

---

## 4. 核心设计约束与防回归要点（重要）

修改代码时请务必遵守以下已验证的设计决策：

### 4.1 配置继承与覆写规则
- **全局默认 (`globalProfile`) + 按设备型号覆写 (`deviceProfiles`)**：
  - 设备方案中某项 `enabled = false` 时，自动继承全局配置；预览卡片 `PopupVisualPreviewCard` 必须消费 `ResolvedPopupConfig`（与 Hook 实际行为一致）。
  - **特殊语义**：设备方案中将 `background.enabled = true` 但媒体留空时，表示**显式覆写为“恢复系统原版背景”**，不再回退继承全局背景。
  - 按钮材质在设备方案中支持 `PopupButtonStyle.INHERIT`（继承全局），解析后下发给 Hook 的 `buttonStyle` 为最终生效值。

### 4.2 玻璃材质渲染与预览一致性
- **单源真相（Single Source of Truth）**：
  - 真实弹窗（`GlassButtonStyler.apply` / `applyCloseButton`）与应用内预览卡片（`PopupPreviewCard.kt` 中的 `PreviewGlassButtonSurface`）**共享同一个 `GlassButtonStyler.createSurfaceDrawable()`**（API 33+ 同一段 `SOFTGLASS_SURFACE_AGSL` 着色器 + 同一套配色参数；预览层在其下方叠加同等半径的 `drawBackdrop { blur(...) }`）。修改材质参数时只需维护 `GlassButtonStyler.java`。
- **浅色模式白底可见性约束**：
  - 浅色玻璃（液态 / 柔和 / 磨砂）必须保持**纯中性无色差**（`R == G == B`，**严禁**添加偏蓝/青粉假色散等自创色调）。
  - 靠「纯白/浅灰半透明体色 + `uPxPerDp` 缩放的中性灰极细外轮廓 + 内侧纯白镜面高光 + 极轻下沿内倒角」在纯白背景上保持清晰。
  - **严禁**给按钮开启外部 `View.setElevation(...)` 投影（强制清零 `elevation = 0f`、`stateListAnimator = null`），否则半透明按钮背后及圆角外会出现溢出阴影或被父容器裁切的矩形阴影。
- **预览卡片默认底色**：
  - 未启用自定义背景时，浅色模式为纯白 `#FFFFFF`，深色模式为纯中性深灰 `#222222`（不要加蓝白渐变）。

### 4.3 阶段切换动画流畅度
- `PopupBackgroundLayer`：`onMeasure` 固定返回 `(0, 0)` 不影响 `LinearLayout` 排版；`onLayout` / `syncToParentBounds` 仅当父容器尺寸相对子视图实测尺寸真正改变时才重新 `measure` 子视图并刷新矩阵，避免阶段切换触发布局抖动导致背景视频/GIF 掉帧。
- `PopupMediaView`：图片解码（`DECODE_EXECUTOR`，2 线程，`ALLOCATOR_DEFAULT`）与视频打开/元数据探测/`MediaPlayer` 释放（`MEDIA_EXECUTOR`）全部在后台线程完成，禁止在 UI 主线程同步调用 `MediaMetadataRetriever` 或 `MediaPlayer.release()`。
- `NativePopupSession`：仅在首次挂载背景层时执行 `panel.invalidate()`、强制关闭 `MiuiBlurUtils.setPassWindowBlurEnabled` 及 60ms/220ms 延时对齐；重复阶段回调中仅在文案/颜色实际变化时更新 View。

### 4.4 UI 与导航规范
- 底部导航固定为 **3 个主 Tab**：`首页` / `自定义` / `设置`（`弹窗阶段` 已合并进 `StylePreviewScreen`，不存在单独的 `StagesScreen`）。
- `HorizontalPageSwitch` 设置 `beyondPageCount = tabs.size` 且切换动画期间不读 `state.currentPage`，防止切换中段卡顿。
- 所有二级页面统一使用 `DocumentPageScaffold`，将 `rememberTopBarMaterialState` 放在 `LocalOverScrollState` 内部，保证短页面与长页面顶部返回按钮玻璃效果完全一致。

### 4.5 Root 重启蓝牙进程规范
- 在 `BluetoothProcessController.kt` 中：
  - 禁止在进入页面时主动探测 `su`；仅在用户点击确认弹窗「确认重启」后在 `Dispatchers.IO` 中先执行 `su -c id` 严格校验 `uid=0`。
  - 重启命令使用 `am force-stop com.xiaomi.bluetooth || exit $?` + 精确 `pidof com.xiaomi.bluetooth com.xiaomi.bluetooth:ui` 清理。**严禁使用 `pkill -f com.xiaomi.bluetooth`**（会误杀命令行包含该包名的 `su -c` 自身子 shell，导致返回 `code=143`）。

### 4.6 Release 构建约束
- `app/build.gradle` 中 `release` 构建类型开启 `minifyEnabled true`、`shrinkResources true`，并通过 `app/proguard-rules.pro` 显式保留 `com.wild0408.headsetpopup.hook.**`、`PopupConfigProvider`、`MainActivity` 及 `META-INF/xposed/**`，兼顾体积瘦身与 Hook/反射零回归。
- 支持通过 Gradle 属性、环境变量或本地 `signing.properties`（已由 `.gitignore` 忽略，模板见 `signing.properties.example`）传入正式签名（`RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`）；未配置时自动回退至 `signingConfigs.debug`。

---

## 5. 本地开发环境、构建与调试命令

> **环境注意**：本机为 Windows + **PowerShell 5.1**，命令串联请使用 `;` 与 `if ($LASTEXITCODE -eq 0) { ... }`，不要使用 `&&`；不要用 PowerShell 管道重定向二进制 `adb exec-out screencap`（会按 UTF-16 损坏 PNG），应先截屏到 `/data/local/tmp/*.png` 再 `adb pull`。

### Gradle 路径
工作区无 `gradlew.bat`，使用本机 Gradle 9.5.1：
```powershell
$gradle = "C:\Users\admin\.gradle\wrapper\dists\gradle-9.5.1-bin\bvvv0hnjcinfezlom7fy9dbyi\gradle-9.5.1\bin\gradle.bat"
```

### 常用构建与安装命令
```powershell
# 1. 构建 Debug APK
& $gradle :app:assembleDebug

# 2. 构建 Release APK（已含 lintVitalRelease）
& $gradle :app:assembleRelease

# 3. 安装到测试设备 afa64220 并启动
adb -s afa64220 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s afa64220 shell am force-stop com.wild0408.headsetpopup
adb -s afa64220 shell am start -n com.wild0408.headsetpopup/.MainActivity

# 4. 查看 Hook 实时日志
adb -s afa64220 logcat -s HeadsetPopup
```

### 调试注意事项
- **Hook 生效前提**：任何 `hook/` 目录下的 Java 改动安装后，都必须重启目标进程 `com.xiaomi.bluetooth`（可直接在 App 内点击 **设置 → 重启蓝牙进程**）后才会在真实弹窗生效。
- **HyperOS ADB 限制**：测试机 `afa64220`（HyperOS）默认禁止 `adb shell input tap`（`SecurityException: INJECT_EVENTS`），不要尝试绕过；交互式测试请交由用户点击，非交互验证可用 `screencap` + `pull` 与 `logcat`。
- **本地忽略的参考文件**：工作区根目录下保留的 `BluetoothExtension-current-decompiled-20261122/`、`preloadedFeature-current-decompiled-20261122/`、历史调试截图与 `.apk` 均已通过 `.gitignore` 排除，切勿提交到 Git，也不要未经用户要求擅自删除。

---

## 6. 后续可扩展方向（按需）

当前 v2.0 核心链路（原位 Hook、5 阶段定制、媒体裁切、继承覆写、同源玻璃预览、Root 蓝牙重启、矢量自适应图标、GitHub Release）已全部闭环。后续如有迭代需求，可考虑：
1. **配置导入 / 导出备份**：将配置文件与私有媒体目录打包为 `.zip`，便于换机或分享主题方案。
2. **自定义正式 Release Keystore & GitHub Actions CI**：配置固定签名密钥与 tag 自动构建发布工作流。
3. **更多耳机型号预设自动识别**：在弹窗触发时记录最近识别到的 `deviceId` 与设备名称，供用户在自定义页一键添加型号方案。
