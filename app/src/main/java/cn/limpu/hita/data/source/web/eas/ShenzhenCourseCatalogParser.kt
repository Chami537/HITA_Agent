package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogPage
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.TermItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object ShenzhenCourseCatalogParser {
    fun parseSelectionTermId(body: String): String? {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val year = first(parsed, "p_dqxn", "p_xn")
        val term = first(parsed, "p_dqxq", "p_xq")
        return if (year.isBlank() || term.isBlank()) null else "$year-$term"
    }

    fun parseTerms(body: String): List<TermItem>? {
        val parsed: JsonElement = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        if (!parsed.isJsonArray) return null
        val rows = parsed.asJsonArray
        val result = buildList<TermItem> {
            rows.forEach { element ->
                val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val year = first(row, "XN")
                val term = first(row, "XQ")
                if (year.isBlank() || term.isBlank()) return@forEach
                add(
                    TermItem(
                        yearCode = year,
                        yearName = first(row, "XNMC").ifBlank { year },
                        termCode = term,
                        termName = first(row, "XQMC", "XNXQMC")
                    ).apply {
                        name = first(row, "XNXQMC").ifBlank { "$year-$term" }
                        isCurrent = first(row, "SFDQXQ") == "1"
                    }
                )
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    fun parsePage(
        body: String,
        source: ShenzhenCourseCatalogSource,
        studentType: String,
        selectionPoolName: String = ""
    ): ShenzhenCourseCatalogPage? {
        val parsed: JsonElement = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        if (!parsed.isJsonObject) return null
        val root = parsed.asJsonObject
        val pageKey = if (source == ShenzhenCourseCatalogSource.AVAILABLE) "kxrwList" else "rwList"
        val pageElement = root.get(pageKey)
        val pageObject = pageElement?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        val rows = when {
            pageObject.get("list")?.isJsonArray == true -> pageObject.getAsJsonArray("list")
            pageElement?.isJsonArray == true -> pageElement.asJsonArray
            else -> JsonArray()
        }
        val page = intValue(pageObject, "pageNum") ?: intValue(root, "pageNum") ?: 1
        val pageSize = intValue(pageObject, "pageSize")
            ?: intValue(root, "pageSize")
            ?: rows.size().coerceAtLeast(20)
        val total = intValue(pageObject, "total") ?: intValue(root, "total") ?: rows.size()

        val items = buildList<ShenzhenCourseCatalogItem> {
            rows.forEachIndexed { index, element ->
                val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEachIndexed
                val code = first(row, "kcdm", "KCDM")
                val name = first(row, "kcmc", "KCMC", "rwmc", "RWMC")
                if (code.isBlank() && name.isBlank()) return@forEachIndexed
                val capacityKeys = if (studentType == "2") {
                    arrayOf("yjsrl", "YJSRL", "zrl", "ZRL")
                } else {
                    arrayOf("bksrl", "BKSRL", "zrl", "ZRL")
                }
                val selectedKeys = if (studentType == "2") {
                    arrayOf("yjsyxrs", "YJSYXRS", "yxzrs", "YXZRS")
                } else {
                    arrayOf("bksyxrs", "BKSYXRS", "yxzrs", "YXZRS")
                }
                add(
                    ShenzhenCourseCatalogItem(
                        id = first(row, "rwh", "RWH", "rwid", "RWID", "id", "ID")
                            .ifBlank { "$code-$index" },
                        courseCode = code,
                        courseName = name,
                        teacher = first(row, "dgjsmc", "DGJSMC", "jsmc", "JSMC"),
                        credits = first(row, "xf", "XF"),
                        totalHours = first(row, "zxs", "ZXS", "xszxs", "XSZXS"),
                        courseNature = first(row, "kcxzmc", "KCXZMC", "kcxz", "KCXZ"),
                        courseCategory = first(row, "kclbmc", "KCLBMC", "kclb", "KCLB"),
                        offeringCollege = first(row, "kkyxmc", "KKYXMC", "kkyx", "KKYX"),
                        campus = first(row, "xiaoqumc", "XIAOQUMC", "xiaoqu", "XIAOQU"),
                        schedule = first(row, "xksj", "XKSJ", "sksj", "SKSJ", "pkjgmx", "PKJGMX")
                            .replace(Regex("\\s+"), " ")
                            .trim(),
                        teachingLanguage = first(row, "skyymc", "SKYYMC"),
                        trainingLevel = first(row, "pyccmc", "PYCCMC"),
                        capacity = first(row, *capacityKeys).toDoubleOrNull()?.toInt(),
                        selectedCount = first(row, *selectedKeys).toDoubleOrNull()?.toInt(),
                        selectionPoolName = selectionPoolName.ifBlank {
                            first(row, "xkfsmc", "XKFSMC")
                        },
                        source = source
                    )
                )
            }
        }
        return ShenzhenCourseCatalogPage(items, total.coerceAtLeast(items.size), page, pageSize)
    }

    private fun first(objectValue: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val value = objectValue.get(key) ?: continue
            if (value.isJsonNull || !value.isJsonPrimitive) continue
            val text = runCatching { value.asString }.getOrNull()?.trim().orEmpty()
            if (text.isNotBlank() && !text.equals("null", ignoreCase = true)) return text
        }
        return ""
    }

    private fun intValue(objectValue: JsonObject, key: String): Int? {
        val value = objectValue.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble.toInt() }.getOrNull()
    }
}
