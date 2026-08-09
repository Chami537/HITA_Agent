package cn.limpu.hita.data.analytics

/**
 * 统一使用统计事件目录。
 *
 * 与 iOS 端共用同一协议（见仓库 docs/unified-protocol.md §2）：
 * 事件名 snake_case、`模块_动作_结果`；维度键值走白名单。
 * 服务端端点：POST {AGENT_BACKEND_BASE_URL}/api/usage
 */
enum class UsageAnalyticsEvent(val eventName: String) {
    // 启动 / 生命周期
    APP_FOREGROUND("app_foreground"),

    // 课表
    TIMETABLE_IMPORT_STARTED("timetable_import_started"),
    TIMETABLE_IMPORT_SUCCEEDED("timetable_import_succeeded"),
    TIMETABLE_IMPORT_FAILED("timetable_import_failed"),
    TIMETABLE_EXPORT_ICS("timetable_export_ics"),

    // 教务
    SCORES_VIEWED("scores_viewed"),
    SCORES_REFRESH_SUCCEEDED("scores_refresh_succeeded"),
    SCORES_REFRESH_FAILED("scores_refresh_failed"),
    EXAMS_VIEWED("exams_viewed"),
    EXAMS_REFRESH_SUCCEEDED("exams_refresh_succeeded"),
    EXAMS_REFRESH_FAILED("exams_refresh_failed"),
    CREDIT_SUMMARY_VIEWED("credit_summary_viewed"),
    CREDIT_SUMMARY_LOAD_SUCCEEDED("credit_summary_load_succeeded"),
    CREDIT_SUMMARY_LOAD_FAILED("credit_summary_load_failed"),
    EMPTY_ROOM_SEARCH_STARTED("empty_room_search_started"),
    EMPTY_ROOM_SEARCH_SUCCEEDED("empty_room_search_succeeded"),
    EMPTY_ROOM_SEARCH_FAILED("empty_room_search_failed"),
    SYSTEM_CALENDAR_WRITE_STARTED("system_calendar_write_started"),
    SYSTEM_CALENDAR_WRITE_SUCCEEDED("system_calendar_write_succeeded"),
    SYSTEM_CALENDAR_WRITE_FAILED("system_calendar_write_failed"),

    // 课程资源 / 教师
    SEARCH_PAGE_VIEWED("search_page_viewed"),
    RESOURCE_SEARCH_STARTED("resource_search_started"),
    RESOURCE_SEARCH_SUCCEEDED("resource_search_succeeded"),
    RESOURCE_SEARCH_NO_RESULTS("resource_search_no_results"),
    RESOURCE_SEARCH_FAILED("resource_search_failed"),
    RESOURCE_RESULT_OPENED("resource_result_opened"),
    TEACHER_SEARCH_STARTED("teacher_search_started"),
    TEACHER_RESULT_OPENED("teacher_result_opened"),

    // AI 助手
    AI_CHAT_STARTED("ai_chat_started"),
    AI_TOOL_INVOKED("ai_tool_invoked"),
    AI_CHAT_FAILED("ai_chat_failed"),

    // 外观
    CUSTOM_WALLPAPER_APPLIED("custom_wallpaper_applied"),
    CUSTOM_WALLPAPER_ACTIVE("custom_wallpaper_active"),
    CUSTOM_AVATAR_APPLIED("custom_avatar_applied"),
    CUSTOM_AVATAR_ACTIVE("custom_avatar_active"),

    // 公告
    NOTICE_SHOWN("notice_shown"),
    NOTICE_DISMISSED("notice_dismissed"),
    NOTICE_ACTION_TAPPED("notice_action_tapped"),

    // 成绩互助（协议预留，功能上线后接入）
    GRADE_SUBMISSION_OPENED("grade_submission_opened"),
    GRADE_SUBMISSION_SUBMITTED("grade_submission_submitted"),
    GRADE_SUBMISSION_DELETED("grade_submission_deleted"),
}

/** 事件维度白名单键（与协议一致）。 */
object UsageAnalyticsDimensions {
    const val SOURCE = "source"
    const val ERROR_CATEGORY = "error_category"
    const val TOOL = "tool"
    const val PRESENTATION = "presentation"
    const val KIND = "kind"
    const val TYPE = "type"

    // 错误分类口径与 iOS UsageAnalyticsErrorClassifier 对齐
    const val ERROR_NETWORK = "network"
    const val ERROR_AUTHENTICATION = "authentication"
    const val ERROR_PERMISSION = "permission"
    const val ERROR_SERVER = "server"
    const val ERROR_UNKNOWN = "unknown"
}
