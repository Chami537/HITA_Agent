# HITA 课表显示目标与课表管理重构设计

日期：2026-10-10。分支：`feat/timetable-sharing`。代码基线：`ae0c5c3`。
状态：聊天设计已逐节呈现并经用户确认细节（显示语义、组合构成、删除语义、入口形态、字体范围、登录导入自动化）；用户授权 spec 自审后直接进入实施计划，无需等待书面审阅。

## 1. 目标与范围

七项需求，全部落在课表域：

1. 按节次对比界面 UI/UX 重构，适配深/浅两种模式与全部 7 种视觉风格。
2. 修复经典主题下同屏不同课程撞色问题，扩充配色。
3. 课表显示设置新增字体大小三档：默认 / 小字 / 大字。
4. 「更多」界面课表区域重构：删除「课表工具」组与「最近课表」卡片，只保留「课表管理」入口；课表管理内提供教务导入、ICS 导入、课表口令导入、新建空白课表；ICS 与口令导入可选择导入为好友课表或我的课表。
5. 导入为我的课表时可选择与现有课表同屏显示（组成组合课表）或分开显示；组合课表在管理页一级界面作为整体卡片，点进二级界面单独管理子课表。
6. 所有我的课表（含教务/ICS/口令导入与手动新建）可重命名。
7. 主课表顶栏显示当前课表的命名，点击可切换显示目标；组合课表只能整体选择。

非目标：好友课表参与组合、多人对比、分享协议升级、联网同步、英文资源、小组件课表源切换、课程提醒按显示目标过滤。

## 2. 已核实的代码基础

- 数据层已是多课表结构：`Timetable(id, name, code, startTime, endTime, scheduleStructure)`（`data/model/timetable/Timetable.kt`）；`TermSubject.timetableId`、`EventItem.timetableId` 均按课表分组。`AppDatabase` 当前版本 15。
- 主课表现状为全部课表事件叠加：`TimetableViewModel` 经 `EventItemDao.getEventsDuring(fromT, toT)`（`EventItemDao.kt:40`）取周窗口内所有事件；顶栏课表芯片（`MainActivity.kt` `TimetableToolbarTitle`）点击仅 `onSemesterSelected` 跳周（`:845`），不过滤；`timetableOptions` 只含 `code` 非空的教务课表（`TimetableFragment.kt:754`）。
- 顶栏标题：`TimetableFragment.buildTitleState`（`:730`）以当前周落在学期内且 `startTime` 最小的课表计算「第 N 周」并取其 `name`；经 `MainPageController.setTitleText/setTimetableName/setTimetableOptions` 传给 `MainActivity`。
- 课表管理：`ui/timetable/manager/TimetableManagerActivity.kt` 全 Compose，2 列网格 + 末尾 `AddTimetableCard`（新建/教务导入/ICS 三按钮，`:479-510`）；好友课表入口是顶栏下 `TextButton`（`:314`）；课表卡点击进入详情、长按进入多选删除；无重命名。`TimetableManagerViewModel.timetablesLiveData = timetableRepository.getTimetables()`。
- 更多界面：`ui/main/navigation/NavigationFragment.kt` 全 Compose；「最近课表」卡 `:520-528`，「课表管理」卡 `:529-541`，「课表工具」组（导入课表/导入 ICS）`:543-546`。
- 课表详情：`ui/timetable/detail/TimetableDetailActivity.kt` 已有编辑、作息编辑、ICS 导出。
- 课表设置面板：`ui/main/timetable/panel/FragmentTimetablePanel.kt`（Compose 底部面板）+ `TimetablePanelViewModel`；存储经 `TimetableStyleRepository`（SharedPreferences 封装，`booleanLiveData`/`intLiveData` 模式，键如 `KEY_ZOOM_COMPRESSED`）。
- 课程卡字号在 `TimetableFragment.kt:1829-1872` 动态计算：基准 title 13f/subtitle 10f × textScale × lengthScale；冲突合并卡 12/9sp（`:1694-1708`）；时间轴标签 10/12sp（`:1242/1262`）。`TimetableCardTextScale.forColumnCount` 当前恒传 1。
- 课程色：存储层 `ColorTools.colorForName` 9 色池按课名 hash 取模写入 `subject.color/event.color`；渲染层 `hitaCourseColor`（`ui/design/HitaCoursePalette.kt:30-47`）经典风格把 storedColor 按色相最近邻压到 6 色板（`:105-110`），或 `storedColor=0`/低饱和时 `hashCode()%6`。所有色板 6 色（P5=2、Sumi=1 为设计意图）。仅 Classic 风格允许用户在 `HitaCoursePaletteDialog` 手选课程色。
- 主题系统：`MODE(深/浅/跟随) × STYLE(7 种) × COLOR_PRESET(4 种，仅 Classic)`；Compose 侧 `HitaComposeTheme` 注册 SP 监听并下发 `MaterialTheme.colorScheme = hitaColorScheme(style, preset, dark)`。
- 对比界面：`ui/main/timetable/compare/`。`ComparisonOverlay` 硬编码 `0xFFD33131`/`0xFF23774D` 与白色徽标底；`ComparisonStatusBar` 4 行文字 + 3 个 TextButton；`ComparisonDetails` 为纯文字流底部弹窗；`ComparisonConfigSheet` 用 OutlinedButton+DropdownMenu。
- 分享/好友：`feature/timetableshare/protocol/TimetableShareCodec.decodeMessage(message): SharedTimetable`（纯 JVM）；`FriendTimetableRepository.preview/confirm`；好友表 `friend_timetable` 存整表 JSON 快照，`remark` 可改名。
- ICS：`TimetableRepository.importFromICSAsNewTimetable`（`:914`，ical4j）与 `exportToICS`（`:843`）；`IcsImportBundleBuilder`。
- 教务导入：`EASTimetableImportRepository`（课表名 `buildTimetableName` = 校区名+短学期名，`:677-684`），入口 `ui/eas/imp/ImportTimetableActivity`；登录后自动导入当前课表为既有行为。

