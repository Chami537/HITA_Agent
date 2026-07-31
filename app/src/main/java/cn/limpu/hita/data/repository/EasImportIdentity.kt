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
