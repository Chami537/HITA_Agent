package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogPage
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseMeeting
import cn.limpu.hita.data.model.eas.ShenzhenCourseAttachment
import cn.limpu.hita.data.model.eas.ShenzhenCourseAttachmentKind
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTime
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTimeSource
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.TermItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

internal object ShenzhenCourseCatalogParser {
    fun parseSelectionPools(body: String): List<ShenzhenSelectionPool> {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val rows = parsed.get("xkgzszList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val code = first(row, "xkfsdm", "XKFSDM")
            if (code.isBlank()) return@mapNotNull null
            ShenzhenSelectionPool(
                code = code,
                name = first(row, "xkfsmc", "XKFSMC").ifBlank { code },
                selectionOpenTime = parseOpenTime(row, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
                    ?: parseOpenTime(
                        row.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
                        ShenzhenSelectionOpenTimeSource.SELECTION_RULE
                    )
            )
        }.distinctBy { it.code }
    }

    fun parseSelectionOpenTime(body: String): ShenzhenSelectionOpenTime? {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val payload = parsed.get("content")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: parsed.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: parsed
        return parseOpenTime(payload, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
            ?: parseOpenTime(parsed, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
            ?: parseOpenTime(
                payload.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
                ShenzhenSelectionOpenTimeSource.SELECTION_RULE
            )
            ?: parseOpenTime(
                parsed.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
                ShenzhenSelectionOpenTimeSource.SELECTION_RULE
            )
    }

    fun parseSelectionTermId(body: String): String? {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val payload = parsed.get("content")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: parsed.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: parsed
        // p_xn/p_xq are the actual course-selection term. p_dq* is only the
        // academic calendar's current-term fallback (for example summer while
        // autumn course selection is already open).
        val yearKeys = arrayOf("p_xn", "xn", "XN", "p_dqxn", "dqxn")
        val termKeys = arrayOf("p_xq", "xq", "XQ", "p_dqxq", "dqxq")
        val year = first(payload, *yearKeys).ifBlank { first(parsed, *yearKeys) }
        val term = first(payload, *termKeys).ifBlank { first(parsed, *termKeys) }
        return if (year.isBlank() || term.isBlank()) null else "$year-$term"
    }

    fun parseTerms(body: String): List<TermItem>? {
        val parsed: JsonElement = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        val rows = mutableListOf<JsonObject>()
        collectTermRows(parsed, rows)
        val result = rows.mapNotNull { row ->
            val year = first(row, "XN", "xn")
            val term = first(row, "XQ", "xq")
            if (year.isBlank() || term.isBlank()) return@mapNotNull null
            val yearName = first(row, "XNMC", "xnmc").ifBlank { year }
            val termName = first(row, "XQMC", "xqmc", "XNXQMC", "xnxqmc")
            TermItem(
                yearCode = year,
                yearName = yearName,
                termCode = term,
                termName = termName
            ).apply {
                name = listOf(yearName, termName).joinToString("")
                    .ifBlank { first(row, "XNXQMC", "xnxqmc", "XNXQ", "xnxq") }
                    .ifBlank { "$year-$term" }
                isCurrent = first(row, "SFDQXQ", "sfdqxq") == "1"
            }
        }.distinctBy { it.id }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun collectTermRows(element: JsonElement, rows: MutableList<JsonObject>) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectTermRows(it, rows) }
            element.isJsonObject -> {
                val row = element.asJsonObject
                val hasTermIdentity = first(row, "XNXQ", "xnxq").isNotBlank() ||
                    (first(row, "XN", "xn").isNotBlank() && first(row, "XQ", "xq").isNotBlank())
                if (hasTermIdentity) {
                    rows += row
                } else {
                    row.entrySet().forEach { (_, value) -> collectTermRows(value, rows) }
                }
            }
        }
    }

    fun parsePage(
        body: String,
        source: ShenzhenCourseCatalogSource,
        studentType: String,
        selectionPoolName: String = "",
        fallbackOpenTime: ShenzhenSelectionOpenTime? = null
    ): ShenzhenCourseCatalogPage? {
        val parsed: JsonElement = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        if (!parsed.isJsonObject) return null
        val root = parsed.asJsonObject
        val pageKey = if (source == ShenzhenCourseCatalogSource.AVAILABLE) "kxrwList" else "rwList"
        val pageElement = root.get(pageKey) ?: root.get("yxkcList")
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
        val contextOpenTime =
            parseOpenTime(pageObject, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
                ?: parseOpenTime(root, ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE)
                ?: parseOpenTime(
                    pageObject.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
                    ShenzhenSelectionOpenTimeSource.SELECTION_RULE
                )
                ?: parseOpenTime(
                    root.get("xkgzszOne")?.takeIf { it.isJsonObject }?.asJsonObject,
                    ShenzhenSelectionOpenTimeSource.SELECTION_RULE
                )
                ?: fallbackOpenTime

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
                val rawSchedule = first(row, "pkjgmx", "PKJGMX", "sksj", "SKSJ")
                    .ifBlank {
                        if (source == ShenzhenCourseCatalogSource.SCHOOL) {
                            first(row, "xksj", "XKSJ")
                        } else {
                            ""
                        }
                    }
                val teacher = first(row, "dgjsmc", "DGJSMC", "jsmc", "JSMC", "skjs", "SKJS")
                add(
                    ShenzhenCourseCatalogItem(
                        id = first(row, "rwh", "RWH", "rwid", "RWID", "id", "ID")
                            .ifBlank { "$code-$index" },
                        taskId = first(row, "rwh", "RWH", "rwid", "RWID"),
                        selectionRequestId = first(row, "id", "ID", "rwid", "RWID"),
                        courseId = first(row, "kcid", "KCID"),
                        taskNumber = first(row, "rwh", "RWH"),
                        courseCode = code,
                        courseName = name,
                        teacher = teacher,
                        credits = first(row, "xf", "XF"),
                        totalHours = first(row, "zxs", "ZXS", "xszxs", "XSZXS"),
                        courseNature = first(row, "kcxzmc", "KCXZMC", "kcxz", "KCXZ"),
                        courseCategory = first(row, "kclbmc", "KCLBMC", "kclb", "KCLB"),
                        offeringCollege = first(row, "kkyxmc", "KKYXMC", "kkyx", "KKYX"),
                        campus = first(row, "xiaoqumc", "XIAOQUMC", "xiaoqu", "XIAOQU"),
                        schedule = plainText(rawSchedule),
                        selectionRequirement = first(row, "xkyq", "XKYQ"),
                        teachingLanguage = first(row, "skyymc", "SKYYMC"),
                        trainingLevel = first(row, "pyccmc", "PYCCMC"),
                        capacity = first(row, *capacityKeys).toDoubleOrNull()?.toInt(),
                        selectedCount = first(row, *selectedKeys).toDoubleOrNull()?.toInt(),
                        hasConflict = first(row, "sfkct", "SFKCT") == "1" ||
                            first(row, "ctkcxx", "CTKCXX").isNotBlank(),
                        conflictDescription = plainText(first(row, "ctkcxx", "CTKCXX")),
                        selectionPoolName = selectionPoolName.ifBlank {
                            first(row, "xkfsmc", "XKFSMC")
                        },
                        classNumber = first(
                            row,
                            "bjh", "BJH", "rwbh", "RWBH", "jxbh", "JXBH", "dgbjmc", "DGBJMC"
                        ),
                        meetings = parseMeetings(row, rawSchedule, teacher),
                        source = source,
                        selectionOpenTime = parseOpenTime(
                            row,
                            ShenzhenSelectionOpenTimeSource.COURSE
                        ) ?: contextOpenTime
                    )
                )
            }
        }
        return ShenzhenCourseCatalogPage(
            items,
            total.coerceAtLeast(items.size),
            page,
            pageSize,
            contextOpenTime
        )
    }

