package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenGradeAnalysis
import cn.limpu.hita.data.model.eas.ShenzhenGradeComponent
import cn.limpu.hita.data.model.eas.ShenzhenGradeCourse
import cn.limpu.hita.data.model.eas.ShenzhenGradeStatus
import cn.limpu.hita.data.model.eas.ShenzhenScoreBand
import cn.limpu.hita.data.model.eas.ShenzhenStudentGrade
import cn.limpu.hita.data.model.eas.TermItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import java.util.Locale

internal object ShenzhenGradeParser {
    fun parseStudentRecordId(body: String): String? {
        val root = objectRoot(body) ?: return null
        val rows = arrayAt(root, "content") ?: return null
        return rows.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?.let { text(it, "xjid", "XJID", "id", "ID") }
            ?.takeIf { it.isNotBlank() }
    }

    fun parseCourses(
        publishedBody: String,
        selectedBody: String,
        earlyBody: String?,
        term: TermItem
    ): List<ShenzhenGradeCourse>? {
        val publishedRoot = objectRoot(publishedBody) ?: return null
        val publishedRows = arrayAt(publishedRoot, "content", "list") ?: JsonArray()
        val selectedRoot = objectRoot(selectedBody) ?: return null
        val selectedRows = arrayAt(selectedRoot, "yxkcList") ?: JsonArray()
        val earlyRows = earlyBody?.let(::objectRoot)?.let { arrayAt(it, "pageInfo", "list") }
            ?: JsonArray()

        val courses = linkedMapOf<String, ShenzhenGradeCourse>()
        publishedRows.forEachObject { row ->
            val course = courseFrom(row, ShenzhenGradeStatus.PUBLISHED, term)
            if (course.taskId.isNotBlank()) courses[course.taskId] = course
        }
        selectedRows.forEachObject { row ->
            val course = courseFrom(row, ShenzhenGradeStatus.SELECTED, term)
            if (course.taskId.isNotBlank()) courses.putIfAbsent(course.taskId, course)
        }

        earlyRows.forEachObject { row ->
            val early = courseFrom(row, ShenzhenGradeStatus.EARLY, term)
            if (early.myScore == null) return@forEachObject
            val matchKey = early.taskId.takeIf(courses::containsKey)
                ?: courses.entries.firstOrNull {
                    early.taskNumber.isNotBlank() && it.value.taskNumber == early.taskNumber
                }?.key
                ?: courses.entries.firstOrNull {
                    normalizedCourseName(it.value.courseName) ==
                        normalizedCourseName(early.courseName)
                }?.key
            if (matchKey != null) {
                val old = courses.getValue(matchKey)
                if (old.status != ShenzhenGradeStatus.PUBLISHED) {
                    courses[matchKey] = old.copy(
                        myScore = early.myScore ?: old.myScore,
                        status = ShenzhenGradeStatus.EARLY,
                        taskNumber = early.taskNumber.ifBlank { old.taskNumber }
                    )
                }
            } else if (early.taskId.isNotBlank()) {
                courses[early.taskId] = early
            }
        }
        return courses.values.sortedWith(
            compareBy<ShenzhenGradeCourse> { it.status.ordinal }.thenBy { it.courseName }
        )
    }

    fun earlyScoreDiagnostics(body: String?): Pair<Int, Int> {
        val rows = body?.let(::objectRoot)?.let { arrayAt(it, "pageInfo", "list") }
            ?: return 0 to 0
        var scoreCount = 0
        rows.forEachObject { row ->
            if (number(row, "zzzscj", "ZZZSCJ") != null) scoreCount++
        }
        return rows.size() to scoreCount
    }

    fun analyze(course: ShenzhenGradeCourse, body: String): ShenzhenGradeAnalysis? {
        val parsed = runCatching { JsonParser().parse(body) }.getOrNull() ?: return null
        val rows = when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject -> arrayAt(parsed.asJsonObject, "content")
                ?: arrayAt(parsed.asJsonObject, "list")
                ?: return null
            else -> return null
        }

        data class Definition(val full: Double, val weight: Double)
        val definitions = linkedMapOf<String, Definition>()
        val rawByStudent = linkedMapOf<String, MutableMap<String, Double?>>()
        rows.forEachObject { row ->
            val studentId = text(row, "XSCJB_ID", "xscjb_id")
            val name = text(row, "FXMC", "fxmc")
            if (studentId.isBlank() || name.isBlank()) return@forEachObject
            val full = number(row, "MF", "mf")?.takeIf { it > 0 } ?: 100.0
            val weight = number(row, "LJFXBZ", "ljfxbz") ?: 0.0
            definitions.putIfAbsent(name, Definition(full, weight))
            rawByStudent.getOrPut(studentId) { linkedMapOf() }[name] =
                number(row, "DF", "df")
        }
        if (rawByStudent.isEmpty() || definitions.isEmpty()) return null

        val totalWeight = definitions.values.sumOf { it.weight }
        val allStudents = rawByStudent.map { (studentId, values) ->
            val components = definitions.map { (name, definition) ->
                ShenzhenGradeComponent(
                    name = name,
                    score = values[name],
                    fullScore = definition.full,
                    weight = definition.weight
                )
            }
            val weighted = components.sumOf { component ->
                (component.score ?: 0.0) / component.fullScore * 100.0 * component.weight / 100.0
            }
            val normalized = if (totalWeight > 0.0 && abs(totalWeight - 100.0) > 0.0001) {
                weighted / totalWeight * 100.0
            } else {
                weighted
            }
            ShenzhenStudentGrade(studentId, rounded(normalized), components)
        }.sortedByDescending { it.total }

