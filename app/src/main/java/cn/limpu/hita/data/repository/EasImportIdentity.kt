package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.utils.CourseNameUtils

internal object EasImportIdentity {
    fun subjectLookupKeys(code: String?, normalizedName: String?, rawName: String?): Set<String> {
        return buildSet {
            code?.trim()?.takeIf { it.isNotEmpty() }?.let { add("code:$it") }
            normalizedName?.trim()?.takeIf { it.isNotEmpty() }?.let { add("name:$it") }
            rawName?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                add("name:$raw")
                CourseNameUtils.normalize(raw)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add("name:$it")
                }
            }
        }
    }

    /**
     * 两门课是否是同一门。
     *
     * 两边都有课程代码时只比代码：规范化课名会丢掉（A）/（B）和教学班后缀，
     * 不能把不同代码的平行课粘成一门。缺代码时才退回课名，方便旧数据补上代码。
     */
    fun subjectsMatch(
        leftCode: String?,
        leftName: String?,
        rightCode: String?,
        rightName: String?,
    ): Boolean {
        val left = leftCode?.trim().orEmpty()
        val right = rightCode?.trim().orEmpty()
        if (left.isNotEmpty() && right.isNotEmpty()) return left == right
        val leftNames = nameKeys(leftName)
        val rightNames = nameKeys(rightName)
        return leftNames.any { it in rightNames }
    }

    fun findReusableSubject(
        subjectsByKey: Map<String, cn.limpu.hita.data.model.timetable.TermSubject>,
        code: String?,
        normalizedName: String?,
        rawName: String?,
    ): cn.limpu.hita.data.model.timetable.TermSubject? {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            subjectsByKey["code:$trimmed"]?.let { return it }
        }
        val names = subjectLookupKeys(null, normalizedName, rawName).filter { it.startsWith("name:") }
        return names.firstNotNullOfOrNull { key ->
            val candidate = subjectsByKey[key] ?: return@firstNotNullOfOrNull null
            val candidateCode = candidate.code?.trim().orEmpty()
            if (trimmed.isNotEmpty() && candidateCode.isNotEmpty() && trimmed != candidateCode) {
                null
            } else {
                candidate
            }
        }
    }

    fun registerSubject(
        subjectsByKey: MutableMap<String, cn.limpu.hita.data.model.timetable.TermSubject>,
        subject: cn.limpu.hita.data.model.timetable.TermSubject,
        normalizedName: String?,
        rawName: String?,
    ) {
        subjectLookupKeys(subject.code, normalizedName, rawName).forEach { key ->
            val existing = subjectsByKey[key]
            if (existing == null || existing.id == subject.id || key.startsWith("code:")) {
                subjectsByKey[key] = subject
                return@forEach
            }
            val existingCode = existing.code?.trim().orEmpty()
            val subjectCode = subject.code?.trim().orEmpty()
            if (existingCode.isNotEmpty() && subjectCode.isNotEmpty() && existingCode != subjectCode) {
                return@forEach
            }
            subjectsByKey[key] = subject
        }
    }

    private fun nameKeys(name: String?): Set<String> {
        val raw = name?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()
        return subjectLookupKeys(null, CourseNameUtils.normalize(raw) ?: raw, raw)
            .filterTo(linkedSetOf()) { it.startsWith("name:") }
    }

    fun classEventIdentityKey(event: EventItem): String {
        return listOf(
            event.timetableId,
            event.type.name,
            event.name.trim(),
            event.place.orEmpty().trim(),
            event.teacher.orEmpty().trim(),
            event.from.time.toString(),
            event.to.time.toString(),
            event.fromNumber.toString(),
            event.lastNumber.toString()
        ).joinToString("|")
    }
}
