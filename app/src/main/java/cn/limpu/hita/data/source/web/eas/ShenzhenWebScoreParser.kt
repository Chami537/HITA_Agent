package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.CourseScoreItem
import cn.limpu.hita.data.model.eas.TermItem
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class ParsedShenzhenWebScores(
    val code: Int,
    val message: String,
    val items: List<CourseScoreItem>
)

internal object ShenzhenWebScoreParser {
    fun parse(body: String, term: TermItem): ParsedShenzhenWebScores? {
        val parsed: JsonElement = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        if (!parsed.isJsonObject) return null
        val root = parsed.asJsonObject
        val code = number(root, "code")?.toInt() ?: 0
        val message = text(root, "msg", "message")
        val rows = root.get("content")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("list")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return ParsedShenzhenWebScores(code, message, emptyList())
        val items = buildList<CourseScoreItem> {
            rows.forEach { element ->
                val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val scoreText = text(row, "xscj", "zzcj", "zpcj")
                add(CourseScoreItem().apply {
                    courseCode = text(row, "kcdm")
                    courseName = text(row, "kcmc")
                    credits = number(row, "xf")?.toFloat() ?: 0f
                    hours = number(row, "xs")?.toInt() ?: 0
                    finalScoresText = scoreText.takeIf { it.isNotBlank() }
                    finalScores = scoreText.toDoubleOrNull()?.toInt() ?: -1
                    courseProperty = text(row, "kcxz", "kcxzen")
                    courseCategory = text(row, "kclb", "kclben")
                    schoolName = text(row, "yxmc")
                    assessMethod = text(row, "khfs")
                    termName = text(row, "xnxqmc").ifBlank { term.name }
                })
            }
        }
        return ParsedShenzhenWebScores(code, message, items)
    }

    private fun text(objectValue: JsonObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = objectValue.get(key) ?: return@forEach
            if (value.isJsonNull || !value.isJsonPrimitive) return@forEach
            val text = runCatching { value.asString }.getOrNull()?.trim().orEmpty()
            if (text.isNotBlank() && !text.equals("null", ignoreCase = true)) return text
        }
        return ""
    }

    private fun number(objectValue: JsonObject, key: String): Double? {
        val value = objectValue.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble }.getOrNull()
    }
}
