package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlan
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanCategory
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanCourse
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanDetail
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanGroup
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanIdentity
import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanLevel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object ShenzhenTrainingPlanParser {
    fun parseIdentity(body: String): ShenzhenTrainingPlanIdentity? {
        val objects = allObjects(parse(body) ?: return null)
        val source = objects.firstOrNull {
            first(it, "ZYMC", "zymc").isNotBlank() || first(it, "NJMC", "njmc", "NJDM", "njdm").isNotBlank()
        } ?: return null
        return ShenzhenTrainingPlanIdentity(
            major = first(source, "ZYMC", "zymc"),
            grade = first(source, "NJDM", "njdm", "NJMC", "njmc"),
            studentType = first(source, "PYLX", "pylx")
        )
    }

    fun parsePlans(body: String, level: ShenzhenTrainingPlanLevel): List<ShenzhenTrainingPlan>? {
        val root = parse(body) ?: return null
        return allObjects(root).mapNotNull { row ->
            val id = first(row, "fah", "FAH")
            if (id.isBlank()) return@mapNotNull null
            ShenzhenTrainingPlan(
                id = id,
                changeId = first(row, "bgid", "BGID"),
                name = first(row, "famc", "FAMC").ifBlank { "培养方案 $id" },
                majorCode = if (level == ShenzhenTrainingPlanLevel.POSTGRADUATE) {
                    first(row, "zyfxdm", "ZYFXDM", "zydm", "ZYDM")
                } else first(row, "zydm", "ZYDM"),
                majorName = first(row, "zymc", "ZYMC"),
                majorDirection = first(row, "zyfxmc", "ZYFXMC", "zyfxdm", "ZYFXDM"),
                schoolCode = first(row, "yxdm", "YXDM"),
                schoolName = first(row, "yxmc", "YXMC"),
                grade = first(row, "njdm", "NJDM"),
                version = first(row, "bbh", "BBH"),
                programType = first(row, "falxmc", "FALXMC"),
                degreeName = first(row, "xwmc", "XWMC", "xwlbmc", "XWLBMC"),
                level = level
            )
        }.distinctBy { it.id }.takeIf { it.isNotEmpty() }
    }

    fun matchPersonalPlans(plans: List<ShenzhenTrainingPlan>, major: String): List<ShenzhenTrainingPlan> {
        val expected = normalizeName(major)
        if (expected.isBlank()) return emptyList()
        val exact = plans.filter { plan ->
            listOf(plan.majorName, plan.majorDirection).any { normalizeName(it) == expected }
        }
        if (exact.isNotEmpty()) return exact
        return plans.filter { plan ->
            listOf(plan.majorName, plan.majorDirection, plan.name).any { value ->
                val candidate = normalizeName(value)
                candidate.isNotBlank() && (candidate.contains(expected) || expected.contains(candidate))
            }
        }
    }

    fun parseGroups(body: String): List<ShenzhenTrainingPlanGroup>? {
        val root = parse(body) ?: return null
        return allObjects(root).mapNotNull { row ->
            val id = first(row, "kzid", "KZID")
            val name = first(row, "kzmc", "KZMC", "text", "TEXT", "name", "NAME")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            ShenzhenTrainingPlanGroup(
                id = id,
                parentId = first(row, "fkzid", "FKZID", "pid", "PID", "parentId"),
                name = name,
                type = first(row, "kzlxmc", "KZLXMC", "kzlx", "KZLX"),
                required = booleanValue(row, "kzsfbx", "KZSFBX", "sfbx", "SFBX"),
                minimumCredits = number(row, "yqxdxf", "YQXDXF"),
                minimumCourses = number(row, "yqxdms", "YQXDMS")?.toInt(),
                minimumHours = number(row, "yqxdxs", "YQXDXS")
            )
        }.distinctBy { it.id }.takeIf { it.isNotEmpty() } ?: emptyList()
    }

    fun parseCourses(body: String): List<ShenzhenTrainingPlanCourse>? {
        val root = parse(body) ?: return null
        return allObjects(root).mapNotNull { row ->
            val code = first(row, "kcdm", "KCDM")
            val name = first(row, "kcmc", "KCMC")
            if (code.isBlank() && name.isBlank()) return@mapNotNull null
            val groupId = first(row, "kzid", "KZID", "fakzid", "FAKZID")
            ShenzhenTrainingPlanCourse(
                id = first(row, "id", "ID", "row_id", "ROW_ID").ifBlank { "$groupId-$code-$name" },
                planId = first(row, "fah", "FAH"),
                groupId = groupId,
                groupName = first(row, "kzmc", "KZMC"),
                courseCode = code,
                courseName = name.ifBlank { code },
                courseNameEnglish = first(row, "kcmc_en", "KCMC_EN"),
                credits = number(row, "xf", "XF", "kcxf", "KCXF"),
                totalHours = number(row, "xszxs", "XSZXS", "kczxs", "KCZXS", "zxs", "ZXS"),
                theoryHours = number(row, "xsllxs", "XSLLXS"),
                labHours = number(row, "xssyxs", "XSSYXS"),
                practiceHours = number(row, "xssjxs", "XSSJXS", "sjxsxs", "SJXSXS"),
                computerHours = number(row, "xsshangjixs", "XSSHANGJIXS"),
                assessmentMethod = first(row, "khfsmc", "KHFSMC"),
                required = booleanValue(row, "sfbx", "SFBX"),
                courseNature = first(row, "kcxzmc", "KCXZMC"),
                courseCategory = first(row, "kclbmc", "KCLBMC"),
                recommendedTerm = first(row, "tjkkxnxq", "TJKKXNXQ"),
                offeringCollege = first(row, "kkyxmc", "KKYXMC"),
                teachingLanguage = first(row, "skyymc", "SKYYMC")
            )
        }.distinctBy { listOf(it.groupId, it.courseCode, it.courseName, it.recommendedTerm) }
            .takeIf { it.isNotEmpty() }
    }

    fun combine(
        plan: ShenzhenTrainingPlan,
        groups: List<ShenzhenTrainingPlanGroup>,
        courses: List<ShenzhenTrainingPlanCourse>
    ): ShenzhenTrainingPlanDetail {
        val categories = if (plan.level == ShenzhenTrainingPlanLevel.POSTGRADUATE && groups.isNotEmpty()) {
            val grouped = courses.groupBy { it.groupId }
            val ordered = groups.mapNotNull { group ->
                grouped[group.id]?.takeIf { it.isNotEmpty() }?.let {
                    ShenzhenTrainingPlanCategory(group.id, group.name, group, it)
                }
            }
            val knownIds = groups.mapTo(hashSetOf()) { it.id }
            val remaining = courses.filter { it.groupId !in knownIds }
            ordered + remaining.groupBy { it.groupName.ifBlank { "其他课程" } }.map { (name, values) ->
                ShenzhenTrainingPlanCategory("other-$name", name, null, values)
            }
        } else {
            courses.groupBy { it.courseCategory.ifBlank { it.courseNature.ifBlank { "其他课程" } } }
                .map { (name, values) -> ShenzhenTrainingPlanCategory(name, name, null, values) }
        }
        return ShenzhenTrainingPlanDetail(plan, groups, courses, categories)
    }

    fun parsePageCount(body: String): Int {
        val root = parse(body) ?: return 1
        return allObjects(root).mapNotNull { number(it, "pages", "PAGES")?.toInt() }
            .maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun parse(body: String): JsonElement? =
        runCatching { JsonParser().parse(body) }.getOrNull()

    private fun allObjects(root: JsonElement): List<JsonObject> = buildList {
        fun visit(element: JsonElement) {
            when {
                element.isJsonObject -> {
                    val value = element.asJsonObject
                    add(value)
                    value.entrySet().forEach { visit(it.value) }
                }
                element.isJsonArray -> element.asJsonArray.forEach(::visit)
            }
        }
        visit(root)
    }

    private fun first(value: JsonObject, vararg keys: String): String {
        keys.forEach { key ->
            val element = value.get(key) ?: return@forEach
            if (!element.isJsonPrimitive) return@forEach
            val text = runCatching { element.asString }.getOrNull()?.trim().orEmpty()
            if (text.isNotBlank() && !text.equals("null", true)) return text
        }
        return ""
    }

    private fun number(value: JsonObject, vararg keys: String): Double? =
        first(value, *keys).toDoubleOrNull()

    private fun booleanValue(value: JsonObject, vararg keys: String): Boolean? =
        when (first(value, *keys).lowercase()) {
            "1", "true", "yes", "是", "必修" -> true
            "0", "false", "no", "否", "选修" -> false
            else -> null
        }

    private fun normalizeName(value: String): String = value
        .replace(Regex("[\\s（）()·•]"), "")
        .replace("专业", "")
        .lowercase()
}
