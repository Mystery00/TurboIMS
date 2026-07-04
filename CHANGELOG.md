## 修复

- 修复 Android 17 QPR1 Beta 6 上 `IActivityManager.stopDelegateShellPermissionIdentity` 签名变化导致的崩溃问题，避免读取 SIM 信息或执行 IMS 配置操作时因停止 shell 权限委托失败而闪退。
- shell 权限委托停止逻辑同时兼容旧版无参接口和新版带 `uid` 接口，保持旧版 Android 与 Android 17 QPR1 Beta 6 都可正常释放权限委托。

## 优化

- 更新 AndroidX、Lifecycle、Activity Compose、Compose BOM 和 Material 3 等依赖版本。