    fun parseAttachments(body: String, courseId: String): List<ShenzhenCourseAttachment>? {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val content = parsed.get("content")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        val course = content.get("kcxxbgbEntity")
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val syllabus = content.get("kcdgbentity")
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        return buildList {
            addAttachment(
                name = first(course, "kcjjfname"),
                serverPath = first(course, "kcjjsname"),
                kind = ShenzhenCourseAttachmentKind.COURSE_DESCRIPTION,
                courseId = courseId
            )
            addAttachment(
                name = first(syllabus, "kczwdgwjm"),
                serverPath = first(syllabus, "kczwdgurl"),
                kind = ShenzhenCourseAttachmentKind.CHINESE_SYLLABUS,
                courseId = courseId,
                sizeBytes = first(syllabus, "kczwdgsize").toLongOrNull()
            )
            addAttachment(
                name = first(syllabus, "kcywdgwjm"),
                serverPath = first(syllabus, "kcywdgurl"),
                kind = ShenzhenCourseAttachmentKind.ENGLISH_SYLLABUS,
                courseId = courseId,
                sizeBytes = first(syllabus, "kcywdgsize").toLongOrNull()
            )
        }.distinctBy { Triple(it.kind, it.name, it.serverPath) }
    }

    private fun MutableList<ShenzhenCourseAttachment>.addAttachment(
        name: String,
        serverPath: String,
        kind: ShenzhenCourseAttachmentKind,
        courseId: String,
        sizeBytes: Long? = null
    ) {
        if (name.isBlank()) return
        if (kind == ShenzhenCourseAttachmentKind.COURSE_DESCRIPTION && serverPath.isBlank()) return
        if (kind != ShenzhenCourseAttachmentKind.COURSE_DESCRIPTION && courseId.isBlank()) return
        add(
            ShenzhenCourseAttachment(
                name = name,
                serverPath = serverPath,
                kind = kind,
                courseId = courseId,
                sizeBytes = sizeBytes
            )
        )
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

    internal fun parseMeetings(
        row: JsonObject,
        rawSchedule: String,
        fallbackTeacher: String = ""
    ): List<ShenzhenCourseMeeting> {
        val structured = sequenceOf("sksjList", "SKSJLIST", "timeList")
            .mapNotNull { key -> row.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .firstOrNull()
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let { meeting ->
                    parseStructuredMeeting(meeting, row, fallbackTeacher)
                }
            }
            .orEmpty()
        if (structured.isNotEmpty()) return structured.distinct()

        parseStructuredMeeting(row, row, fallbackTeacher)?.let { return listOf(it) }
        if (rawSchedule.isBlank()) return emptyList()

        val fragments = Jsoup.parseBodyFragment(rawSchedule).body()
            .select("p, li")
            .map { it.text() }
            .ifEmpty {
                plainText(rawSchedule).split(Regex("[;；\\n]+"))
            }
        return fragments.mapNotNull { fragment ->
            val weekday = parseWeekday(fragment) ?: return@mapNotNull null
            val period = PERIOD_REGEX.find(fragment) ?: return@mapNotNull null
            val begin = period.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val end = period.groupValues[2].toIntOrNull() ?: begin
            val weeks = parseWeeks(fragment)
            if (weeks.isEmpty()) return@mapNotNull null
            val location = fragment.substringAfter("节", "")
                .trim(' ', ',', '，', ';', '；', '[', ']', '【', '】')
            ShenzhenCourseMeeting(
                weeks = weeks,
                weekday = weekday,
                beginPeriod = begin,
                endPeriod = end,
                teacher = fallbackTeacher,
                location = location
            )
        }.distinct()
    }

    private fun parseStructuredMeeting(
        row: JsonObject,
        fallback: JsonObject,
        fallbackTeacher: String
    ): ShenzhenCourseMeeting? {
        if (first(row, "key", "KEY").equals("bz", ignoreCase = true)) return null
        val weekday = parseWeekday(firstNonBlank(row, fallback, "xqj", "XQJ", "xq", "XQ", "weekday"))
            ?: return null
        val begin = firstNonBlank(row, fallback, "ksjc", "KSJC", "qsjc", "QSJC", "beginPeriod")
            .toDoubleOrNull()?.toInt() ?: return null
        val end = firstNonBlank(row, fallback, "jsjc", "JSJC", "zzjc", "ZZJC", "endPeriod")
            .toDoubleOrNull()?.toInt() ?: begin
        val weeks = parseWeeks(firstNonBlank(row, fallback, "zc", "ZC", "skzc", "SKZC", "weeks"))
        if (weeks.isEmpty()) return null
        return ShenzhenCourseMeeting(
            weeks = weeks,
            weekday = weekday,
            beginPeriod = begin,
            endPeriod = end,
            teacher = firstNonBlank(row, fallback, "skjs", "SKJS", "jsxm", "JSXM", "teacher")
                .ifBlank { fallbackTeacher },
            location = firstNonBlank(
                row,
                fallback,
                "jasmc", "JASMC", "cdmc", "CDMC", "skdd", "SKDD", "location"
            )
        )
    }

    private fun firstNonBlank(primary: JsonObject, fallback: JsonObject, vararg keys: String): String =
        first(primary, *keys).ifBlank { first(fallback, *keys) }

    internal fun parseWeeks(raw: String): List<Int> {
        val normalized = raw.trim()
            .replace('－', '-')
            .replace('—', '-')
            .replace('~', '-')
            .replace("至", "-")
        if (normalized.length in 33..34 && normalized.all { it == '0' || it == '1' }) {
            return normalized.takeLast(33).mapIndexedNotNull { index, value ->
                if (value == '1') index + 1 else null
            }
        }
        val expression = WEEK_REGEX.find(normalized)?.groupValues?.getOrNull(1) ?: normalized
        val parity = when {
            normalized.contains('单') -> 1
            normalized.contains('双') -> 0
            else -> null
        }
        return buildSet {
            expression.split(',', '，', '、').forEach { component ->
                val numbers = NUMBER_REGEX.findAll(component).mapNotNull { it.value.toIntOrNull() }.toList()
                when {
                    numbers.size >= 2 && component.contains('-') -> {
                        val first = numbers[0]
                        val last = numbers[1]
                        if (first <= last) {
                            (first..last).filterTo(this) { week ->
                                week in 1..33 && (parity == null || week % 2 == parity)
                            }
                        }
                    }
                    numbers.isNotEmpty() -> numbers.filterTo(this) { week ->
                        week in 1..33 && (parity == null || week % 2 == parity)
                    }
                }
            }
        }.sorted()
    }

    private fun parseWeekday(raw: String): Int? {
        raw.trim().toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        return when {
            raw.contains("星期一") || raw.contains("周一") -> 1
            raw.contains("星期二") || raw.contains("周二") -> 2
            raw.contains("星期三") || raw.contains("周三") -> 3
            raw.contains("星期四") || raw.contains("周四") -> 4
            raw.contains("星期五") || raw.contains("周五") -> 5
            raw.contains("星期六") || raw.contains("周六") -> 6
            raw.contains("星期日") || raw.contains("星期天") || raw.contains("周日") || raw.contains("周天") -> 7
            else -> null
        }
    }

    private fun plainText(value: String): String {
        if (value.isBlank()) return ""
        return Jsoup.parseBodyFragment(value).text()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseOpenTime(
        row: JsonObject?,
        source: ShenzhenSelectionOpenTimeSource
    ): ShenzhenSelectionOpenTime? {
        if (row == null) return null
        for (key in OPEN_TIME_KEYS) {
            val raw = first(row, key)
            if (raw.isBlank()) continue
            val normalized = raw.replace(' ', 'T')
            val epochMillis = runCatching {
                OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
            }.recoverCatching {
                LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(SHENZHEN_ZONE)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: continue
            return ShenzhenSelectionOpenTime(raw, epochMillis, source)
        }
        return null
    }

    private val OPEN_TIME_KEYS = arrayOf("ktxkkssj", "KTXKKSSJ", "ksrq", "KSRQ")
    private val SHENZHEN_ZONE = ZoneId.of("Asia/Shanghai")
    private val WEEK_REGEX = Regex("(?:第)?([0-9、,，\\-~至单双]+)周")
    private val PERIOD_REGEX = Regex("(?:第)?(\\d{1,2})(?:[-~至](\\d{1,2}))?节")
    private val NUMBER_REGEX = Regex("\\d+")
}
