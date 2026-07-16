package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenCourseRecommendationEngineTest {
    @Test
    fun `schedule parser expands weeks weekday and periods`() {
        val meetings = ShenzhenCourseRecommendationEngine.parseMeetings(
            "1-3,5周,星期二第1-2节 T3401 8周,星期五第7-8节 H308"
        )

        assertEquals(2, meetings.size)
        assertEquals(2, meetings[0].weekday)
        assertEquals(setOf(1, 2, 3, 5), meetings[0].weeks)
        assertEquals(1, meetings[0].startPeriod)
        assertEquals(2, meetings[0].endPeriod)
    }

    @Test
    fun `recommendation excludes full direct conflict and selected course`() {
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = listOf(course("SELECTED", "已选", "2", "1-16周,星期一第3-4节")),
            candidates = listOf(
                course("SELECTED", "已选另一班", "2", "1-16周,星期二第3-4节"),
                course("FULL", "满员课", "3", "1-16周,星期二第3-4节", capacity = 30, selected = 30),
                course("CONFLICT", "冲突课", "3", "1-16周,星期三第3-4节", conflict = true),
                course("GOOD", "可选课", "3", "1-16周,星期四第3-4节", capacity = 30, selected = 20)
            ),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 3.0)
        )

        assertEquals(1, result.excludedFullCount)
        assertEquals(1, result.excludedConflictCount)
        assertEquals(listOf("GOOD"), result.plans.first().courses.map { it.courseCode })
    }

    @Test
    fun `free day and fewer early preferences choose different sections`() {
        val selected = listOf(course("BASE", "基础课", "2", "1-16周,星期一第3-4节"))
        val candidates = listOf(
            course("EARLY", "同日早八", "3", "1-16周,星期一第1-2节"),
            course("LATER", "另日非早八", "3", "1-16周,星期二第3-4节")
        )

        val freeDay = ShenzhenCourseRecommendationEngine.recommend(
            selected,
            candidates,
            ShenzhenRecommendationOptions(
                targetAdditionalCredits = 3.0,
                preference = ShenzhenRecommendationPreference.FREE_DAY
            )
        )
        val fewerEarly = ShenzhenCourseRecommendationEngine.recommend(
            selected,
            candidates,
            ShenzhenRecommendationOptions(
                targetAdditionalCredits = 3.0,
                preference = ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES
            )
        )

        assertEquals("EARLY", freeDay.plans.first().courses.single().courseCode)
        assertEquals("LATER", fewerEarly.plans.first().courses.single().courseCode)
        assertTrue(freeDay.plans.first().activeWeekdays < fewerEarly.plans.first().activeWeekdays)
    }

    private fun course(
        code: String,
        name: String,
        credits: String,
        schedule: String,
        capacity: Int? = null,
        selected: Int? = null,
        conflict: Boolean = false
    ) = ShenzhenCourseCatalogItem(
        id = code,
        taskId = code,
        courseCode = code,
        courseName = name,
        credits = credits,
        schedule = schedule,
        capacity = capacity,
        selectedCount = selected,
        hasConflict = conflict,
        source = ShenzhenCourseCatalogSource.AVAILABLE
    )
}
