# 课表显示目标与课表管理重构 实施计划

**Goal:** 按 spec 完成 7 项课表域需求：对比界面重构、经典主题配色去重、字体三档、课表管理/导入流程重构（组合课表）、我的课表重命名、主课表按显示目标过滤切换。
**Architecture:** DB 15→16 加 `timetable_group` 表与 `timetable.groupId` 列；主课表引入持久化「显示目标」（独立课表/组合课表）并按其过滤事件；颜色经纯 Kotlin 分配器按显示目标全课程稳定分配 12 色板；UI 全部 Compose + HitaTheme 语义色。
**Spec:** [docs/superpowers/specs/2026-10-10-timetable-display-management-design.md](../specs/2026-10-10-timetable-display-management-design.md)

不采用 TDD（项目约定）；每个任务编译 + 针对性 JVM 单测验证。

### Task 1: 数据层——组合课表模型与迁移
**Files:** Create `data/model/timetable/TimetableGroupEntity.kt`、`data/source/dao/TimetableGroupDao.kt`; Modify `data/model/timetable/Timetable.kt`(加 `groupId`)、`data/AppDatabase.kt`(v16 + MIGRATION_15_16 + dao 注册)、`data/source/dao/EventItemDao.kt`(加按 id 集合过滤的周窗口查询)、`data/repository/TimetableRepository.kt`(renameTimetable/createGroup/addToGroup/removeFromGroup/dissolveGroup/删课表联动空组清理)、`app/di/DatabaseModule.kt`
- [x] entity/dao/migration 实现;导出 v16 schema
- [x] JVM 单测:组生命周期(建/入/出/解散/空组自删)、重命名校验(trim/非空/≤40 码点)
- [x] `./gradlew :app:compileDebugKotlin` 通过

### Task 2: 显示目标 DisplayScope 与全通道过滤
**Files:** Create `data/repository/DisplayScopeResolver.kt`(sealed DisplayScope + SP 持久化 + 默认解析/回落 + 输出 timetableId 集合,单例共用); Modify `ui/main/timetable/TimetableViewModel.kt`、`TimetableFragment.kt`(buildTitleState 按目标算周数/取作息)
- [x] SP 存 type+id;默认=当前学期 startTime 最小课表;失效回落并清除
- [x] Group 目标事件=组内 timetableId 集合过滤;Single=单 id
- [x] JVM 单测:默认解析/回落/周数命中规则
- [x] 编译通过

### Task 2b: 时间线/小组件/提醒/Agent 跟随显示目标
**Files:** Modify 今日时间线 ViewModel、ui/widgets/ 小组件数据查询、data/work/CourseReminder、agent/timetable/ 课表工具(具体路径以侦察为准)
- [x] 四处全部经 DisplayScopeResolver 拿 id 集合后走过滤查询
- [x] DAO 补齐各消费方需要的过滤版查询(today/upcoming/after 等)
- [x] 编译通过

### Task 3: 主课表顶栏切换显示目标
**Files:** Modify `MainActivity.kt`(TimetableToolbarTitle 下拉数据从 List<Timetable> 改为显示目标列表,组合项带「N 份课表」徽标;`onSemesterSelected` 改为切换目标并跳周)、`TimetableFragment.kt`(MainPageController 接口与 applyTitleState)
- [x] 芯片显示当前目标名;下拉=全部独立课表+组合(整体选,不展开)
- [x] 选择后过滤显示并跳到该目标当前周/第一周
- [x] 编译通过;手动冒烟(真机阶段统一验)

### Task 4: 颜色系统——12 色板 + 分配器
**Files:** Create `ui/design/TimetableColorAssigner.kt`(纯 Kotlin); Modify `ui/design/HitaCoursePalette.kt`(经典色板 6→12 深浅两套)、`TimetableFragment.kt`(周视图渲染前按显示目标全课程算映射传入卡片)
- [x] 12 色按色相环均匀分布手工调色,深浅两套,文字对比仍走 ColorContrast.contrastText
- [x] 分配器:稳定排序→storedColor 色相最近邻倾向→占用则最近空槽→超池确定性复用;手选色优先占槽
- [x] 6 色风格(玻璃/赛博/日映/深空)同走分配器;P5/Sumi 跳过
- [x] JVM 单测:≤12 门同屏不撞色、倾向尊重、超池确定性、输入顺序无关性
- [x] 编译通过

