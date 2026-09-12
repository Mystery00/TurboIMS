# 自动恢复 IMS 配置实施计划

> 执行方式：在当前会话分步实现；完成后使用 requesting-code-review 技能进行独立审查。

**目标：** 提供默认关闭、用户主动启用的 Shizuku 自动授权与配置恢复功能。

**架构：** ConfigurationRepository 负责历史与恢复元数据；AutoRestoreController 负责服务事件和有限重试；ConfigurationOperations 串行协调界面与后台操作。

**技术栈：** Kotlin、协程、SharedPreferences、Jetpack Compose、现有 Shizuku API。

**设计：** [自动恢复设计](2026-09-12-auto-restore-design.md)

## 全局约束

- Android 13 及以上，不增加依赖或更改隐藏 API 签名。
- 沟通、文档、新增注释使用中文；日志使用英文。
- 默认关闭，不自动启动 Shizuku，不恢复持久化 VoLTE，不自动应用默认配置。
- 所有成功写入继续使用原有 Instrumentation 与 Broker 回退。
- 不新增单元测试；必须完成 Debug 编译。

## 任务一：提取配置存储与操作协调

- [x] 新增 `ConfigurationRepository.kt`，提供 `load(subId)`、`save(subId, config)`、`recordReset(subId)`、`restoreTarget(subId)` 与 `buildBundle(subId, config)`，迁移 ViewModel 中现有读写及构造逻辑。
- [x] 使用单调序号选择最新全卡或单卡操作；重置保留历史并写入恢复阻止标记。
- [x] 新增 `ConfigurationOperations.kt`，以 `Mutex.withLock` 和状态流串行协调业务操作；手动成功应用同步记录当次开机恢复进度。

## 任务二：自动恢复及用户开关

- [x] 新增 `AutoRestoreController.kt`：注册 sticky Binder 监听、死亡监听和权限回调；默认关闭；先检查历史，再申请权限；使用独立请求码。
- [x] 以 `Settings.Global.BOOT_COUNT` 记录成功恢复与自动请求周期；检查拒绝状态，限制重复请求；恢复仅遍历活动 SIM，失败有限重试，成功才记录进度。
- [x] `Application.kt` 初始化协调器；ViewModel 暴露开关状态和用户动作；MainActivity 增加开关卡片及中英文资源。
- [x] 关闭功能时停止后续调度，每次写入前重新检查开关，防止迟到回调继续恢复。

## 任务三：文档与验证

- [x] 更新 AGENTS.md，说明默认关闭、恢复规则和新增组件。
- [x] 运行 `./gradlew.bat :app:assembleDebug --stacktrace --console=plain` 和 `git diff --check`。
- [x] 检查 adb 设备；没有设备时明确说明未完成真机验证。
- [x] 独立审查自动授权、并发、全卡/单卡优先级和重置逻辑，修复重要问题并按需重新编译。

## 验证结果

- 2026-09-12：Debug 编译两次通过，第二次包含独立审查修正。
- `git diff --check` 通过。
- 独立审查发现并修复：关闭开关后等待中的主路径/Broker 启动检查，以及自动恢复结束后持久化 VoLTE 延迟查询的唤醒；复核通过。
- `adb devices -l` 未发现设备，授权弹窗、重启恢复和双卡行为尚未进行真机验证。

## 自动应用结果提示补充

- [x] 界面明确 Shizuku 启动、连接并授权后才自动应用配置，不将设备重启描述为直接触发条件。
- [x] 一轮自动应用按 SIM 汇总结果，优先 Toast，5 秒未确认显示时取消排队 Toast 并尝试通知兜底。
- [x] 新增通知渠道与 POST_NOTIFICATIONS 前台授权；拒绝通知仍可自动应用，渠道或应用通知关闭时提供对应设置入口。
- [x] 补充实现通过 Debug 编译、差异检查和独立代码审查。
- [x] 后续检测到 Pixel 6 Pro（API 37），已覆盖安装最新 Debug APK，主 Activity 冷启动成功，检查日志未发现本次启动的 AndroidRuntime 异常。
- 设备当前处于锁屏，尚未完成界面目视检查及 Toast 被系统抑制后的通知兜底真机验证。

## 解锁并插卡后的真机验证

- Pixel 6 Pro（Android 17 / API 37）识别到一张中国电信 SIM，Shizuku 已就绪，自动应用开关已开启。
- 使用已有保存配置重新启用自动应用，屏幕截图确认显示“已自动应用 1 张 SIM 的配置”；日志记录自动应用成功，本次开机的成功标记已写入。
- 界面触发条件文案显示完整；通知权限已授予，结果通知渠道已开启。未发现本次检查的 AndroidRuntime 崩溃。
- 临时将本应用 TOAST_WINDOW 设为 ignore 并未抑制该系统的文本 Toast，故没有实际触发通知兜底；此项不能记为通过。测试后已恢复 default，配置功能值未调整。

## 通知兜底的确定性真机验证

- 使用临时、仅 Debug 可用且要求 DUMP 权限的广播探针，直接调用未修改的 AutoRestoreNotifier；只注入结果提示，不执行 SIM 配置写入。
- 超时场景：仅在 showResult 同步调用期间代理 Toast 缓存服务，拦截 enqueueTextToast 后立即恢复原服务。20:24:28 确认 1 秒时尚无通知，20:24:33 确认超时后真实通知已发布至 auto_apply_results 渠道，标题和正文分别为“自动应用配置结果”和“已自动应用 1 张 SIM 的配置”。通知栏截图已确认；点击后返回 MainActivity，通知自动取消。
- 异常场景：在同一入口注入 Toast 提交异常，20:25:23 正式错误边界捕获异常，随后立即发布结果通知；1 秒检查确认通知存在。两次广播探针均返回 PASS。
- 上述验证模拟 Toast 无回调或抛异常，验证真实兜底逻辑与系统通知投递，不代表复现了该机系统自然抑制 Toast 的条件。
- 正式 AutoRestoreNotifier 文件验证前后 SHA-256 均为 5BB48B775F467D61E2FD29CF4BF449C8A96E99472E84724F5F2C6C0ED0EB12A0。
- 临时探针和 Debug 清单已删除，重新构建普通 Debug APK 并覆盖安装，确认已安装包不再包含探针；开关、配置进度及 Toast 系统设置保持原状。
- 验证截图与设备日志保存在 app/build/verification/notification-fallback/，属于可清理的构建验证产物。
