# HITA Agent 开发指南

## 项目简介
HITA Agent 是一款面向哈工大三校区的 Android 校园助手 App，集成课表管理、成绩查询、课程资源、AI 助手等功能。

## 技术栈
- **开发语言**：Kotlin
- **最低 SDK**：26 (Android 8.0)
- **目标 SDK**：34 (Android 14)
- **构建工具**：Gradle 8.7, AGP 8.5.2, Kotlin 1.9.22
- **JDK**：17

## 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高
- JDK 17
- Android SDK 26+

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
