package cn.limpu.hita.utils

import java.util.Locale

object CourseNameUtils {
    private val bracketSegments = Regex("（[^）]*）|\\([^)]*\\)|【[^】]*】|\\[[^\\]]*]")
    private val bracketChars = Regex("[（）()【】\\[\\]]")
    private val trailingSlashTeachingClass = Regex(
        "\\s*\\d+\\s*[/／]\\s*[A-Za-z0-9]+\\s*班\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val trailingTeachingClass = Regex(
        "\\s+[A-Za-z0-9一二三四五六七八九十]+\\s*班\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val trailingLetter = Regex("[\\s·_.-]*[A-Za-z]+\\d*\\s*$")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val original = raw.trim()
        var name = original
        name = name.replace(bracketSegments, " ")
        name = name.replace(bracketChars, " ")
        name = name.replace(trailingSlashTeachingClass, "")
        name = name.replace(trailingTeachingClass, "")
        name = name.replace(trailingLetter, "")
        name = name.replace("\\s+".toRegex(), " ").trim()
        return if (name.isBlank()) original else name
    }

    fun normalizeKey(raw: String?): String {
        val normalized = normalize(raw) ?: return ""
        return normalized
            .replace("\\s+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }
}
