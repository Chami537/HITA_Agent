# 课表单码离线分享（步骤 1～3）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Repository AGENTS.md takes precedence over this plan for commit and push approval; do not dispatch subagents unless the user explicitly requests them.

**Goal:** 在现有 HITA2 口令上增加面对面、全程离线的单张二维码展示和扫码导入，并沿用现有的预览与确认流程。

**Architecture:** 二维码只承载完整的 `HITA2:` 文本；二维码生成、相机识别和口令解析均在 Android 本地完成。生成端在 Q 级容量内用 Q 级纠错，在 M 级容量内用 M 级纠错，超过 M 级则明确提示二维码不可用，保留口令复制与系统分享。接收端将扫码文本交给现有导入 ViewModel，复用 `FriendTimetableRepository.preview`、`TimetableManagerViewModel.parseToken` 及保存流程。

**Tech Stack:** Kotlin、Jetpack Compose、ZXing Core 3.5.3、ZXing Android Embedded 4.3.0、JUnit 4、Gradle Android Debug 构建。

**Spec:** `docs/timetable-share-format-v2.md`（HITA2 数据格式）；本文件的“范围与交互约定”（二维码产品行为）。

## 范围与交互约定

- 不改 HITA2 字段、摘要和好友快照存储格式；继续读取 HITA1 旧口令。
- 一张二维码只包含一个完整口令。管理页或好友页一次导出多个口令时，各行各有“显示二维码”，不把多个口令拼成一张码。
- 分享页继续提供复制和系统分享；扫码入口放在“添加课表 → 口令导入”和“好友课表 → 导入”。扫码取消或相机权限被拒绝时保留原有表单状态。
- 完整模式的二维码可被任何扫码者读取；界面提示分享人这一点。忙闲模式仍由原有口令编码器控制，不在二维码层另行删字段。
- 使用 QR 字节模式的保守容量：含 `HITA2:` 前缀的 ASCII 文本 ≤1663 字符选 Q 级，1664～2331 选 M 级，>2331 不生成；不用 L 级硬塞。最终仍以 ZXing 编码成功为准。
- 显示码使用纯黑白、四模块留白、高分辨率，无叠加图标。二维码仅在弹层可见期间保留内存位图，不落盘、不上传。
- 此阶段不做蓝牙/Wi-Fi 直连、分片、相册识别、服务器中转或两台手机实测。超限课表的单码离线直连是后续独立功能。

## Global Constraints

- Android minSdk 26、targetSdk 35；不要引入依赖 Google Play 服务才能首次离线扫码的方案。
- 在现有独立工作树 `codex/unified-timetable-share-format` 中完成，不在主工作区或 main 上直接编辑。
- Compose 文案加入 `app/src/main/res/values/strings.xml`，复用 `HitaTheme` 与现有导入预览。
- 所有口令生成、二维码编码与解析均离开主线程；取消后不得显示过期结果。
- 提交前必须展示 diff 并取得用户确认；推送前另行确认。本计划本身不授权提交或推送。

## Review Focus

- 口令正好处于 1663、1664、2331、2332 字符边界时，Q/M/超限选择应准确；Task 2 的边界测试覆盖。
- 生成口令后修改课表、昵称或模式，旧二维码不得继续展示；Task 3 扩展 `ShareFormStateTest` 并审查弹层生命周期。
- 扫码得到非 HITA、损坏、超长或多个口令时，不得直接保存；Task 4 的导入测试覆盖。
- 扫码取消或相机权限拒绝时，原输入与预览不得丢失；Task 4 审查 Activity 回调且在交付时明确没有设备级验证。
- 忙闲模式扫码后不得出现课程名和地点，也不得导入为“我的课表”；Task 4 的现有预览/保存测试覆盖。

