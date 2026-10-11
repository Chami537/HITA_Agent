package cn.limpu.hita.data.model.timetable

/**
 * 主课表显示目标：一份独立课表，或一个组合课表（整体）。
 * 仅表达「身份」，名称与成员解析在 [cn.limpu.hita.data.repository.DisplayScopeResolver]。
 */
sealed interface DisplayScope {
    val id: String

    data class Single(override val id: String) : DisplayScope
    data class Group(override val id: String) : DisplayScope

    companion object {
        const val TYPE_SINGLE = "single"
        const val TYPE_GROUP = "group"

        fun of(type: String?, id: String?): DisplayScope? {
            if (id.isNullOrBlank()) return null
            return when (type) {
                TYPE_SINGLE -> Single(id)
                TYPE_GROUP -> Group(id)
                else -> null
            }
        }

        fun typeOf(scope: DisplayScope): String = when (scope) {
            is Single -> TYPE_SINGLE
            is Group -> TYPE_GROUP
        }
    }
}
