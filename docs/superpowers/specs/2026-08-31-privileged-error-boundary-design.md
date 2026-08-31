# 特权操作错误边界修复设计

## 背景

TurboIMS 通过主进程中的 `ShizukuProvider` 启动多个特权 Instrumentation，再由 Instrumentation 使用 shell permission delegation 调用 CarrierConfig 和 Telephony 隐藏接口。

当前错误边界分散在各入口中，存在以下问题：

1. `startInstrumentation()` 的 Boolean 返回值被忽略，watcher 等待没有超时。
2. SIM 尚未选中时，重置按钮可能解引用空值。
3. shell permission delegation 的启动或清理异常可能逃逸，导致 Instrumentation 不执行 `finish()`。
4. 反射调用产生的 `InvocationTargetException` 隐藏了真实权限异常，使 Broker fallback 无法可靠触发。
5. 配置尚未成功时就保存了本地历史。
6. 旧版 Shizuku 的 `NEED_UPDATE` 状态会被后续状态覆盖。

## 目标

- 所有 Instrumentation 操作在 15 秒内明确成功或失败，不永久等待。
- shell permission delegation 的启动、业务执行和清理具有统一且可预测的错误边界。
- 返回真实根因，使 Broker fallback 能识别权限错误。
- 消除重置按钮空指针风险。
- 只保存成功应用的配置历史。
- 正确保留旧版 Shizuku 状态。

## 非目标

- 不重写现有 Bundle 返回协议。
- 不改为常驻 Shizuku UserService。
- 不处理 SIM 空列表、日志生命周期、签名配置等其他审查问题。
- 不对 CarrierConfig 多 SIM 写入增加事务或回滚。

## 方案选择

采用集中错误边界方案：新增小型特权执行 helper，复用现有 Instrumentation、Bundle 和 Broker 架构。

相比逐文件打补丁，该方案减少重复的 delegation 和异常处理逻辑；相比重做强类型 AIDL/Result 协议，改动范围更小，更适合当前修复。

## 主进程桥接设计

`ShizukuProvider.startInstrumentation()` 将执行以下流程：

1. 调用 AMS `startInstrumentation()`。
2. 若返回 `false`，立即返回启动失败，不进入 watcher 等待。
3. 需要结果时，在 15 秒超时范围内等待 `instrumentationFinished()`。
4. 超时时返回明确错误 Bundle 或调用层可识别的失败结果。
5. 捕获 Binder、Shizuku 和启动异常并记录英文日志。

所有现有调用入口继续复用该方法，因此无需分别实现超时。

## 特权执行 helper

新增 helper，负责一个 Instrumentation 请求的统一生命周期：

1. 创建结果 Bundle。
2. 获取 Shizuku 包装后的 `IActivityManager`。
3. 在顶层异常边界内启动 shell permission delegation。
4. 仅在 delegation 成功后执行业务 lambda。
5. 在独立清理块中停止 delegation。
6. 清理异常只追加为 suppressed 或记录日志，不覆盖原业务异常。
7. 将最终结果交由调用方一次性执行 `finish()`。

迁移范围：

- `ImsModifier`
- `BrokerInstrumentation`
- `ImsCapabilityReader`
- `ImsResetter`
- `SimReader`

各入口保留自己的参数解析和业务逻辑，不把 CarrierConfig 或 Telephony 细节放入 helper。

## 异常根因处理

新增纯 Kotlin 异常工具：

- 递归展开 `InvocationTargetException.targetException`。
- 必要时继续沿 `cause` 查找最有意义的根因。
- 生成稳定错误描述：根因类名加非空消息。
- 提供权限错误判定，识别 `SecurityException`、`MODIFY_PHONE_STATE` 和 CarrierConfig 写权限错误。

`ImsModifier` 返回根因描述，`ShizukuProvider.shouldRetryWithBroker()` 使用统一权限错误判定，而不是仅依赖外层异常消息。

为避免改动现有 Bundle 协议，本次仍通过 `BUNDLE_RESULT_MSG` 返回错误文本。

## UI 与状态修复

### 重置按钮

- 应用与重置按钮共享 `selectedSim != null` 的启用条件。
- 回调捕获当前 `SimSelection` 快照并使用空安全调用。
- Shizuku 非 READY 时保留现有提示路径。

### 配置历史

`MainViewModel.onApplyConfiguration()` 调整为：

1. 构建当前配置快照。
2. 执行系统修改。
3. 仅在返回成功时保存 SharedPreferences。
4. 失败时保留上一次成功配置。

### Shizuku 状态

`updateShizukuStatus()` 将旧版本判断作为最高优先级分支。检测到 `isPreV11()` 后设置 `NEED_UPDATE` 并结束本次更新。

## 测试与验证

项目不要求全面新增单元测试，但异常根因展开和权限错误判定属于纯逻辑，将优先增加小型测试覆盖：

- `InvocationTargetException(SecurityException)` 能展开到真实根因。
- 错误描述包含根因类型和消息。
- 权限错误能触发 Broker fallback 判定。
- 普通异常不会误触发 fallback。

完成后运行：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
.\gradlew.bat lint --stacktrace --console=plain
```

若 lint 仍有与本轮无关的既有告警，应明确记录；本轮涉及的权限和异常处理错误必须解决或进行有依据的局部说明。

## 风险与兼容性

- Android 13 至 Android 17 的隐藏接口仍依赖真实 Pixel framework；helper 不改变接口签名，只收紧生命周期和错误处理。
- 15 秒超时可能在极端系统负载下提前失败，但优于永久挂起；用户可重新操作。
- 清理 delegation 失败时系统侧身份状态由 AMS/Binder 生命周期兜底，应用必须记录该异常并完成 watcher 回调。
- Broker fallback 仍依赖错误原因可识别，但解包反射异常后比字符串匹配外层异常可靠。