### Task 5: 字体三档
**Files:** Modify `data/repository/TimetableStyleRepository.kt`(`KEY_FONT_SIZE_LEVEL` int 0/1/2 + liveData)、`ui/main/timetable/panel/FragmentTimetablePanel.kt` + `TimetablePanelViewModel.kt`(三档 SegmentedButton)、`TimetableFragment.kt`(系数 1.0/0.85/1.12 乘入课程卡/冲突卡/时间轴标签,先于 coerceIn)
- [x] 表头月份/星期/第N周不缩放
- [x] 编译通过 + 三档切换即时生效

### Task 6: 课表管理一级界面重构
**Files:** Modify `ui/timetable/manager/TimetableManagerActivity.kt` + ViewModel(组合卡+徽标、添加按钮改底部菜单四选项、卡片长按菜单加重命名、好友入口样式)
- [x] 组合卡=组名+「N 份课表」徽标,点击进二级;独立卡行为不变
- [x] 重命名对话框(trim/非空/≤40);教务导入课表可改
- [x] 编译通过

### Task 7: 组合课表二级界面
**Files:** Create `ui/timetable/manager/group/TimetableGroupActivity.kt`(+Screen/ViewModel)
- [x] 子课表列表:重命名/移出组合/删除(确认弹窗,删课表连带课程);组合级:重命名/解散
- [x] 无导入/新建入口
- [x] 编译通过

### Task 8: 导入流程三分支
**Files:** Modify `TimetableManagerActivity`/ViewModel(菜单分流)、`EASTimetableImportRepository.kt` 或导入完成回调(自动同屏+Snackbar 撤销); Create `data/repository/SharedTimetableImporter.kt`(口令快照→三表行;ICS→好友快照); Modify `ui/timetable/friend/FriendImportDialog.kt` 或新增导入选项 Sheet(导入为好友/我的、同屏目标选择)
- [x] 教务(含登录自动):完成后自动与当前显示目标同屏,Snackbar 提示可撤销(=移出)
- [x] ICS:好友课表(快照,昵称=文件名,term 从课程时间推导,作息取当前课表或默认) / 我的课表→同屏目标选择或独立
- [x] 口令:FULL 可选两路;BUSY 选我的课表→禁止+提示
- [x] JVM 单测:快照→三表字段映射、BUSY 拒绝、ICS term 推导
- [x] 编译通过

### Task 9: 更多界面清理
**Files:** Modify `ui/main/navigation/NavigationFragment.kt`(删课表工具组+最近课表卡+ICS launcher 与结果处理)、ViewModel(recentTimetableLiveData 清理)
- [x] 课表区域只剩「课表管理」卡
- [x] 编译通过;无残留字符串引用(删 strings 需全仓检查)

### Task 10: 对比界面 UI 重构
**Files:** Modify `compare/ComparisonOverlay.kt`(语义色+10sp 标签)、`compare/ComparisonDetails.kt`(状态条一行化+详情卡片化)、`compare/ComparisonConfigSheet.kt`(ExposedDropdown/Switch/RangeSlider)、`ui/design/HitaComposeTheme.kt` 或 palette(如需补语义 token)
- [x] error/tertiary 系 + surface 徽标;深浅×7 风格自适配;图例色块+文字双编码
- [x] 行为语义零变化(判定/隐私/只读/时区)
- [x] 编译通过

### Task 11: 全量验证与交付准备
- [x] `./gradlew testDebugUnitTest` 全绿
- [x] `./gradlew assembleDebug` 通过
- [x] v16 schema 已导出且迁移链完整
- [ ] 停下来向用户汇报,等指令再产 `app-debug.apk`;真机确认后才谈合并/推送