---

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `app/build.gradle` | 加入本地二维码生成与扫描依赖；确认依赖解析及打包不要求 Play 服务。 |
| `app/src/main/java/cn/limpu/hita/feature/timetableshare/qr/TimetableQrEncoder.kt` | 纯 Kotlin 容量选择和 ZXing `BitMatrix` 生成；返回可展示或超限结果。 |
| `app/src/main/java/cn/limpu/hita/ui/timetable/share/TimetableQrDialog.kt` | 把矩阵绘成黑白位图并展示二维码、容量错误和隐私提示。 |
| `app/src/main/java/cn/limpu/hita/ui/timetable/share/TimetableShareScreen.kt`、`TimetableShareActivity.kt` | 单课表分享页的“显示二维码”操作和取消/状态管理。 |
| `app/src/main/java/cn/limpu/hita/ui/timetable/export/TimetableExportSheet.kt` | 每个口令结果行提供单码入口，复用二维码弹层。 |
| `app/src/main/java/cn/limpu/hita/ui/timetable/manager/import/TimetableImportSheets.kt`、`manager/TimetableManagerActivity.kt` | 我的课表导入弹层中的扫码按钮及扫描结果交接。 |
| `app/src/main/java/cn/limpu/hita/ui/timetable/friend/FriendImportDialog.kt`、`FriendTimetableActivity.kt`、`FriendTimetableViewModel.kt` | 好友课表导入弹层中的扫码按钮、扫描结果预览。 |
| `app/src/main/res/values/strings.xml` | 二维码、扫码、超限、权限及隐私文案。 |
| `app/src/test/java/cn/limpu/hita/feature/timetableshare/qr/TimetableQrEncoderTest.kt` | 容量边界与矩阵本地解码回环。 |
| `app/src/test/java/cn/limpu/hita/ui/timetable/share/ShareFormStateTest.kt`、`ui/timetable/friend/FriendImportUiStateTest.kt` | 旧结果失效和扫码填入后必须重新预览的状态测试。 |

### Task 1: 固定 HITA2 基线

**Files:** 现有 `CompactShareJson.kt`、`TimetableShareCodec.kt`、格式文档及协议测试；这一任务不新增产品文件。

**Interfaces:** 消费 `TimetableShareCodec.encode(snapshot): String`；向后续任务提供 `HITA2:` 口令与 `decodeMessage(message): SharedTimetable`。

- [ ] **Step 1:** 检查 `git diff --check` 与目标分支状态，确认仅有统一分享格式相关改动；核对 HITA1 解码、HITA2 往返、忙闲字段约束与 16 门/256 次样本。
- [ ] **Step 2:** 运行 `./gradlew :app:testDebugUnitTest` 和 `./gradlew :app:assembleDebug`（Windows 在本次命令中设置 `ANDROID_HOME=E:\Android\sdk`）；预期两项退出码均为 0。
- [ ] **Step 3:** 运行 `python scripts/check_repository.py`，展示改动摘要与已知兼容性：旧版应用不能读取新生成的 HITA2 口令。
- [ ] **Step 4:** 仅在用户看过 diff 并明确确认后，用显式文件列表暂存并按 conventional commits 提交 HITA2；不自动推送。

### Task 2: 编码器和容量边界

**Files:** 修改 `app/build.gradle`；新增 `feature/timetableshare/qr/TimetableQrEncoder.kt` 和对应 JVM 测试。

**Interfaces:**

```kotlin
sealed interface TimetableQrResult {
    data class Ready(val matrix: BitMatrix, val level: ErrorCorrectionLevel) : TimetableQrResult
    data object TooLarge : TimetableQrResult
}
fun encodeTimetableQr(token: String): TimetableQrResult
```

`token` 必须以 `HITA2:` 开头且仅含 ASCII；非法输入抛 `IllegalArgumentException`，UI 将其归入生成失败，不展示不完整二维码。

- [ ] **Step 1:** 加入 `com.google.zxing:core:3.5.3` 与 `com.journeyapps:zxing-android-embedded:4.3.0`；先运行 `./gradlew :app:dependencies --configuration debugRuntimeClasspath`，确认镜像能解析且无冲突。
- [ ] **Step 2:** 写边界测试：生成长度 1663、1664、2331、2332 的 `HITA2:` ASCII 口令；断言依次为 Q、M、M、TooLarge。补充实际 `TimetableShareCodec.encode(fullFixture())` 输出，用矩阵像素构造 `RGBLuminanceSource`，经 `HybridBinarizer` 和 `QRCodeReader` 读回原文；非法前缀和非 ASCII 各断言 `IllegalArgumentException`。
- [ ] **Step 3:** 运行 `./gradlew :app:testDebugUnitTest --tests cn.limpu.hita.feature.timetableshare.qr.TimetableQrEncoderTest`，预期新测试因缺少编码器而失败。
- [ ] **Step 4:** 实现 `encodeTimetableQr`：拒绝非 `HITA2:` 或非 ASCII 输入；先按口令总长度选 Q/M/TooLarge；用 `QRCodeWriter`、`BarcodeFormat.QR_CODE`、四模块留白生成矩阵。ASCII 口令沿用 ZXing 默认单字节编码，不额外加入 UTF-8 ECI，以保持 1663/2331 的边界；将 ZXing 的容量异常映射为 TooLarge，不截断口令。
- [ ] **Step 5:** 重跑该测试；预期边界断言和本地读回均通过。检查是否需要把二维码矩阵生成放到 `Dispatchers.Default`，避免 UI 主线程执行。