        val incompleteStudentCount = allStudents.count { student ->
            student.components.any { it.score == null }
        }
        // The reference implementation treats a missing component as zero. Keeping those
        // rows is also required when an unpublished total is used to locate this student.
        val students = allStudents
        if (students.isEmpty()) return null

        val totals = students.map { it.total }.sorted()
        val mean = totals.average()
        val median = if (totals.size % 2 == 0) {
            (totals[totals.size / 2 - 1] + totals[totals.size / 2]) / 2.0
        } else {
            totals[totals.size / 2]
        }
        val stdev = if (totals.size > 1) {
            sqrt(totals.sumOf { (it - mean).pow(2) } / (totals.size - 1))
        } else 0.0

        val exactId = course.recordId.takeIf { id -> students.any { it.anonymousId == id } }
        val scoreMatches = if (exactId == null && course.myScore != null) {
            students.filter { abs(it.total - course.myScore) <= 0.05 }
        } else emptyList()
        val myId = exactId ?: scoreMatches.firstOrNull()?.anonymousId
        val myTotal = students.firstOrNull { it.anonymousId == myId }?.total
        val rank = myTotal?.let { score -> students.count { it.total > score } + 1 }

        return ShenzhenGradeAnalysis(
            course = course,
            students = students,
            componentDefinitions = definitions.map { (name, definition) ->
                ShenzhenGradeComponent(name, null, definition.full, definition.weight)
            },
            mean = rounded(mean),
            median = rounded(median),
            standardDeviation = rounded(stdev),
            maximum = totals.maxOrNull() ?: 0.0,
            minimum = totals.minOrNull() ?: 0.0,
            failCount = totals.count { it < 60.0 },
            excludedIncompleteStudentCount = incompleteStudentCount,
            myStudentId = myId,
            myScore = myTotal,
            myRank = rank,
            percentile = rank?.let { rounded((1.0 - (it - 1.0) / students.size) * 100.0) },
            identityMatchCount = if (exactId != null) 1 else scoreMatches.size,
            bands = listOf(
                band("90–100", totals, 90.0, Double.POSITIVE_INFINITY),
                band("80–89", totals, 80.0, 90.0),
                band("70–79", totals, 70.0, 80.0),
                band("60–69", totals, 60.0, 70.0),
                band("< 60", totals, Double.NEGATIVE_INFINITY, 60.0)
            )
        )
    }

    private fun courseFrom(
        row: JsonObject,
        status: ShenzhenGradeStatus,
        term: TermItem
    ): ShenzhenGradeCourse {
        val taskId = text(row, "rwid", "RWID")
        return ShenzhenGradeCourse(
            rowId = text(row, "id", "ID"),
            recordId = if (status == ShenzhenGradeStatus.PUBLISHED) text(row, "id", "ID") else "",
            taskId = taskId,
            taskNumber = text(row, "rwh", "RWH"),
            courseCode = text(row, "kcdm", "KCDM"),
            courseName = text(row, "kcmc", "KCMC", "rwmc", "RWMC").ifBlank { "未命名课程" },
            termCode = text(row, "xnxq", "XNXQ", "xnxqdm", "XNXQDM")
                .ifBlank { term.getCode() },
            teacher = text(row, "dgjsmc", "DGJSMC", "jsxm", "JSXM"),
            credits = number(row, "xf", "XF"),
            myScore = number(row, "xscj", "XSCJ", "zzcj", "ZZCJ", "zzzscj", "ZZZSCJ"),
            status = status
        )
    }

    private fun band(label: String, values: List<Double>, from: Double, until: Double) =
        ShenzhenScoreBand(label, values.count { it >= from && it < until })

    private fun normalizedCourseName(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s·・•]+"), "")
        .replace('（', '(')
        .replace('）', ')')

    private fun rounded(value: Double): Double = round(value * 100.0) / 100.0

    private fun objectRoot(body: String): JsonObject? = runCatching { JsonParser().parse(body) }
        .getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

    private fun arrayAt(root: JsonObject, vararg path: String): JsonArray? {
        var current: JsonElement = root
        path.forEach { key ->
            current = current.takeIf { it.isJsonObject }?.asJsonObject?.get(key) ?: return null
        }
        return current.takeIf { it.isJsonArray }?.asJsonArray
    }

    private inline fun JsonArray.forEachObject(block: (JsonObject) -> Unit) {
        forEach { element -> if (element.isJsonObject) block(element.asJsonObject) }
    }

    private fun text(row: JsonObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = row.get(key) ?: return@forEach
            if (!value.isJsonPrimitive) return@forEach
            val candidate = runCatching { value.asString }.getOrNull()?.trim().orEmpty()
            if (candidate.isNotBlank() && !candidate.equals("null", true)) return candidate
        }
        return ""
    }

    private fun number(row: JsonObject, vararg keys: String): Double? {
        keys.forEach { key ->
            val value = row.get(key) ?: return@forEach
            if (!value.isJsonPrimitive) return@forEach
            runCatching { value.asDouble }.getOrNull()?.let { return it }
        }
        return null
    }
}
