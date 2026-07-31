package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.TermItem
import org.jsoup.nodes.Document

internal object BenbuTermParser {
    fun parseTerms(doc: Document, selectName: String): List<TermItem> {
        val options = doc.select("select[name=$selectName] option")
        val currentValue = doc.select("select[name=$selectName] option[selected]")
            .attr("value")
            .trim()
            .ifEmpty { options.firstOrNull()?.attr("value")?.trim().orEmpty() }

        return options.mapIndexedNotNull { index, option ->
            val value = option.attr("value").trim()
            if (value.isBlank()) return@mapIndexedNotNull null
            parseTermValue(value, option.text().trim())?.apply {
                isCurrent = value == currentValue || (currentValue.isEmpty() && index == 0)
            }
        }
    }

    fun mergeTerms(primary: List<TermItem>, secondary: List<TermItem>): MutableList<TermItem> {
        val merged = LinkedHashMap<String, TermItem>()
        secondary.forEach { term -> merged[term.getCode()] = term }
        primary.forEach { term ->
            val existing = merged[term.getCode()]
            if (existing == null) {
                merged[term.getCode()] = term
            } else {
                if (term.termName.isNotBlank()) existing.termName = term.termName
                if (term.name.isNotBlank()) existing.name = term.name
                existing.isCurrent = existing.isCurrent || term.isCurrent
            }
        }
        return merged.values.toMutableList()
    }

    fun parseTermValue(value: String, label: String): TermItem? {
        val cleanedValue = value.trim()
        val fullMatch = Regex("""(\d{4}-\d{4})(\d+)""").matchEntire(cleanedValue)
        val rawYearCode = fullMatch?.groupValues?.get(1)
            ?: Regex("""\d{4}-\d{4}""").find(cleanedValue)?.value
            ?: Regex("""\d{4}""").find(cleanedValue)?.value
            ?: return null
        val yearCode = if (rawYearCode.length == 4) {
            val startYear = rawYearCode.toIntOrNull() ?: return null
            "$startYear-${startYear + 1}"
        } else {
            rawYearCode
        }
        val termCode = fullMatch?.groupValues?.get(2)
            ?: Regex("""\d+$""").find(cleanedValue)?.value
            ?: return null

        val normalizedLabel = normalizeTermLabel(label)
        val derivedTermName = when {
            normalizedLabel.isNotBlank() -> normalizedLabel
            termCode.startsWith("1") -> "$yearCode 秋季学期"
            termCode.startsWith("2") -> "$yearCode 春季学期"
            termCode.startsWith("3") -> "$yearCode 夏季学期"
            termCode.startsWith("4") -> "$yearCode 冬季学期"
            else -> "$yearCode $termCode"
        }

        return TermItem(yearCode, yearCode, termCode, derivedTermName).apply {
            name = derivedTermName
        }
    }

    private fun normalizeTermLabel(label: String): String {
        val compact = label.replace(Regex("""\s+"""), "").trim()
        if (compact.isBlank()) return ""

        val yearCode = Regex("""\d{4}-\d{4}""").find(compact)?.value
            ?: Regex("""(\d{4})学年""").find(compact)?.groupValues?.getOrNull(1)?.let {
                "$it-${it.toInt() + 1}"
            }
            ?: ""
        val season = when {
            compact.contains("秋") || compact.contains("第一") || compact.contains("上学期") -> "秋季学期"
            compact.contains("春") || compact.contains("第二") || compact.contains("下学期") -> "春季学期"
            compact.contains("夏") || compact.contains("第三") -> "夏季学期"
            compact.contains("寒") || compact.contains("冬") || compact.contains("第四") -> "冬季学期"
            else -> ""
        }
        return when {
            yearCode.isNotBlank() && season.isNotBlank() -> "$yearCode $season"
            compact.contains("学年") && season.isNotBlank() -> compact
                .replace("学年", "学年 ")
                .replace("学期", "学期 ")
                .trim()
            else -> compact
        }
    }
}