### Task 3: 发送端展示二维码

**Files:** 新增 `ui/timetable/share/TimetableQrDialog.kt`；修改 `TimetableShareScreen.kt`、`TimetableShareActivity.kt`、`TimetableExportSheet.kt`、`strings.xml`。

**Interfaces:** 消费 Task 2 的 `encodeTimetableQr(token)`；不修改 `TimetableShareCodec` 或仓库的 token 生成签名。

- [ ] **Step 1:** 在现有口令弹层增加“显示二维码”；共用 `TimetableQrDialog(token, onDismiss)`，在导出结果中为每个单独口令增加相同入口。打开前后均保留复制与系统分享按钮。
- [ ] **Step 2:** 弹层在后台生成 1024×1024 黑白图，保留四模块白边；在生成中显示进度，超过 M 级容量显示明确的“此课表内容超过单张二维码容量，可继续使用口令分享”。显示完整模式的“扫码者可读取课程与地点”提示。
- [ ] **Step 3:** 以 token 为异步生成任务的 key；关闭弹层或 token 因选课表、昵称、分享模式而失效时，取消旧任务并释放位图，旧结果不得重新出现。
- [ ] **Step 4:** 扩展 `ShareFormStateTest`，分别断言修改课表、昵称、分享模式都清除原 token；审查 Compose 弹层取消逻辑，确认超限仅影响二维码、口令操作仍可用。运行 `./gradlew :app:assembleDebug`。

### Task 4: 接收端扫码并复用导入

**Files:** 修改 `TimetableImportSheets.kt`、`TimetableManagerActivity.kt`、`FriendImportDialog.kt`、`FriendTimetableActivity.kt`、`FriendTimetableViewModel.kt`、`strings.xml`；检查合并后的 `AndroidManifest.xml`，若库 Manifest 未声明相机权限，再在应用 Manifest 添加 `android.permission.CAMERA`。

**Interfaces:** 扫描成功只输出 `String`；管理页调用 `viewModel.editTokenInput(text)` 后 `viewModel.parseToken(text)`；好友页提供 `viewModel.acceptScannedToken(text)`，其内部更新 `form` 并调用既有 `parse()`。保存仍经原有“导入为好友/我的课表”或好友确认按钮。

- [ ] **Step 1:** 在两个现有导入弹层加“扫码”按钮；Activity 用 `registerForActivityResult(ScanContract())` 启动只识别 QR_CODE 的 `ScanOptions`。扫描、ZXing 解码、口令预览均在本地完成。
- [ ] **Step 2:** 结果为空视为取消且不改表单；非空结果填入口令输入并自动执行原有解析，展示既有预览后才允许保存。权限拒绝或相机失败显示可理解的错误，粘贴导入继续可用。
- [ ] **Step 3:** 扩展 `FriendImportUiStateTest`，断言新扫码文本经 `edit` 后旧预览失效、旧异步解析结果被忽略；扩展 `TimetableShareCodecTest`，断言有效 HITA2/HITA1、损坏、超长和多个口令经同一解码入口得到原有结果或错误。复核 `TokenImportSheet` 的忙闲模式限制仍禁止导入为“我的课表”。
- [ ] **Step 4:** 检查 Manifest 合并结果及相机权限请求路径；审查取消/拒权回调是否在修改表单前返回。运行目标 JVM 测试、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`、`python scripts/check_repository.py` 和 `git diff --check`。不以软件回环证明真实手机扫码体验。

### Task 5: 交付审查

**Files:** 本计划涉及的所有改动。

- [ ] **Step 1:** 审查实际 diff 是否仍限定在 HITA2 基线、二维码生成展示、扫码导入；确认没有服务器请求、临时图片文件或调试输出。
- [ ] **Step 2:** 汇报测试命令、通过数量、依赖解析、二维码容量边界以及未做双机实测这一限制。
- [ ] **Step 3:** 展示拟提交的显式文件清单和 commit message；等待用户确认后提交。推送另行确认。
