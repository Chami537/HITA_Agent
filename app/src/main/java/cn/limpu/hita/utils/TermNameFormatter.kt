package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.TermItem

object TermNameFormatter {
    private val yearPrefix = Regex("^\\s*\\d{4}-\\d{4}(?:\\s*学年)?\\s+")

    fun shortTermName(termName: String?, fallback: String?): String {
        val primary = termName?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary.replace(yearPrefix, "")
        return fallback?.trim()?.replace(yearPrefix, "") ?: ""
    }

    fun fullTermName(term: TermItem): String {
        val year = term.yearCode.trim().ifBlank { term.yearName.trim() }
        val source = listOf(term.termName, term.name).joinToString(" ")
        val season = when {
            source.contains("春") -> "春季"
            source.contains("夏") -> "夏季"
            source.contains("秋") -> "秋季"
            source.contains("冬") -> "冬季"
            source.contains("寒") -> "寒假"
            term.termCode == "1" -> "秋季"
            term.termCode == "2" -> "春季"
            term.termCode == "3" -> "夏季"
            term.termCode == "4" -> "冬季"
            else -> shortTermName(term.termName, term.name)
        }
        return listOf(year, season).filter { it.isNotBlank() }.joinToString(" ")
    }
}
