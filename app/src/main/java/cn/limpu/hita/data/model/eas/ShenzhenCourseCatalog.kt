package cn.limpu.hita.data.model.eas

enum class ShenzhenCourseCatalogSource {
    AVAILABLE,
    SCHOOL
}

data class ShenzhenCourseCatalogItem(
    val id: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String = "",
    val credits: String = "",
    val totalHours: String = "",
    val courseNature: String = "",
    val courseCategory: String = "",
    val offeringCollege: String = "",
    val campus: String = "",
    val schedule: String = "",
    val teachingLanguage: String = "",
    val trainingLevel: String = "",
    val capacity: Int? = null,
    val selectedCount: Int? = null,
    val selectionPoolName: String = "",
    val source: ShenzhenCourseCatalogSource
)

data class ShenzhenCourseCatalogPage(
    val items: List<ShenzhenCourseCatalogItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int
) {
    val hasNextPage: Boolean
        get() = items.isNotEmpty() && page * pageSize < total
}

data class ShenzhenSelectionPool(
    val code: String,
    val name: String
)