## 3. 概念模型与术语

面向用户的术语：动作叫「**同屏显示**」，组叫「**组合课表**」（默认命名「组合课表 N」）。不用「重叠」「嵌套」。

```
显示目标 DisplayScope ─┬─ Single(timetableId)   独立课表
                       └─ Group(groupId)       组合课表 ──包含──> 1..N 份子课表
```

约束：一份子课表最多属于一个组合（`groupId` 单值）；好友课表不参与组合；组合为空（最后一份子课表被移出或删除）时自动删除。

## 4. 数据模型（DB 15 → 16）

- 新表 `timetable_group`：`id TEXT PK`（UUID）、`name TEXT NOT NULL`、`createdAt INTEGER NOT NULL`。
- `timetable` 表新增可空列 `groupId TEXT`（null = 独立课表），加 `groupId` 索引。
- 迁移 `MIGRATION_15_16` 走显式迁移链，导出 v16 schema；不修改既有列，不回退 destructive。
- 新 DAO：`TimetableGroupDao`（增删改查、观察全部组及成员）。`EventItemDao` 新增 `getEventsDuring(fromT, toT, timetableIds: List<String>)`（`WHERE timetableId IN (:ids)` 且时间窗相交，沿用既有 `from < :toT AND to > :fromT` 语义）。
- `TimetableRepository` 新增：`renameTimetable(id, name)`（trim、非空、≤40 码点）、`createGroup(name)`、`addToGroup(timetableId, groupId)`、`removeFromGroup(timetableId)`、`dissolveGroup(groupId)`（成员全部移出后删组）；删除课表时若其所属组因此为空则顺带删组。

## 5. 主课表显示目标（需求 7）

- 主课表任何时刻只显示一个显示目标；**不再提供「全部课表叠加」**。
- 选择持久化（用户明确要求的记忆化）：SharedPreferences 存 `display_scope_type`（single/group）+ `display_scope_id`，重启/杀进程后保持上次选择，不需要每次重选。
- **过滤范围（用户拍板：全部跟随）**：显示目标不仅管主课表——今日时间线、桌面小组件、课程提醒、Agent 课表查询全部跟随显示目标过滤。实现上由统一的显示目标解析器解析当前目标对应的 timetableId 集合（含失效回落），所有消费方共用。
- 默认目标解析顺序：持久化值有效 → 用之；否则当前时间落在学期内的课表中 `startTime` 最小者（沿用 minTT 语义）；无任何课表 → 空态。
- 显示目标被删除/解散后回落到上述默认；持久化值指向不存在的组/课表时同样回落并清除。
- 事件加载：`Single` → 按该 timetableId 过滤；`Group` → 按组内全部 timetableId 集合过滤。LiveData 切换随显示目标变化重建查询。
- 顶栏芯片文字 = 当前显示目标名（课表名或组合名，即用户重命名后的名字）；下拉列出全部独立课表与组合课表（组合带「N 份课表」徽标，只能整体选择，不展开子课表）。
- 「第 N 周」计算：`Single` 按该课表学期；`Group` 按当前周落在哪个子课表学期（多份命中取 `startTime` 最小）；均未命中显示「假期」。
- 网格作息结构：与周数同源，取当前周命中子课表的 `scheduleStructure`；对比模式仍按其自身规则覆盖。
- 顶栏既有跳周语义（`onSemesterSelected`）被「切换显示目标」取代：选择目标后跳到该目标当前周（目标学期内则当前周，否则第一周周一），并过滤显示。
- 对比、分享、好友功能仍面向全部我的课表，不受显示目标过滤影响（对比的 personalChoices 保持全量）。

