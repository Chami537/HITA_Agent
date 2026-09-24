package cn.limpu.hita.ui.main

import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.EASToken

enum class MainTab(
    val titleRes: Int,
    val iconRes: Int,
) {
    TIMELINE(R.string.title_timeline, R.drawable.ic_nav_today),
    TIMETABLE(R.string.title_timetable, R.drawable.ic_nav_timetable),
    AGENT(R.string.title_agent, R.drawable.ic_baseline_toys_24),
    BLOG(R.string.title_blog, R.drawable.ic_nav_blog),
    NAVIGATION(R.string.title_navigation, R.drawable.ic_nav_navigation);

    companion object {
        fun visibleFor(token: EASToken?): List<MainTab> {
            val showBlog = token?.campus == EASToken.Campus.SHENZHEN
            return if (showBlog) entries.toList() else entries.filter { it != BLOG }
        }

        fun fromName(raw: String?): MainTab? =
            raw?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
