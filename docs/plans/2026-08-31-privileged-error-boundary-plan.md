# 特权操作错误边界修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Instrumentation 永久等待、重置空指针、shell permission delegation 异常逃逸、Broker fallback 失效、失败配置覆盖历史和旧版 Shizuku 状态覆盖六个问题。

**Architecture:** 保留现有主进程、Instrumentation、Bundle 和 Broker 架构。新增纯 Kotlin 异常工具和小型 delegation 生命周期 helper；主进程桥接统一检查启动结果并设置 15 秒超时，UI/ViewModel 只做必要的状态修复。

**Tech Stack:** Kotlin 2.4.10、Android SDK 37、Shizuku、Instrumentation、CarrierConfig、Kotlin Coroutines、Jetpack Compose、kotlin.test。

**Spec:** `docs/plans/2026-08-31-privileged-error-boundary-design.md`

## Global Constraints

- 所有 Instrumentation 操作最多等待 15 秒。
- 保持现有 Bundle 返回协议，不引入新的 AIDL 或常驻服务。
- 兼容 Android 13 至 Android 17，不改变隐藏 API stub 签名。
- 注释使用中文，日志 message 使用英文。
- 不处理 SIM 空列表、日志生命周期、签名配置等本轮范围外问题。
- 完成后必须运行 `./gradlew.bat :app:assembleDebug --stacktrace --console=plain` 和 `./gradlew.bat lint --stacktrace --console=plain`。

---

## 文件结构

- 新增 `app/src/main/java/io/github/vvb2060/ims/privileged/PrivilegedError.kt`：反射异常展开、稳定错误描述和 Broker 权限错误判断。
- 新增 `app/src/main/java/io/github/vvb2060/ims/privileged/PrivilegedOperation.kt`：统一 shell permission delegation 生命周期。
- 新增 `app/src/test/java/io/github/vvb2060/ims/privileged/PrivilegedErrorTest.kt`：纯逻辑回归测试。
- 修改 `app/build.gradle.kts`：增加 `testImplementation(kotlin("test"))`。
- 修改五个 `privileged/` Instrumentation：复用统一 helper，并确保一次性 `finish()`。
- 修改 `ShizukuProvider.kt`：检查启动结果、15 秒超时和统一 Broker 判断。
- 修改 `MainActivity.kt`、`MainViewModel.kt`：修复重置空值、成功后保存和旧版 Shizuku 状态。

---

### Task 1: 异常根因与权限错误工具

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/privileged/PrivilegedError.kt`
- Create: `app/src/test/java/io/github/vvb2060/ims/privileged/PrivilegedErrorTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `fun Throwable.privilegedRootCause(): Throwable`
- Produces: `fun Throwable.toPrivilegedErrorMessage(): String`
- Produces: `fun isCarrierConfigPermissionError(message: String): Boolean`
- Consumes: Java `InvocationTargetException` 和现有 Broker 错误文本。

- [ ] **Step 1: 增加测试依赖**

在 `app/build.gradle.kts` 的 dependencies 中加入：

```kotlin
testImplementation(kotlin("test"))
```

- [ ] **Step 2: 编写失败测试**

创建测试覆盖四个行为：

```kotlin
package io.github.vvb2060.ims.privileged

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivilegedErrorTest {
    @Test
    fun unwrapsInvocationTargetException() {
        val cause = SecurityException("denied")
        val wrapped = InvocationTargetException(cause)
        assertEquals(cause, wrapped.privilegedRootCause())
    }

    @Test
    fun formatsRootCauseTypeAndMessage() {
        val wrapped = InvocationTargetException(SecurityException("denied"))
        assertEquals("SecurityException: denied", wrapped.toPrivilegedErrorMessage())
    }

    @Test
    fun recognizesCarrierConfigPermissionFailure() {
        assertTrue(isCarrierConfigPermissionError("SecurityException: No permission to write to carrier config"))
        assertTrue(isCarrierConfigPermissionError("SecurityException: missing android.permission.MODIFY_PHONE_STATE"))
    }

    @Test
    fun ignoresUnrelatedFailure() {
        assertFalse(isCarrierConfigPermissionError("IllegalStateException: phone service unavailable"))
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "io.github.vvb2060.ims.privileged.PrivilegedErrorTest" --stacktrace --console=plain
```

