package com.limpu.hitax.ui.credit

import com.limpu.hitax.data.model.timetable.TermSubject

data class CreditStatsState(
    val totalCredits: Float = 0f,
    val totalSubjects: Int = 0,
    val spaCredits: Float = 0f,
    val nonSpaCredits: Float = 0f,
    val categories: List<CreditCategorySummary> = emptyList(),
    val isEmpty: Boolean = true
)

data class CreditCategorySummary(
    val type: TermSubject.TYPE,
    val totalCredits: Float,
    val goalCredits: Float?,
    val subjectCount: Int,
    val subjects: List<SubjectCreditItem>,
    val fieldBreakdown: List<FieldCreditSummary>,
    val expanded: Boolean = false
)

data class SubjectCreditItem(
    val name: String,
    val credit: Float,
    val field: String?,
    val countInSpa: Boolean
)

data class FieldCreditSummary(
    val fieldName: String,
    val totalCredits: Float
)
