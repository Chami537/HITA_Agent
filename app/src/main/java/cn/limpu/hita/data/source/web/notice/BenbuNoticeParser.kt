package cn.limpu.hita.data.source.web.notice

import cn.limpu.hita.data.model.notice.CampusNotice
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 本部「今日哈工大」公告公示（category/10，Drupal）。
 * 列表只给相对时间（"2小时前"），绝对日期取自 /article/yyyy/MM/dd/{id} 路径。
 */
object BenbuNoticeParser {
    const val LIST_URL = "https://today.hit.edu.cn/category/10"
    const val MAX_NOTICES = 30
    const val PAGES_FOR_CAP = 2
    const val PARSER_VERSION = 1
    const val CAMPUS = "BENBU"

    private val articleRegex = Regex("""/article/(20\d{2})/(\d{2})/(\d{2})/(\d+)""")
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }

    fun listPageUrl(page: Int): String {
        val safe = page.coerceAtLeast(1)
        return if (safe == 1) LIST_URL else "$LIST_URL?page=${safe - 1}"
    }

    fun parse(html: String, baseUrl: String = LIST_URL): List<CampusNotice> {
        val doc = Jsoup.parse(html, baseUrl)
        val root = doc.selectFirst("ul.paragraph.list-tooltip") ?: return emptyList()
        val byId = LinkedHashMap<String, CampusNotice>()
        for (anchor in root.select("span.title a[href]")) {
            val raw = anchor.attr("href").substringBefore('#').trim()
            val match = articleRegex.find(raw) ?: continue
            val (year, month, day, articleId) = match.destructured
            val title = anchor.attr("title").ifBlank { anchor.text() }
                .replace('\u00a0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            if (title.length < 4) continue
            val id = "hit:$articleId"
            if (id in byId) continue
            byId[id] = CampusNotice(
                campus = CAMPUS,
                id = id,
                title = title,
                url = "https://today.hit.edu.cn/article/$year/$month/$day/$articleId",
                pubDateMillis = parseDateMillis(year, month, day),
            )
        }
        return byId.values
            .sortedWith(compareByDescending<CampusNotice> { it.pubDateMillis }.thenBy { it.title })
            .take(MAX_NOTICES)
    }

    fun isPersistedNotice(notice: CampusNotice): Boolean =
        notice.campus == CAMPUS && notice.id.startsWith("hit:") &&
            articleRegex.containsMatchIn(notice.url)

    private fun parseDateMillis(year: String, month: String, day: String): Long =
        runCatching { dateFormat.get()!!.parse("$year-$month-$day")?.time }.getOrNull() ?: 0L
}
