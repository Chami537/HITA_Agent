# HITA 课表

[App 下载（Releases）](https://github.com/Chami537/HITA_Agent/releases/latest)

## 应用简介

面向哈尔滨工业大学三校区（深圳 / 本部 / 威海）学生的工具类 APP（非官方）。

### 当前功能

- **课表与日程**：教务导入、按周查看、ICS 导入、手动添加课程/考试/DDL
- **成绩查询**：学期成绩、GPA / CGPA / 学分绩（百分制 & 4.0 制）、排名
- **学分统计**：必修/限选/任选/MOOC 分类汇总、学分目标追踪
- **考试查询**：教务接口查询考试安排、一键导入到课表
- **空教室查询**：按教学楼和教学周查空闲教室
- **课程资源**：应用内搜索课程资料与 README、支持追加型投稿
- **教师搜索**：课程资源数据 + 教师主页检索入口
- **AI 助手**：基于 ReAct 框架的智能问答，支持课表查询、课程搜索、教师搜索、Web 搜索、RAG 知识库等

### AI 助手工具

| 工具 | 功能 |
|------|------|
| `get_timetable` | 查询今日/任意日期课表 |
| `search_course` | 搜索课程 |
| `get_course_detail` | 获取课程 README、评价、教师信息 |
| `search_teacher` | 搜索教师信息 |
| `add_activity` | 添加日程事件 |
| `web_search` / `brave_answer` | Brave 搜索 / AI 问答 |
| `rag_search` | 校内知识库检索 |
| `crawl_page` / `crawl_site` | 网页爬取 |
| `submit_review` | 提交课程评价 / PR |

## 技术栈

- **客户端**：Android Kotlin + Retrofit + Room + Hilt + Jsoup
- **LLM**：MiniMax API
- **后端**：pr-server（课程资源）+ agent-backend（AI 工具编排）

## 构建

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
```

环境：JDK 21、Android SDK 35、Gradle 8.7

## 数据与版权

- 课程与课表数据来自教务系统，仅存储在设备本地
- 课程资料来源 HOA（校内民间开源组织），官网：hoa.moe
- 如有问题联系：2720649216@qq.com

## 开源库

- [LoadingButtonAndroid](https://github.com/leandroBorgesFerreira/LoadingButtonAndroid)
- [multiline-collapsingtoolbar](https://github.com/opacapp/multiline-collapsingtoolbar)
- [Luban](https://github.com/Curzibn/Luban)

## License

[MIT](LICENSE) © Stupid Tree
