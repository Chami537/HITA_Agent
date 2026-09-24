# 实时课程兼容性记录

只记录已在真机或模拟器完成的结果；以下内容不是厂商兼容性承诺。

| OEM | 系统 | Android | Live Update 展示位置 | 状态 | 证据 |
| --- | --- | ---: | --- | --- | --- |
| Google | Pixel UI / Android Emulator | 36 | 待测 | 未测试 | - |
| OPPO / OnePlus | ColorOS | 16 | 待测（可能为流体云） | 未测试 | - |
| Samsung | One UI | - | 系统决定 | 未测试 | - |
| Xiaomi | HyperOS | - | 系统决定 | 未测试 | - |
| HONOR | MagicOS | - | 系统决定 | 未测试 | - |

## 通用降级规则

- Android 16 及以上：请求 promoted ongoing notification，是否展示为系统 Live Update 由设备和用户设置决定。
- 旧版 Android 或 OEM 未提升：继续显示同一份 ongoing 通知。
- 通知权限关闭：不显示，也不保留过期活动。