Expected: FAIL，提示三个生产函数尚不存在。

- [ ] **Step 4: 实现最小异常工具**

`PrivilegedError.kt` 实现以下逻辑：

```kotlin
package io.github.vvb2060.ims.privileged

import java.lang.reflect.InvocationTargetException

fun Throwable.privilegedRootCause(): Throwable {
    var current = this
    val visited = mutableSetOf<Throwable>()
    while (visited.add(current)) {
        current = when (current) {
            is InvocationTargetException -> current.targetException ?: return current
            else -> current.cause ?: return current
        }
    }
    return current
}

fun Throwable.toPrivilegedErrorMessage(): String {
    val root = privilegedRootCause()
    val name = root.javaClass.simpleName.ifBlank { root.javaClass.name }
    return root.message?.takeIf { it.isNotBlank() }?.let { "$name: $it" } ?: name
}

fun isCarrierConfigPermissionError(message: String): Boolean {
    return message.contains("SecurityException", ignoreCase = true) ||
        message.contains("android.permission.MODIFY_PHONE_STATE", ignoreCase = true) ||
        message.contains("No permission to write to carrier config", ignoreCase = true)
}
```

- [ ] **Step 5: 运行测试确认通过**

Run 同 Step 3。

Expected: `PrivilegedErrorTest` 全部 PASS。

- [ ] **Step 6: 提交任务**

```bash
git add app/build.gradle.kts app/src/main/java/io/github/vvb2060/ims/privileged/PrivilegedError.kt app/src/test/java/io/github/vvb2060/ims/privileged/PrivilegedErrorTest.kt
git commit -m "新增特权异常根因处理"
```

---

### Task 2: 统一 shell permission delegation 生命周期

**Files:**
- Create: `app/src/main/java/io/github/vvb2060/ims/privileged/PrivilegedOperation.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/privileged/ImsModifier.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/privileged/BrokerInstrumentation.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/privileged/ImsCapabilityReader.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/privileged/ImsResetter.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/privileged/SimReader.kt`

**Interfaces:**
- Consumes: `Throwable.toPrivilegedErrorMessage()` from Task 1。
- Produces: `fun Instrumentation.runWithShellPermissionDelegation(tag: String, block: () -> Unit): Throwable?`
- Contract: 返回 `null` 表示业务和清理均成功；返回 Throwable 表示 delegation 启动、业务或清理失败。

- [ ] **Step 1: 创建 delegation helper**

实现：

```kotlin
package io.github.vvb2060.ims.privileged

import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.ServiceManager
import android.system.Os
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper

fun Instrumentation.runWithShellPermissionDelegation(
    tag: String,
    block: () -> Unit,
): Throwable? {
    var activityManager: IActivityManager? = null
    var delegationStarted = false
    var failure: Throwable? = null
    try {
        val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            ?: error("activity service unavailable")
        activityManager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        Log.i(tag, "starting shell permission delegation")
        activityManager.startDelegateShellPermissionIdentity(Os.getuid(), null)
        delegationStarted = true
        block()
    } catch (t: Throwable) {
        failure = t
    } finally {
        if (delegationStarted) {
            try {
                activityManager?.stopDelegateShellPermissionIdentityCompat()
                Log.i(tag, "stopped shell permission delegation")
            } catch (cleanup: Throwable) {
                Log.e(tag, "failed to stop shell permission delegation", cleanup)
                if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
            }
        }
    }
    return failure
}
```

