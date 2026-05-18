package com.limpu.hitax.data.model.resource

sealed class UnifiedResourceItem {
    abstract val displayName: String
    abstract val subtitle: String
    abstract val sourceTag: String
    abstract val sourceColor: Int // ColorRes

    data class HoaCourse(
        val repoName: String,
        val courseName: String,
        val courseCode: String,
        val repoType: String,
        val teachers: List<String>,
    ) : UnifiedResourceItem() {
        override val displayName: String get() = courseName
        override val subtitle: String
            get() = listOfNotNull(
                courseCode.takeIf { it.isNotBlank() },
                teachers.take(3).joinToString(" / ").takeIf { it.isNotBlank() }
            ).filter { it.isNotBlank() }.joinToString("  ·  ")
        override val sourceTag: String get() = "HOA"
        override val sourceColor: Int get() = com.limpu.hitax.R.color.primary
    }

    data class ExternalCourse(
        val courseName: String,
        val category: String,
        val source: ResourceSource,
        val path: String,
    ) : UnifiedResourceItem() {
        override val displayName: String get() = courseName
        override val subtitle: String get() = category
        override val sourceTag: String
            get() = when (source) {
                ResourceSource.HITCS -> "HITCS"
                ResourceSource.FIREWORKS -> "薪火"
            }
        override val sourceColor: Int
            get() = when (source) {
                ResourceSource.HITCS -> com.limpu.hitax.R.color.subject3
                ResourceSource.FIREWORKS -> com.limpu.hitax.R.color.subject4
            }
    }
}
