# 持久化 VoLTE 设计

## 范围

按已确认的 issue #31 评估方案，在现有主进程、ShizukuProvider、Instrumentation 架构中增加实验性 VoLTE 持久化入口。仅设置 VoIMS opt-in 和 VoLTE 用户开关，不修改 VoNR、VoWiFi 或 5G SA。默认不主动写入；页面展示实时状态。

## 操作与恢复

- 卡片提供“启用”和“恢复原设置”，即时执行，不混入“本次应用”的 Feature 列表。
- 仅允许对单张有效 SIM 启用。读取失败显示未知，不伪装为关闭；不支持的系统明确提示。
- 写入前保存原始订阅值，包括用户开关的 -1（使用运营商默认值）。恢复按钮恢复首次启用前的两个值，而不是强制关闭用户原本已开启的 VoLTE。
- 备份在 noBackupFilesDir 中原子保存，以 subId 和 SIM 标识摘要校验，不备份到云端，不记录明文 SIM 标识。重复启用不覆盖原始备份。
- key 68 写入检查返回码；两个值写入后回读。失败尝试回退本次操作前的状态；回退失败保留恢复记录并报告，不误报成功。
- 现有“重置配置”先恢复目标活动 SIM 的持久化备份，再清除 CarrierConfig。所有 SIM 模式逐卡恢复，未插入的卡保留记录，重新插入后可恢复。

## 兼容与状态

使用系统 ProvisioningManager、ImsMmTelManager、SubscriptionManager 的反射入口，沿用当前隐藏 API 豁免和统一 shell permission delegation，不增加未经设备核实的 AIDL stub。方法缺失时降级为不支持。所有业务动作放入 ViewModel，并与现有写入共用 OperationGate。状态绑定 subId，切卡不显示旧卡结果。

Instrumentation 桥接统一串行调度，避免新工作线程入口与既有读卡、能力查询互相清理 UID 权限委托。主进程等待超时不等于设备端操作停止；上一条 watcher 未返回时，后续请求只等待，不启动新的特权操作。

## 验证

运行 assembleDebug、检查 diff 并独立代码评审。无连接设备时不声称已验证通话。实机需覆盖 Android 13–17、双卡、失败回退、原始 -1 恢复、重启后无 Shizuku 且无 CarrierConfig 覆盖的拨入拨出。系统更新、换卡和卸载后的保留不作保证；卸载前应先恢复，卸载会丢失本地恢复记录。

## 参考

- https://github.com/ryfineZ/carrier-ims-for-pixel/pull/106
- AOSP ImsManager.isVolteEnabledByPlatform、setLocalImsConfigKeyInt
- Android SDK 本地源码 android-37.2