- [ ] **Step 2: 迁移 ImsModifier 和 BrokerInstrumentation**

要求：

- 将业务方法中的 delegation 启动/停止代码移除。
- `onCreate()` 调用 helper 执行业务。
- 使用 `failure.toPrivilegedErrorMessage()` 写入 `BUNDLE_RESULT_MSG`。
- 无论成功或失败，最后只调用一次 `finish(Activity.RESULT_OK, result)`。
- `ImsModifier` 原有等待 Shizuku Binder 的逻辑保留在 helper 调用之前，但所有后续异常必须进入结果 Bundle。

- [ ] **Step 3: 迁移 ImsCapabilityReader 和 ImsResetter**

要求与 Step 2 相同；另外：

- `ImsCapabilityReader` 的 `tm.serviceState` 保留 shell delegation 语义，在该行增加局部且有中文理由的 `@SuppressLint("MissingPermission")` 或方法级注解。
- 注解只能用于特权 Instrumentation 业务方法，不得扩大到普通 UI/ViewModel。

- [ ] **Step 4: 迁移 SimReader**

`SimReader.start()` 使用 helper 构建结果，结束后统一调用一次 `finish()`。读取异常时记录根因并返回 `Activity.RESULT_CANCELED`；delegation 清理异常同样必须触发失败结果。

- [ ] **Step 5: 编译验证迁移结果**

Run:

```powershell
./gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 运行异常工具测试**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "io.github.vvb2060.ims.privileged.PrivilegedErrorTest" --stacktrace --console=plain
```

Expected: PASS。

- [ ] **Step 7: 提交任务**

```bash
git add app/src/main/java/io/github/vvb2060/ims/privileged
git commit -m "统一特权权限委托生命周期"
```

---

### Task 3: Instrumentation 启动检查、超时与 Broker fallback

**Files:**
- Modify: `app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt`

**Interfaces:**
- Consumes: `isCarrierConfigPermissionError(message: String)` from Task 1。
- Produces: 所有需要结果的 Instrumentation 最多等待 `15_000L` 毫秒。
- Existing return contract: `Bundle?`，失败继续返回 `null`，调用方保留现有错误处理。

- [ ] **Step 1: 增加超时常量和 coroutine API**

加入：

```kotlin
private const val INSTRUMENTATION_TIMEOUT_MS = 15_000L
```

并导入：

```kotlin
import kotlinx.coroutines.withTimeoutOrNull
```

- [ ] **Step 2: 检查 startInstrumentation 返回值**

将启动调用改为：

```kotlin
val started = am.startInstrumentation(
    name,
    null,
    flags,
    args,
    watcher,
    connection,
    0,
    null
)
if (!started) {
    Log.e(TAG, "instrumentation start rejected for component: $name")
    return null
}
Log.i(TAG, "instrumentation started successfully")
```

- [ ] **Step 3: 增加 15 秒 watcher 超时**

需要结果时执行：

```kotlin
val result = withTimeoutOrNull(INSTRUMENTATION_TIMEOUT_MS) {
    deferredResult.await()
}
if (result == null) {
    Log.e(TAG, "instrumentation result timeout for component: $name")
}
return result
```

不需要结果时维持现有 `null` 返回语义。

- [ ] **Step 4: 替换 Broker 字符串猜测**

`shouldRetryWithBroker()` 调整为：

```kotlin
private fun shouldRetryWithBroker(msg: String): Boolean {
    return isCarrierConfigPermissionError(msg) ||
        msg.contains("failed with empty result", ignoreCase = true)
}
```

- [ ] **Step 5: 编译并运行测试**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain
./gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: 全部成功。

- [ ] **Step 6: 提交任务**

```bash
git add app/src/main/java/io/github/vvb2060/ims/ShizukuProvider.kt
git commit -m "限制特权 Instrumentation 等待时间"
```

---

### Task 4: UI 空值、配置历史与 Shizuku 状态

