package com.limpu.hitax.utils

object TermNameFormatter {
    private val yearPrefix = Regex("^\\s*\\d{4}-\\d{4}(?:\\s*学年)?\\s+")

    fun shortTermName(termName: String?, fallback: String?): String {
        val primary = termName?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary.replace(yearPrefix, "")
        return fallback?.trim()?.replace(yearPrefix, "") ?: ""
    }
}
