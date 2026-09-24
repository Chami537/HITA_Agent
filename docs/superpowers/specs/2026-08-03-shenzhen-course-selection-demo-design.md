# 深圳教务单次选课与定时任务 Demo 设计

## 目标

在 HITA 现有深圳“可选课程”目录中加入真实教务选课能力，并移植 Class 原型中的定时任务、任务监控和多课程并发体验。

Demo 只支持深圳校区。每个课程 ID 在一次任务中最多发送一次选课 POST，不对失败、超时或认证异常自动重发。提交后的状态确认仅使用只读的“已选课程”查询。

## 范围

包含：

- 在现有深圳可选课程卡片中选择课程。
- 立即提交和精确到秒的单次定时提交。
- 每批最多 20 门课程，课程 ID 去重，最多 10 门不同课程并发。
- 任务持久化、执行前取消、App 重启恢复和手机重启后重新注册闹钟。
- 任务及逐课程状态监控。
- 提交后查询已选课程并确认结果。
- Android 12 及以上的精确定时权限检查与授权引导。

不包含：

- 本部和威海选课。
- 同一课程重复发包。
- POST 自动重试、循环抢课、余量轮询或蹲守模式。
- 后台保存账号、密码或 Cookie 副本。
- Room 数据库迁移。

## 用户流程

### 选择课程

只有 `ShenzhenCourseCatalogSource.AVAILABLE` 下的课程卡片显示“加入选课计划”操作。已加入的课程显示选中状态，页面底部显示已选数量以及“立即提交”“定时提交”两个操作。

一批任务最多选择 20 门课程。重复选择相同课程 ID 时只保留一项。离开页面时保留当前草稿，执行或明确清空后移除。

### 立即提交

用户点击“立即提交”后看到二次确认对话框，其中显示课程数量、课程名称和“该操作会在真实教务系统产生选课结果”的提示。确认后创建任务并立即执行。

### 定时提交

用户选择本机时区下的日期和精确到秒的时间。时间必须至少晚于当前时间 500 毫秒，且最多提前 24 小时。

Android 12 及以上若没有精确定时权限，界面跳转系统授权页；授权前不创建任务，也不静默降级到 WorkManager。

任务创建后由 `AlarmManager.setExactAndAllowWhileIdle()` 注册。闹钟触发时由 BroadcastReceiver 启动短生命周期前台服务，通知栏持续显示任务状态。

### 监控与取消

课程目录中的任务区域展示等待、执行中和最近完成的任务。任务详情展示计划时间、剩余时间、课程数量和逐课程结果。

等待状态的任务可以取消。执行开始后不可取消正在发送的请求。对于“待确认”或“结果未知”，用户可以点击“重新查询结果”；该操作只查询已选课程，不重新发送选课 POST。

## 架构

### 数据模型

新增内部模型：

- `CourseSelectionJob`
  - `id`
  - `termYearCode`
  - `termCode`
  - `scheduledAtMillis`
  - `createdAtMillis`
  - `status`
  - `courses`
  - `results`
- `CourseSelectionJobCourse`
  - 构造选课表单所需的课程 ID、任务 ID、选课池代码、名称、教师和课程代码
- `CourseSelectionCourseResult`
  - `courseId`
  - `status`
  - `message`
  - `submittedAtMillis`
  - `confirmedAtMillis`

任务状态：`WAITING`、`RUNNING`、`COMPLETED`、`PARTIAL`、`FAILED`、`CANCELLED`。

课程结果状态：`CONFIRMED`、`UNCONFIRMED`、`BUSINESS_FAILURE`、`AUTH_REQUIRED`、`UNKNOWN`。

任务模型不保存 Cookie、账号或密码。执行时始终从 `EasPreferenceSource` 读取当前 `EASToken`。

### 任务存储

新增 `CourseSelectionJobStore`，使用应用私有 SharedPreferences 保存 JSON。选择 SharedPreferences 是为了避免 Demo 阶段修改 Room schema 和迁移链。

所有任务更新由单一同步入口完成，写入完整快照。保留等待、运行任务和最近 20 个终态任务，防止历史无限增长。

应用启动时将遗留的 `RUNNING` 任务转为 `FAILED`，消息标记为“应用在执行期间中断，结果未知”，并允许只读重新确认。

### 调度

新增：

- `CourseSelectionAlarmScheduler`
- `CourseSelectionAlarmReceiver`
- `CourseSelectionForegroundService`
- `CourseSelectionBootReceiver`

每个任务使用由任务 ID 派生的稳定 PendingIntent request code。更新同一任务时覆盖原闹钟，取消任务时同时取消 AlarmManager 和本地任务。

