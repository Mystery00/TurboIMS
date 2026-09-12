# 持久化 VoLTE 实施计划

> 执行方式：在当前任务按步骤实施，完成后使用 requesting-code-review 独立审查。

**目标：** 为单张 SIM 提供可回读、可恢复的实验性持久化 VoLTE。
**架构：** Compose → MainViewModel → ShizukuProvider → PersistentVolteModifier；隐藏 API 与原始值恢复封装在 privileged，状态模型位于 model。
**技术：** Kotlin、Compose Material 3、Instrumentation、AtomicFile；不增加依赖。
**设计：** [设计文档](2026-09-12-persistent-volte-design.md)

## 全局约束

- Android 13+，编译目标 SDK 37；沿用统一权限清理 helper。
- 中文文档和注释、英文日志；不要求添加单元测试。
- 原始值 -1/0/1 必须区分；读取错误不能表示为 false。

## 步骤

- [x] 新增 `model/PersistentVolteState.kt`：包含 subId、可空状态、恢复能力、错误及不支持状态。
- [x] 新增 `privileged/PersistentVolteSettings.kt`：反射预检、读取原始值、设置 provisioning 和用户开关、校验与恢复。
- [x] 新增 `privileged/PersistentVolteBackup.kt`：原子保存原始值和 SIM 摘要；读写失败中止修改。
- [x] 新增 `privileged/PersistentVolteModifier.kt` 并注册 Manifest：实现 query/enable/restore，失败回退，所有活动 SIM 的恢复。
- [x] 在 ShizukuProvider 添加入口 `persistentVolte(context, subId, action): PersistentVolteState`，先检查 Shizuku，保留取消语义，解析结果。
- [x] MainViewModel 增加状态查询和独占写入动作，重置时先恢复备份；UI 只触发 ViewModel。
- [x] 新增 `ui/components/PersistentVolteCard.kt`，接入主界面单卡选择；增加中英文案，说明立即生效、恢复含义及功能边界。
- [x] 更新 AGENTS.md 的入口和数据约定。
- [x] 运行 ` .\gradlew.bat :app:assembleDebug --stacktrace --console=plain`，审查 diff、独立评审，修复重要问题并复编译。
- [x] 记录设备可用性、已完成验证及待验证的重启通话矩阵。

## 验证记录

- 2026-09-12：最终代码运行 `.\gradlew.bat :app:assembleDebug --stacktrace --console=plain` 成功，47 个任务，耗时 18 秒。
- `git diff --check` 通过；独立代码评审未发现 Critical、Important 或实质 Minor 问题。
- 本地 SDK 37.2 源码核对了 provisioning、订阅属性、用户开关接口和 ImsManager 恢复顺序。没有新增 AIDL stub。
- `adb devices -l` 没有连接设备，以下实机项目尚未验证：
  1. Android 13–17 的读取、启用、恢复及接口缺失提示。
  2. 原始用户设置为 -1/0/1 的恢复；断开 Shizuku、部分写入失败和重新尝试。
  3. 双卡切换、只修改选中卡、重置所有活动卡、拔卡后的恢复记录保留。
  4. 重启后不启动 App/Shizuku，确认无 CarrierConfig 覆盖，测试实际拨入拨出及 LTE 语音能力。
  5. 实机界面、较大字体和中英文布局。
