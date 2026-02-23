# IMS 实时状态显示设计文档

**日期**：2026-02-23
**状态**：已确认，待实现

## 背景

"查看IMS"对话框当前展示的 VoLTE / VoWiFi / VoNR / 5G NR 状态均来自 `CarrierConfigManager`（运营商配置标志位），
而非实时运行状态。这导致与系统 `*#*#4636#*#*` (Phone Information) 显示的内容不一致。

`*#*#4636#*#*` 使用 `ImsMmTelManager.isAvailable()` 读取 IMS 服务当前实际注册的能力，属于实时数据。

## 目标

将"查看IMS"对话框的所有状态指标改为与 `*#*#4636#*#*` 相同的数据来源，使两者显示内容一致。

## 数据来源变更

| 指标 | 旧数据源（CarrierConfig） | 新数据源（实时 API） |
|---|---|---|
| IMS 注册状态 | `ITelephony.isImsRegistered(subId)` | 保持不变 |
| VoLTE | `KEY_CARRIER_VOLTE_AVAILABLE_BOOL` | `ImsMmTelManager.isAvailable(CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE)` |
| VoWiFi | `KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL` | `ImsMmTelManager.isAvailable(CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN)` |
| VoNR | `KEY_VONR_ENABLED_BOOL && KEY_VONR_SETTING_VISIBILITY_BOOL` | `ImsMmTelManager.isAvailable(CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR)` |
| VT（视频通话） | `KEY_CARRIER_VT_AVAILABLE_BOOL` | `ImsMmTelManager.isAvailable(CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE)` |
| NR NSA | `KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY` | `NetworkRegistrationInfo.getNrState() == NR_STATE_CONNECTED/NOT_RESTRICTED` |
| NR SA | 同上（需同时含 NSA 和 SA） | `NetworkRegistrationInfo.getAccessNetworkTechnology() == NETWORK_TYPE_NR` |

所用 API：
- `android.telephony.ims.ImsMmTelManager`（公开 API，Android 10+）— 需要 `READ_PRECISE_PHONE_STATE` 权限
- `android.telephony.ServiceState` / `NetworkRegistrationInfo`（公开 API，Android 11+）
- `com.android.internal.telephony.ITelephony`（隐藏 API，已有 stub）

权限通过 Shizuku 的 `startDelegateShellPermissionIdentity(uid, null)` 获得，无需新增 stub。

## 新增/修改文件

### 新增

**`privileged/ImsCapabilityReader.kt`**
新的 Instrumentation 类，在特权进程中完成所有数据读取：
1. `startDelegateShellPermissionIdentity`
2. `ITelephony.isImsRegistered(subId)` → `isRegistered`
3. `ImsMmTelManager.createForSubscriptionId(subId).isAvailable(...)` × 4 次
4. `TelephonyManager.createForSubscriptionId(subId).serviceState` → NR NSA/SA
5. `finish(RESULT_OK, bundle)`

Bundle 键常量（定义在 companion object）：
- `BUNDLE_SELECT_SIM_ID`
- `BUNDLE_IMS_REGISTERED`
- `BUNDLE_VOLTE`
- `BUNDLE_VOWIFI`
- `BUNDLE_VONR`
- `BUNDLE_VT`
- `BUNDLE_NR_NSA`
- `BUNDLE_NR_SA`
- `BUNDLE_RESULT_MSG`（错误时写入）

**`model/ImsCapabilityStatus.kt`**
```kotlin
data class ImsCapabilityStatus(
    val isRegistered: Boolean,
    val isVolteAvailable: Boolean,
    val isVoWifiAvailable: Boolean,
    val isVoNrAvailable: Boolean,
    val isVtAvailable: Boolean,
    val isNrNsaAvailable: Boolean,
    val isNrSaAvailable: Boolean,
)
```

### 修改

**`ShizukuProvider.kt`**
- 新增 `readImsCapabilities(context, subId): ImsCapabilityStatus?`（启动 `ImsCapabilityReader`）
- 删除 `readImsRegistrationStatus()` 方法

**`viewmodel/MainViewModel.kt`**
- `loadRealSystemConfig(subId)` 返回类型改为 `ImsCapabilityStatus?`（原为 `Pair<Bundle, Boolean?>`）
- 内部改为调用 `ShizukuProvider.readImsCapabilities()`

**`ui/MainActivity.kt`**
- `SystemConfigDialog` 参数由 `Pair<Bundle, Boolean?>` 改为 `ImsCapabilityStatus`
- 移除对 `FeatureConfigMapper.fromBundle()` 的调用
- 移除原始 CarrierConfig 键值列表区域
- 展示内容：IMS 注册状态 + VoLTE + VoWiFi + VoNR + VT + NR NSA + NR SA（✅/❌）

### 删除

**`privileged/ImsStatusReader.kt`**
不再使用，一并删除。

## 数据流

```
点击"查看IMS"
  → MainViewModel.loadRealSystemConfig(subId)
      → ShizukuProvider.readImsCapabilities(context, subId)
          → ImsCapabilityReader (Instrumentation，特权进程)
              → startDelegateShellPermissionIdentity
              → 读取 isImsRegistered / isAvailable × 4 / ServiceState NR
              → finish(RESULT_OK, bundle)
          → 解析 Bundle → ImsCapabilityStatus
      → 返回 ImsCapabilityStatus?
  → MainActivity 设置 imsCapabilityStatus 状态
  → SystemConfigDialog(imsCapabilityStatus) 显示
```

## UI 展示

```
IMS 状态查看

IMS 注册：      ✅ 已注册  /  ❌ 未注册
VoLTE：         ✅ 可用    /  ❌ 不可用
VoWiFi：        ✅ 可用    /  ❌ 不可用
VoNR：          ✅ 可用    /  ❌ 不可用
VT（视频通话）：  ✅ 可用    /  ❌ 不可用
NR NSA：        ✅ 可用    /  ❌ 不可用
NR SA：         ✅ 可用    /  ❌ 不可用

                                    [确定]
```

## 错误处理

- `ImsCapabilityReader` 用 try-catch 包裹所有调用，异常时写入 `BUNDLE_RESULT_MSG`，能力字段默认 `false`
- `ShizukuProvider.readImsCapabilities()` 若 Bundle 为 null 或含 `result_msg`，返回 `null`
- `MainViewModel` 收到 `null` 时通过 Toast 提示用户"加载失败"

## 不变的内容

- `FeatureConfigMapper`、`ConfigReader`、`ImsModifier`、`ImsResetter` 等——不涉及"查看IMS"功能，不做修改
- `AndroidManifest.xml` 中的 `<instrumentation>` 需新增 `ImsCapabilityReader` 的注册
