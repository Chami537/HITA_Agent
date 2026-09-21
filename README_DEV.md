# HITA Agent 开发指南

## 项目简介
HITA Agent 是一款面向哈工大三校区的 Android 校园助手 App，集成课表管理、成绩查询、课程资源、AI 助手等功能。

## 技术栈
- **开发语言**：Kotlin
- **最低 SDK**：26 (Android 8.0)
- **目标 / 编译 SDK**：35 (Android 15)
- **构建工具**：Gradle 8.11.1, AGP 8.10.1, Kotlin 2.2.21
- **JDK**：17（本地用 JDK 21 亦可构建通过）
- **架构**：MVVM + Repository，Hilt 依赖注入，Room 数据库，WorkManager 定时任务
- **UI**：Jetpack Compose（Material3）与 ViewBinding 混合，Markwon 渲染 Markdown
- **网络**：Retrofit + Jsoup（教务系统 HTML 解析），OkHttp

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
./gradlew connectedAndroidTest   # 仪器测试（需连接设备/模拟器）
./project_health_check.sh        # 项目文件与仓库卫生检查
```

- 所有 Maven 仓库走国内镜像（阿里云 / 腾讯 / JitPack），见 `build.gradle`
- Debug 包使用独立 applicationId `cn.limpu.hita.diag`（应用名"HITA 诊断版"），可与正式版共存安装
- Release 签名需要本地配置 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`）及对应的 `.jks` 文件，二者均被 gitignore，不要提交

## 关键 BuildConfig

在 `app/build.gradle` 中定义：
- `HOA_BASE_URL` — 课程资源后端
- `AGENT_BACKEND_BASE_URL` — AI Agent 后端
- `HOA_API_KEY` — 课程资源 API 密钥（代码中为空，构建时注入）
- `UPDATE_URL` / `UPDATE_VERSION_NAME` / `UPDATE_VERSION_CODE` / `UPDATE_LOG` / `UPDATE_ALLOW_PRERELEASE` — 应用内检查更新
- `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` — LLM 提供商密钥（本地构建留空时走默认配置）

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
- 邮箱：2720649216@qq.com、859635282@qq.com

## 常见陷阱

- **EAS HTML 解析**：三校区（深圳/本部/威海）页面结构不同，改动解析逻辑需同时验证三校区
- **数据库迁移**：Room schema 变更必须升 `@Database` version 并写 migration，否则老用户升级崩溃
- **主线程网络**：所有 EAS / Retrofit 调用必须在协程中执行
- **硬编码中文**：UI 文案放 `strings.xml`（当前仅中文 `values/`）

## 文档与构建产物

完整架构与开发约定见 `CLAUDE.md`（Codex 适配副本为 `AGENTS.md`，内容一致）。使用统计契约和验证范围见 [统计 v2](docs/analytics-v2.md)。设计说明保留在 `docs/superpowers/specs/`；已完成的用户反馈实施清单已从当前树移除，未实施的字体计划保留。

APK 和构建元信息从 CI / Release 获取；源码树不保存旧 `app/debug/` 安装包。`app/schemas/` 和 `hitauser/schemas/` 是 Room 数据库历史结构，供迁移验证使用，不能作为旧生成数据删除。

运行 `./project_health_check.sh` 检查必要项目文件和仓库卫生，`./code_consistency_check.sh` 为兼容入口。检查器验证本地 Markdown 文件链接，并阻止运行数据库、APK 和旧可执行文件重新进入 Git；它不检查外部链接和标题锚点，不使用命名数量作为质量评分。JVM 测试、Debug 编译和中文 PDF 模拟器回归由 CI 执行。

PDF 依赖自带资源的约定、去重依据和回归命令见 [PDF 资源](docs/pdf-resources.md)。
