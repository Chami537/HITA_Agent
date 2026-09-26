package cn.limpu.hita.data.work

import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.CourseScoreItem
import cn.limpu.hita.data.model.eas.TermItem
import com.limpu.component.data.DataState

internal object ScoreReminderPolicy {
    private val yearPattern = Regex("20\\d{2}")

    fun isTerminal(state: DataState.STATE): Boolean =
        state != DataState.STATE.NOTHING && state != DataState.STATE.LOADING

    fun ownerKey(token: EASToken): String? {
        val identity = token.stuId?.trim()?.takeIf(String::isNotEmpty)
            ?: token.id?.trim()?.takeIf(String::isNotEmpty)
            ?: token.username?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        return "${token.campus.name}:$identity"
    }

    fun newKeys(known: Set<String>?, current: Set<String>): Set<String> =
        known?.let { current - it } ?: emptySet()

    fun termsToCheck(terms: List<TermItem>): List<TermItem> {
        val ordered = terms.distinctBy(TermItem::id).sortedWith(
            compareByDescending<TermItem> {
                yearPattern.find(it.yearCode)?.value?.toIntOrNull() ?: Int.MIN_VALUE
            }.thenByDescending { it.termCode.toIntOrNull() ?: Int.MIN_VALUE }
        )
        val currentIndex = ordered.indexOfFirst(TermItem::isCurrent).takeIf { it >= 0 } ?: 0
        return ordered.drop(currentIndex).take(2)
    }

    fun baselineKey(ownerKey: String, termId: String): String = "$ownerKey|$termId"

    fun scoreKey(item: CourseScoreItem): String {
        val grade = item.finalScoresText?.trim()?.takeIf(String::isNotEmpty)
            ?: item.finalScores.toString()
        return listOf(item.courseCode?.trim().orEmpty(), item.courseName?.trim().orEmpty(), grade)
            .joinToString("|")
    }
}
