# AGENTS.md

## 基本约定

- 与用户沟通时使用中文。
- 生成或修改的文档、代码注释使用中文；日志打印使用英文。
- 可保留 Shizuku、Instrumentation、CarrierConfig、IMS、VoLTE、VoWiFi、VoNR、AIDL 等通用英文技术名词。
- 修改代码前先理解当前实现和设备兼容约束，避免只针对一个 Android 版本做硬编码修复。
- 本项目不要求添加单元测试。代码变更后至少运行 `.\gradlew.bat :app:assembleDebug --stacktrace --console=plain` 验证编译。
- Git 提交信息使用中文；自动生成提交信息时也必须生成中文内容。

## 项目概览

TurboIMS 是一个面向 Google Pixel Tensor 设备的 Android 应用，用于读取和修改 IMS/运营商相关配置，开启或关闭 VoLTE、VoWiFi、VT、VoNR、Cross-SIM Calling、UT、5G NR 等功能。项目依赖 Shizuku 执行特权操作，不要求 root。

应用主要面向 Android 13 及以上系统，当前构建目标为 Android SDK 37。设备侧功能验证应优先在已安装并运行 Shizuku 的真实 Pixel 设备上完成。

## 构建环境

- Gradle wrapper：`9.4.1`
- Android Gradle Plugin：`9.2.1`
- Kotlin：`2.4.0`
- `compileSdk`：`37`
- `targetSdk`：`37`
- `minSdk`：`33`
- Java/Kotlin JVM target：`21`
- ABI：仅 `arm64-v8a`
- UI：Jetpack Compose + Material 3
- 版本号：`versionCode` 由 Git commit 数生成，`versionName` 来自 `gradle/libs.versions.toml` 的 `app-version`

常用命令：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
.\gradlew.bat :app:assembleRelease --stacktrace --console=plain
.\gradlew.bat lint --stacktrace --console=plain
.\gradlew.bat clean
```

依赖版本统一维护在 `gradle/libs.versions.toml`，不要在 Gradle 脚本中硬编码依赖版本。

## 模块结构

- `app/`：主应用模块，包含 UI、ViewModel、Shizuku 桥接、Instrumentation 入口和业务逻辑。
- `stub/`：编译期隐藏 API 和 internal AIDL stub。该模块通过 `compileOnly(project(":stub"))` 供 `app` 编译使用，不应作为运行时代码打包进 APK。

`stub` 模块启用了 AIDL，且显式设置 `enableKotlin = false`，用于配合 AGP 9 的 built-in Kotlin 行为。

## 核心包结构

- `io.github.vvb2060.ims.model`：数据模型和功能映射。
- `io.github.vvb2060.ims.viewmodel`：界面状态和业务调度，主要是 `MainViewModel` 与 `LogcatViewModel`。
- `io.github.vvb2060.ims.ui`：Activity、Compose UI、组件和主题。
- `io.github.vvb2060.ims.privileged`：通过 Instrumentation 执行的特权逻辑。
- `ShizukuProvider.kt`：主进程到特权 Instrumentation 的桥接层。
- `LogcatRepository.kt`：应用日志采集和导出相关逻辑。

## 运行架构

项目采用主应用进程 + 特权 Instrumentation 的双路径模型：

1. 主应用进程负责 Compose UI、ViewModel 状态管理、Shizuku 状态检查和用户交互。
2. `ShizukuProvider` 使用 Shizuku 包装后的 `IActivityManager.startInstrumentation()` 启动特权 Instrumentation。
3. Instrumentation 通过 shell permission delegation 访问受限 API，例如 `CarrierConfigManager`、`SubscriptionManager`、`ITelephony`、`ISub`。
4. Instrumentation 通过 `IInstrumentationWatcher` 返回 `Bundle` 结果，`ShizukuProvider` 再转换为 ViewModel 需要的数据。

典型数据流：

```text
用户操作 -> ViewModel -> ShizukuProvider -> Instrumentation
                                       -> CarrierConfigManager / Telephony service
                                       -> Bundle 结果 -> ViewModel -> UI