**Files:**
- Modify: `app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt`
- Modify: `app/src/main/java/io/github/vvb2060/ims/viewmodel/MainViewModel.kt`

**Interfaces:**
- Consumes: 现有 `Buttons`、`SimSelection`、`ShizukuStatus` 和 `overrideImsConfig()` 返回协议。
- Produces: 应用与重置共享 `selectedSim != null` 启用条件；仅成功配置保存历史；旧 Shizuku 保持 `NEED_UPDATE`。

- [ ] **Step 1: 修复 Shizuku 状态优先级**

将 `updateShizukuStatus()` 改为：

```kotlin
fun updateShizukuStatus() {
    viewModelScope.launch {
        if (Shizuku.isPreV11()) {
            _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
            return@launch
        }
        _shizukuStatus.value = when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuStatus.NO_PERMISSION
            else -> ShizukuStatus.READY
        }
    }
}
```

- [ ] **Step 2: 成功后保存配置历史**

从 `onApplyConfiguration()` 开头移除 `saveConfiguration()`；在：

```kotlin
if (resultMsg == null) {
```

分支内先执行：

```kotlin
saveConfiguration(selectedSim.subId, map)
```

然后显示成功 Toast。失败分支不得修改历史。

- [ ] **Step 3: 修复重置按钮空值**

调用 `Buttons` 时计算：

```kotlin
val hasSelectedSim = selectedSim != null
```

传入统一启用状态，并将两个回调改为先捕获：

```kotlin
val currentSim = selectedSim ?: return@Buttons
```

再调用 ViewModel。`Buttons` 的参数从 `isApplyButtonEnabled` 重命名为 `isActionEnabled`，并同时传给应用和重置 Button 的 `enabled`。

- [ ] **Step 4: 编译验证**

Run:

```powershell
./gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 运行 lint**

Run:

```powershell
./gradlew.bat lint --stacktrace --console=plain
```

Expected: 本轮涉及的 `ImsCapabilityReader.serviceState` MissingPermission 错误消失；允许记录与本轮无关的既有 warnings，但不接受新的 error。

- [ ] **Step 6: 最终检查**

Run:

```bash
git diff --check
git status --short
```

确认没有遗漏新增文件或非预期修改。

- [ ] **Step 7: 提交任务**

```bash
git add app/src/main/java/io/github/vvb2060/ims/ui/MainActivity.kt app/src/main/java/io/github/vvb2060/ims/viewmodel/MainViewModel.kt
git commit -m "修复配置操作状态与历史保存"
```

---

### Task 5: 全量复核

**Files:**
- Review only: 本计划涉及的所有生产代码、测试和 Gradle 文件。

**Interfaces:**
- Consumes: Task 1-4 的全部变更。
- Produces: 可发布前的代码审查结论和验证证据。

- [ ] **Step 1: 运行完整验证**

```powershell
./gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain
./gradlew.bat :app:assembleDebug --stacktrace --console=plain
./gradlew.bat lint --stacktrace --console=plain
```

Expected: 单元测试与 Debug 编译通过；lint 不包含本轮相关 error。

- [ ] **Step 2: 请求独立代码审查**

审查重点：

- 是否仍存在 watcher 永久等待路径。
- delegation 启动或清理异常是否都能到达 `finish()`。
- `InvocationTargetException(SecurityException)` 是否能触发 Broker。
- 重置按钮是否仍可能解引用 null。
- 失败配置是否仍会修改历史。
- `NEED_UPDATE` 是否仍可能被覆盖。

- [ ] **Step 3: 修复阻断问题并重新验证**

仅修复审查确认的 Critical/Important 问题；每次修复后重新运行 Task 5 Step 1。

- [ ] **Step 4: 提交复核修正**

如有修正：

```bash
git add app app/build.gradle.kts
git commit -m "完善特权操作错误边界"
```

如无修正，不创建空提交。
