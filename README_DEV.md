# HITA Agent 开发指南

## 项目简介
HITA Agent 是一款面向哈工大三校区的 Android 校园助手 App，集成课表管理、成绩查询、课程资源、AI 助手等功能。

## 技术栈
- **开发语言**：Kotlin
- **最低 SDK**：26 (Android 8.0)
- **目标 / 编译 SDK**：35 (Android 15)
- **构建工具**：Gradle 8.11.1, AGP 8.10.1, Kotlin 2.2.21
- **JDK**：17

## 环境要求
- Android Studio 2025.3.2 或更高
- JDK 17
- Android SDK Platform 35（应用最低支持 API 26）

## 项目结构

```
HITA_Agent/
├── app/           # 主应用模块 — UI、业务逻辑、数据层、Agent 系统
├── component/     # 共享基础组件 — DataState、Result、SharedPreferenceLiveData
├── hitauser/      # 用户模块 — 认证、个人信息、独立 Room DB
├── style/         # UI 基础 — BaseActivity/Fragment、自定义 Widget、主题工具
└── sync/          # 数据同步（暂未编译进主工程）
```

详见 `CLAUDE.md` 了解完整架构。

## 构建

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew testDebugUnitTest      # 单元测试
```

## 关键 BuildConfig

在 `app/build.gradle` 中定义：
- `HOA_BASE_URL` — 课程资源后端
- `AGENT_BACKEND_BASE_URL` — AI Agent 后端
- `HOA_API_KEY` — API 密钥

## 权限

- `INTERNET` / `ACCESS_NETWORK_STATE`
- `POST_NOTIFICATIONS`（Android 13+）
- `REQUEST_INSTALL_PACKAGES`（应用内更新）

## 贡献

1. Fork 项目
2. 创建功能分支 (`git checkout -b feat/xxx`)
3. 提交 (`git commit -m 'feat: xxx'`)
4. 推送到分支并开启 PR

## 联系方式

- 问题反馈：[Issues](https://github.com/HIT-A/HITA_Android/issues)
- 邮箱：2720649216@qq.com

## 文档与构建产物

使用统计契约和验证范围见 [统计 v2](docs/analytics-v2.md)。设计说明保留在 `docs/superpowers/specs/`；已完成的用户反馈实施清单已从当前树移除，未实施的字体计划保留。

APK 和构建元信息从 CI / Release 获取；源码树不保存旧 `app/debug/` 安装包。`app/schemas/` 和 `hitauser/schemas/` 是 Room 数据库历史结构，供迁移验证使用，不能作为旧生成数据删除。