手机重启后，BootReceiver 读取仍处于 `WAITING` 的任务：未来任务重新注册；已经过期的任务不补发，改为失败并提示用户手动处理。

前台服务一次只处理一个任务。不同任务通过进程内互斥锁串行，防止两个任务同时扩大并发量；单个任务内部最多并发 10 门不同课程。

### 教务提交网关

在 `EASWebSource` 增加深圳单次选课方法，调用 `/Xsxk/addGouwuche`，使用当前深圳 Web Cookie、`X-Requested-With`、Origin、Referer 和表单字段。

选课 POST 使用专用的单次请求函数：

- 允许提交前执行只读会话预热。
- 只执行一次 POST。
- 发现认证失效时返回 `AUTH_REQUIRED`，不静默重新登录后重发。
- `jg=1` 解释为教务接受提交。
- `jg=-1` 保留教务业务消息。
- HTML、登录页、无法解析的 JSON 和网络超时归类为 `UNKNOWN` 或 `AUTH_REQUIRED`，不得解释为成功。

任务中的课程先按 ID 去重，再用 Kotlin coroutine `async` 执行，并使用 `Semaphore(10)` 限制并发。每个课程协程只调用一次提交方法。

全部 POST 完成后调用一次现有的 `getShenzhenSelectedCourses(term)`。课程存在于已选列表时标记 `CONFIRMED`；教务返回成功但未查到时标记 `UNCONFIRMED`。业务失败保持失败，不被确认查询覆盖。

### Repository 与 ViewModel

`EASRepository` 提供：

- 创建立即任务。
- 创建定时任务。
- 取消等待任务。
- 观察任务列表。
- 只读重新确认任务结果。

`ShenzhenCourseCatalogViewModel` 管理当前课程草稿、任务状态和一次性 UI 事件。网络和任务执行不在 Compose 主线程中运行。

### UI

修改 `ShenzhenCourseCatalogActivity`：

- 可选课程卡片增加选中操作。
- 页面底部增加批量操作栏。
- 增加真实提交二次确认对话框。
- 增加日期时间选择和精确定时权限引导。
- 增加任务列表与任务详情。
- 请求执行期间禁用重复操作。

新增用户可见文字统一写入 `strings.xml`；不在 Kotlin 中硬编码中文。

## 权限与 Manifest

新增或确认以下声明：

- `android.permission.SCHEDULE_EXACT_ALARM`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- `android.permission.POST_NOTIFICATIONS`

前台服务声明使用 `dataSync` 类型。Receiver 与 Service 均不导出，BootReceiver 只接收系统启动广播。

Android 13 及以上通知权限被拒绝时，界面必须在创建定时任务前提示用户处理；不创建无法可靠展示前台状态的任务。

## 错误处理

- 无深圳 Web 会话：阻止提交并进入现有 Web 登录流程。
- Cookie 失效：任务结束为失败，提示重新登录，不自动重发。
- 网络不可用：课程结果为 `UNKNOWN`，允许只读重新确认。
- 部分课程失败：任务状态为 `PARTIAL`，保留逐课程结果。
- 重复任务：同一计划秒、相同课程 ID 集合只保留一个等待任务。
- 时间已过：不补发，标记失败。
- App 或系统中断执行：不恢复 POST，标记结果未知并允许只读确认。

## 测试策略

自动化测试不得访问真实教务系统。

单元测试覆盖：

- 表单字段构造。
- `jg=1`、`jg=-1`、认证 HTML、异常 JSON 和超时分类。
- 课程 ID 去重和 20 门上限。
- 10 路并发下每个课程 ID 只调用一次，无自动重试。
- 提交结果与已选课程确认结果合并。
- 任务 JSON 存取和中断恢复。
- 重复任务指纹。
- Alarm 时间、PendingIntent 标识和取消行为。
- BootReceiver 对未来任务和过期任务的处理。

构建验证：

- `gradlew testDebugUnitTest`
- `gradlew assembleDebug`

真实链路只由用户在明确确认后手动验证一门课程。测试结果必须同时记录教务响应分类和只读确认结果，不进行第二次 POST。

## 完成标准

- 深圳可选课程能加入批量计划。
- 立即任务和精确定时任务都能创建、监控和取消。
- 每门课程在一次任务中最多发送一次选课 POST。
- 单任务并发不超过 10，批量不超过 20。
- 提交后能用已选课程查询确认结果。
- 认证、HTML 和未知响应不误报成功。
- App 重启和手机重启不会重复提交任务。
- 单元测试和 Debug APK 构建通过。
