### 新增

- 新增 IMS 注册状态实时查询功能（使用 `ImsMmTelManager` 读取实际能力状态）
- 新增重置 IMS 功能（`ImsResetter`） （该功能合并自[carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel)）
- 引入 `BrokerInstrumentation` 以增强 IMS 配置覆盖能力 （该功能合并自[carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel)）
- 新增 `ImsCapabilityStatus` 数据类及相关多语言文案

### 优化

- 合并配置按钮，优化主界面布局与交互
- 默认选中卡1，无 SIM 卡时自动回退到全部 SIM 视图
- 优化 Shizuku 状态卡片布局
- 升级依赖库版本

### 移除

- **移除运营商国家码（SIM_COUNTRY_ISO_OVERRIDE_STRING）自定义功能**

  该功能已从本应用中移除。如需自定义运营商国家码，以及网络叹号屏蔽、TikTok 相关修复等更多定制功能，请使用：

  **[carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel)**