## 6. 课表管理与导入流程（需求 4/5/6）

### 6.1 更多界面

删除「课表工具」组（导入课表、导入 ICS 两行）与「最近课表」卡片；课表区域只保留「课表管理」卡片。ICS 导入结果处理逻辑从 `NavigationFragment` 迁出。

### 6.2 课表管理一级界面

- 网格内容：独立课表卡 + 组合课表卡（组名 + 「N 份课表」徽标 + 季节配图，与独立卡视觉可区分）。
- 现有「添加课表」卡改造为单一入口按钮，点击弹底部菜单：**教务导入 / 导入 ICS / 课表口令导入 / 新建空白课表**。不新增 FAB/加号按钮。
- 好友课表入口保留在一级界面（由顶栏文字按钮改为更和谐的入口样式）。
- 课表卡操作：点击进入详情；长按菜单新增「重命名」，保留多选删除。
- 重命名（需求 6）：管理页卡片菜单与课表详情页均提供；适用于全部我的课表（教务导入的自动命名如「深圳 2026秋季」可改）；trim、非空、≤40 码点。

### 6.3 二级界面（组合课表）

- 从一级界面点击组合卡进入：列出组内子课表。
- 每个子课表支持：重命名、移出组合（变回独立课表）、删除（删课表本身，连带课程，与一级删除语义一致，需确认弹窗）。
- 组合级操作：解散组合（全部子课表移出，组删除）、重命名组合。
- 二级界面不提供任何导入/新建入口。

### 6.4 导入流程

```text
教务导入(含登录自动导入)
  └─ 完成后自动同屏:当前显示目标为组合 → 加入该组合;
     为独立课表 → 新建组合课表(默认命名)含两者并切换显示目标过去;
     无既有课表(首次) → 保持独立课表。
     Toast 提示「已加入组合课表 X」;撤销路径为管理页二级界面的「移出组合」。

导入 ICS → 解析成功后询问「导入为」
  ├─ 好友课表 → 转为 HITA1 快照存 friend_timetable(昵称默认取文件名,备注可后改)
  │             term 范围从课程实际起止推导;作息取当前显示课表作息,无则默认结构
  └─ 我的课表 → 询问「同屏显示?」:选择现有课表/组合(同屏) 或 分开显示(独立课表)
                **不设默认选中项**,用户必须主动选择;
                选独立课表目标 → 新建组合含两者;选组合目标 → 加入该组合

课表口令导入 → 粘贴解析预览后询问「导入为」
  ├─ 好友课表 → 现有 FriendTimetableRepository 流程(更新/新建语义不变)
  └─ 我的课表(仅 FULL 模式) → 快照转 Timetable+TermSubject+EventItem 真实行,
                作息取快照 periods,学期取快照 term;随后同上询问同屏目标
                BUSY 模式选择「我的课表」→ 禁止并提示「忙闲课表没有课程详情」
```

- 口令导入为我的课表时新课程色走既有 `ColorTools.colorForName`；`source` 标记为手动/导入类，不标 EAS_IMPORT。
- 导入入口收敛后，`NavigationFragment` 不再持有 ICS launcher；ICS 选择与结果处理集中到课表管理（或其 ViewModel）。

## 7. 颜色系统（需求 2）

选定方案 A：**大色板 + 稳定轮转分配**。存储层不动（无数据迁移）。用户要求色板总数大于 12 以覆盖课程种类多的学期。

- 经典色板 6 → **16 色**：色相环均匀分布（22.5° 步进），深/浅各一套手工调色，与既有 6 色风格延续，保证卡片底色上文字的对比度规则不变（仍走 `ColorContrast.contrastText`）。
- 新增纯 Kotlin 分配器 `TimetableColorAssigner`（不依赖 Android，JVM 可测）：
  - 输入：当前显示目标的**全部课程**（courseKey = subjectId 或课名兜底）及其 storedColor、目标色板。
  - 输出：`Map<courseKey, 槽位 index>`。
  - 规则：按 courseKey 稳定排序；每门课先取 storedColor 色相最近邻槽位作为倾向；槽位已被先序课程占用时，沿色相环找最近空槽；课程数 > 色板大小时按序复用（最少碰撞）。
  - 以「显示目标全部课程」而非「本周窗口课程」为输入，保证一门课的颜色在学期内稳定，不随周导航漂移。