```

## 特权入口

- `SimReader`：读取当前可用 SIM 列表。
- `ImsCapabilityReader`：读取 IMS 注册状态和 VoLTE/VoWiFi/VoNR/VT/NR 能力状态。
- `ImsModifier`：写入或重置运营商配置覆盖值。
- `ImsResetter`：调用 telephony service 重置 IMS。
- `PersistentVolteModifier`：按单张 SIM 读写持久化 VoLTE，使用 VoIMS opt-in 和用户开关；复用统一权限委托，通过系统管理类反射访问隐藏 API。
- `BrokerInstrumentation`：在主修改路径权限受限或返回空结果时作为 fallback。
- `ShellPermissionDelegation`：统一处理 shell permission delegation 停止调用的 Android 版本兼容。

## Android 版本兼容重点

- 隐藏 API 和 AIDL 接口可能随 Android 版本变更。修改 `stub/` 后必须确认目标设备 framework 的真实签名。
- Android 17 QPR1 Beta 6 中 `IActivityManager.stopDelegateShellPermissionIdentity` 从旧版无参签名变为带 `uid` 的签名。项目通过 `ShellPermissionDelegation.kt` 同时兼容：
  - 旧版：`stopDelegateShellPermissionIdentity()`
  - 新版：`stopDelegateShellPermissionIdentity(int uid)`
- 不要在业务类里分散写这类兼容分支；优先集中封装，保持 `SimReader`、`ImsModifier` 等入口只调用统一 helper。
- 使用 Android 14+ 才稳定存在或行为不同的能力时，需要做版本判断或反射 fallback。

## Shizuku 与隐藏 API 约束

- 任何依赖 Shizuku 的路径都要处理 binder 不可用、权限未授予、服务版本过旧、返回空结果等情况。
- 使用隐藏 API、反射或编译期 stub 时，必须在代码附近用中文注释说明原因和兼容考虑。
- `LSPass.setHiddenApiExemptions("")` 目前在 `ShizukuProvider.onCreate()` 中用于隐藏 API 绕过，不要随意删除。
- 真实功能验证优先使用已连接设备和 logcat，不能只依赖编译通过。

## 业务与数据约定

- SIM 配置按 `subId` 持久化到 `SharedPreferences`，名称形如 `sim_config_<subId>`。
- `subId = -1` 表示应用到所有 SIM。
- 持久化 VoLTE 是独立的即时操作，不属于 `Feature` 配置草稿。原始订阅值（含 -1）保存在 `noBackupFilesDir/persistent_volte_<subId>.json`，以 SIM 标识摘要校验；失败回退失败时保留记录供恢复。重置配置先恢复所选活动 SIM 的原始值，再清除 CarrierConfig；未激活 SIM 的备份保留至重新激活后恢复。
- `Feature` 和 `FeatureConfigMapper` 是 IMS 功能开关到运营商配置键的主要映射入口。
- 修改运营商配置时优先走 `ImsModifier`，必要时再由 `ShizukuProvider` 触发 `BrokerInstrumentation` fallback。
- UI 不直接执行业务写入逻辑，业务动作应放在 ViewModel 或特权入口中。

## 代码风格

- 保持现有 Kotlin/Compose 风格，不引入 Hilt、Dagger、Room 等新框架，除非任务明确需要。
- Activity 只承担界面承载和生命周期对接，不放复杂业务逻辑。
- ViewModel 负责状态流、用户动作调度和持久化协调。
- 复杂隐藏 API 调用集中在 `privileged/` 或明确的 helper 中，不要散落到 UI 层。
- 文件过长或逻辑明显复用时，优先提取小型 helper，避免大范围重构。
- 注释写中文，日志 message 写英文。

## 验证流程

常规代码变更：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

设备验证示例：

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell am force-stop io.github.vvb2060.ims
adb shell am start -n io.github.vvb2060.ims/.ui.MainActivity
adb logcat -d -v time AndroidRuntime:E ShizukuProvider:D SimReader:I ShellPermission:W '*:S'
```

遇到系统版本相关崩溃时，优先抓完整 `AndroidRuntime` 栈，并对照设备 `/system/framework/framework.jar` 或对应 AOSP 接口确认真实签名。

## 文档维护

- 本文件是代理协作的权威说明。
- 所有设计文档和实施计划统一存放在 `docs/plans/` 目录，不要创建 `docs/superpowers/specs/`、`docs/superpowers/plans/` 等其他规划文档目录。
- 设计文档和实施计划文件名使用 `YYYY-MM-DD-<主题>-design.md` 与 `YYYY-MM-DD-<主题>-plan.md` 格式。
- 若项目结构、构建版本、兼容策略或开发约定发生变化，应更新本文件。
- `CLAUDE.md` 仅作为 Claude Code 的兼容入口，不维护重复的项目说明。