- 接入点：`TimetableFragment` 在周视图渲染前按当前显示目标课程集合计算映射，传入课程卡；`hitaCourseColor` 的单课现算路径（详情页等）保留并映射到大色板最近邻。用户手选课程色（Classic 下 `HitaCoursePaletteDialog`）优先占用其选定槽位。
- 其他风格：玻璃/赛博/日映/深空等 6 色板同样走分配器降低碰撞；P5（2 色）与 Sumi（单色）保持「全部同色」设计意图，跳过分配器。

## 8. 字体三档（需求 3）

- `TimetableStyleRepository` 新增 `KEY_FONT_SIZE_LEVEL`：0=默认(1.0)、1=小字(0.85)、2=大字(1.12)；默认 0。
- 「课表显示设置」面板新增三档选择（SegmentedButton），与缩放模式并列。
- 缩放系数乘入：课程卡标题/副标题基准（13f/10f）、冲突合并卡（12/9sp）、左侧节次与时间轴标签（10/12sp）；表头月份、星期、「第 N 周」大标题不缩放。
- 缩放参与既有 coerceIn 上下限之前（即先乘系数再走高度/宽度封顶），压缩模式的宽度反推逻辑不变。

## 9. 对比界面重构（需求 1）

根因：硬编码色值与白色徽标绕过主题系统；状态条信息堆叠；详情弹窗纯文字流水。

- **颜色语义化**：冲突 → `colorScheme.error` 系；共同空闲 → `colorScheme.tertiary` 系；徽标底 → `surface`/`onSurface` 系。全部经 `HitaComposeTheme` 的 colorScheme，深浅 × 7 风格自动适配；水墨等弱色风格下靠「填充 vs 边框」形态双编码，图例色块+文字并存（色弱友好）。
- **覆盖层**：空闲 = tertiary 12% 填充 + 左上角小标签；冲突 = error 1.5dp 边框 + 8% 填充 + 右下角标签；标签 9 → 10sp；无障碍 contentDescription 保留。
- **状态条**：压缩为一行——「对比中：我的课表 × 好友名」+ 共同空闲/同节有课两个图例 chip + 调整/详情/退出三个紧凑操作；结构外课程提示并入图例行尾部或省略到详情页。
- **详情弹窗**：按日分节卡片化；每日头部 = 日期 + 统计 chip（空闲 N 节·X 分钟 / 冲突 N 节·Y 分钟）；节次行 = 节次号 + 起止时间 + 状态 chip，点击展开双方课程明细卡（原有 FULL/BUSY 隐私规则不变：BUSY 只显示忙碌）。
- **配置面板**：课表/好友选择改为 ExposedDropdownMenu 式 outlined 文本框；共同空闲/冲突开关改 Switch 行；时间范围用 RangeSlider（分钟步进 5）；结构不兼容提示保留原语义，排版改为说明卡。
- 行为语义全部不变：判定规则、隐私边界、只读限制、周导航与时区处理沿用对比 spec。

## 10. 自动化验证与产品验收

纯 JVM 测试：

- `TimetableColorAssigner`：≤ 池大小同屏必不撞色；storedColor 倾向尊重；碰撞时最近空槽；超池复用的确定性；排序稳定性（颜色不随输入顺序漂移）。
- 显示目标解析：默认/持久化/回落规则；组删除后的回落；周数与作息的同源选择。
- 组合操作：建组/入组/移出/解散/空组自动删除；删除子课表连带课程与组生命周期。
- 口令/ICS 转我的课表：快照→三表行的字段映射；BUSY 拒绝；term 推导。
- 字体系数：三档映射函数。
- 既有对比引擎、分享协议测试不回撤。

设备/集成：DB 15→16 迁移保留既有数据（schema 导出 + 迁移测试）；`assembleDebug` + `testDebugUnitTest` 通过后交付真机验收：两模式 × 多主题下对比界面观感、经典主题多课程不撞色、三档字体、导入三分支（教务自动同屏/ICS 两选/口令两选）、组合课表管理与主课表切换。

## 11. 实施顺序与授权边界

顺序：DB 迁移与组合模型 → 显示目标与主课表过滤 → 课表管理一级/二级界面与重命名 → 导入流程三分支 → 颜色分配器与色板 → 字体档位 → 对比界面重构 → 更多界面清理 → 验证与文档。

不做 TDD（项目约定），以编译、JVM 单测与真机验收保证质量。commit/push 按 AGENTS.md 等用户明确授权；构建验证通过后停下汇报，等指令再产出本地测试 APK。
