package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCreditRequirement
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationCreditType
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationTrack
import cn.limpu.hita.data.model.eas.recognizedCrossMajorCourseCodes
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

    @Test
    fun `credit range rejects lighter plan below minimum`() {
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("LIGHT", "低于下限", "3", ""),
                course("IN_RANGE", "区间内", "4", "1-16周,星期一第1-2节")
            ),
            options = ShenzhenRecommendationOptions(
                targetAdditionalCredits = 5.0,
                minAdditionalCredits = 4.0,
                maxAdditionalCredits = 6.0
            )
        )

        assertEquals("IN_RANGE", result.plans.first().courses.single().courseCode)
        assertEquals(4.0, result.minAdditionalCredits, 0.001)
        assertEquals(6.0, result.maxAdditionalCredits, 0.001)
    }

    @Test
    fun `recommendation fills cross major and general education separately`() {
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("CROSS", "跨专业课", "2", "1-16周,星期一第3-4节", pool = "跨专业课程体系"),
                course("GENERAL", "通识课", "2", "1-16周,星期二第3-4节", pool = "文理通识"),
                course("OTHER", "其他课", "4", "1-16周,星期三第3-6节", pool = "限选课程池")
            ),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 4.0),
            requirements = listOf(
                requirement("跨专业发展课程", required = 10.0, completed = 8.0),
                requirement("文理通识", required = 8.0, completed = 6.0, nature = "任选")
            )
        )

        assertEquals(setOf("CROSS", "GENERAL"), result.plans.first().courses.map { it.courseCode }.toSet())
        assertTrue(result.plans.first().creditProgress.all { it.remainingCredits == 0.0 })
    }

    @Test
    fun `general education subrequirements outrank generic credits`() {
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("GENERIC", "普通通识", "5", "1-16周,星期一第3-6节", pool = "文理通识"),
                course(
                    "ART", "艺术课", "2", "1-16周,星期二第3-4节",
                    pool = "文理通识", category = "美育类"
                ),
                course(
                    "HISTORY", "四史课", "1", "1-16周,星期三第3-4节",
                    pool = "文理通识", category = "四史类"
                ),
                course(
                    "ENGLISH", "英文通识", "2", "1-16周,星期四第3-4节",
                    pool = "文理通识", language = "英文"
                )
            ),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 5.0),
            requirements = listOf(
                requirement("文理通识", required = 8.0, completed = 8.0, nature = "任选"),
                requirement("艺术鉴赏与创作—美育类", required = 2.0, completed = 0.0),
                requirement("社会与当代中国—四史类课", required = 0.5, completed = 0.0),
                requirement(
                    "文理通识", required = 2.0, completed = 0.0,
                    nature = "任选", language = "英文"
                )
            )
        )

        assertEquals(
            setOf("ART", "HISTORY", "ENGLISH"),
            result.plans.first().courses.map { it.courseCode }.toSet()
        )
        assertTrue(result.plans.first().creditProgress.all { it.remainingCredits == 0.0 })
    }

    @Test
    fun `mooc remains selectable but only two credits count toward general education`() {
        val requirements = listOf(
            requirement(
                "文理通识",
                required = 10.0,
                completed = 7.0,
                nature = "任选",
                creditedMooc = 2.0
            )
        )
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("MOOC", "在线课程", "2", "", pool = "MOOC"),
                course("GENERAL", "线下通识", "2", "1-16周,星期二第3-4节", pool = "文理通识")
            ),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 2.0),
            requirements = requirements
        )

        assertEquals("GENERAL", result.plans.first().courses.single().courseCode)
        assertEquals(2.0, result.plans.first().countedMoocCredits, 0.001)
        assertEquals(
            1.0,
            result.plans.first().creditProgress.single {
                it.type == ShenzhenRecommendationCreditType.GENERAL_EDUCATION
            }.remainingCredits,
            0.001
        )

        val moocOnly = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(course("MOOC", "在线课程", "2", "", pool = "MOOC")),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 2.0),
            requirements = requirements
        )
        assertEquals("MOOC", moocOnly.plans.first().courses.single().courseCode)
        assertEquals(2.0, moocOnly.plans.first().countedMoocCredits, 0.001)

        val partialCap = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("MOOC-A", "在线课程 A", "2", "", pool = "MOOC"),
                course("MOOC-B", "在线课程 B", "2", "", pool = "MOOC")
            ),
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 4.0),
            requirements = listOf(
                requirement(
                    "文理通识",
                    required = 10.0,
                    completed = 5.0,
                    nature = "任选",
                    creditedMooc = 1.0
                )
            )
        )
        assertEquals(2, partialCap.plans.first().courses.size)
        assertEquals(2.0, partialCap.plans.first().countedMoocCredits, 0.001)
        assertEquals(6.0, partialCap.plans.first().creditProgress.single().projectedCredits, 0.001)
    }

    @Test
    fun `mooc cap applies to general total but not its subrequirements`() {
        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("ART-MOOC", "美育慕课", "2", "", pool = "MOOC", category = "美育类"),
                course("HISTORY-MOOC", "四史慕课", "2", "", pool = "MOOC", category = "四史类"),
                course("GENERAL", "普通文理通识", "6", "", pool = "文理通识")
            ),
            options = ShenzhenRecommendationOptions(
                targetAdditionalCredits = 10.0,
                minAdditionalCredits = 10.0,
                maxAdditionalCredits = 10.0
            ),
            requirements = listOf(
                requirement("文理通识", required = 8.0, completed = 0.0, nature = "任选"),
                requirement("艺术鉴赏与创作—美育类", required = 2.0, completed = 0.0),
                requirement("社会与当代中国—四史类课", required = 0.5, completed = 0.0)
            )
        )

        val plan = result.plans.first()
        assertEquals(2.0, plan.countedMoocCredits, 0.001)
        assertTrue(plan.creditProgress.all { it.remainingCredits == 0.0 })
        assertEquals(
            8.0,
            plan.creditProgress.single {
                it.type == ShenzhenRecommendationCreditType.GENERAL_EDUCATION
            }.projectedCredits,
            0.001
        )
        assertEquals(
            2.0,
            plan.creditProgress.single {
                it.type == ShenzhenRecommendationCreditType.AESTHETIC_EDUCATION
            }.projectedCredits,
            0.001
        )
        assertEquals(
            2.0,
            plan.creditProgress.single {
                it.type == ShenzhenRecommendationCreditType.FOUR_HISTORIES
            }.projectedCredits,
            0.001
        )
    }

    @Test
    fun `other track compulsory courses exclude courses shared with planned track`() {
        val tracks = listOf(
            ShenzhenRecommendationTrack("own", "本轨道", setOf("SHARED", "OWN")),
            ShenzhenRecommendationTrack(
                "other",
                "其他轨道",
                setOf("shared", "OTHER", "OTHER-ELECTIVE"),
                compulsoryCourseCodes = setOf("shared", "OTHER")
            )
        )
        val recognized = recognizedCrossMajorCourseCodes(tracks, "own")
        assertEquals(setOf("OTHER"), recognized)

        val result = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = listOf(
                course("SHARED", "公共必修", "2", ""),
                course("OTHER", "其他轨道必修", "2", "")
            ),
            options = ShenzhenRecommendationOptions(
                targetAdditionalCredits = 2.0,
                recognizedCrossMajorCourseCodes = recognized
            ),
            requirements = listOf(
                requirement("跨专业发展课程", required = 2.0, completed = 0.0)
            )
        )

        assertEquals("OTHER", result.plans.first().courses.single().courseCode)
        assertEquals(0.0, result.plans.first().creditProgress.single().remainingCredits, 0.001)
    }

    @Test
    fun `practice innovation courses are excluded by default and considered when enabled`() {
        val candidates = listOf(
            course("INNOVATION", "创新研修课", "2", "", pool = "创新研修"),
            course("PRACTICE", "社会实践课", "1", "", pool = "社会实践课"),
            course("GENERIC", "普通课程", "3", "1-16周,星期一第3-4节", pool = "限选课程池")
        )
        val requirements = listOf(
            requirement(
                "创新创业,创新创业实践,创新创业通选课,创新实验,创新研修,创业指导课," +
                    "竞赛指导类,社会实践",
                required = 6.0,
                completed = 3.0
            ),
            requirement(
                "创新创业,创新创业实践,创新创业通选课,创新实验,创新研修,创业指导课," +
                    "竞赛指导类",
                required = 4.0,
                completed = 2.0
            ),
            requirement("社会实践", required = 1.0, completed = 0.0)
        )

        val disabled = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = candidates,
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 3.0),
            requirements = requirements
        )
        assertEquals("GENERIC", disabled.plans.first().courses.single().courseCode)
        assertEquals(2, disabled.excludedPracticeInnovationCount)
        assertTrue(disabled.currentCreditProgress.none {
            it.type == ShenzhenRecommendationCreditType.INNOVATION ||
                it.type == ShenzhenRecommendationCreditType.SOCIAL_PRACTICE
        })

        val enabled = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = candidates,
            options = ShenzhenRecommendationOptions(
                targetAdditionalCredits = 3.0,
                includePracticeInnovationCourses = true
            ),
            requirements = requirements
        )
        assertEquals(
            setOf("INNOVATION", "PRACTICE"),
            enabled.plans.first().courses.map { it.courseCode }.toSet()
        )
        assertEquals(0, enabled.excludedPracticeInnovationCount)
        assertTrue(enabled.plans.first().creditProgress.all { it.remainingCredits == 0.0 })
    }

    @Test
    fun `minor plan courses are excluded by default and considered when enabled`() {
        val candidates = listOf(
            course(
                "MINOR", "辅修专业课", "2", "",
                pool = "跨专业课程体系", selectionRequirement = "仅限辅修培养方案学生"
            ),
            course("NORMAL", "普通课程", "2", "", pool = "普通课程池")
        )
        val requirements = listOf(
            requirement("跨专业发展课程", required = 2.0, completed = 0.0)
        )

        val disabled = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = candidates,
            options = ShenzhenRecommendationOptions(targetAdditionalCredits = 2.0),
            requirements = requirements
        )
        assertEquals("NORMAL", disabled.plans.first().courses.single().courseCode)
        assertEquals(1, disabled.excludedMinorPlanCount)

        val enabled = ShenzhenCourseRecommendationEngine.recommend(
            selected = emptyList(),
            candidates = candidates,
            options = ShenzhenRecommendationOptions(
                targetAdditionalCredits = 2.0,
                includeMinorPlanCourses = true
            ),
            requirements = requirements
        )
        assertEquals("MINOR", enabled.plans.first().courses.single().courseCode)
        assertEquals(0, enabled.excludedMinorPlanCount)
        assertEquals(0.0, enabled.plans.first().creditProgress.single().remainingCredits, 0.001)
    }

    private fun course(
        code: String,
        name: String,
        credits: String,
        schedule: String,
        capacity: Int? = null,
        selected: Int? = null,
        conflict: Boolean = false,
        pool: String = "",
        category: String = "",
        language: String = "",
        selectionRequirement: String = ""
    ) = ShenzhenCourseCatalogItem(
        id = code,
        taskId = code,
        courseCode = code,
        courseName = name,
        credits = credits,
        schedule = schedule,
        courseCategory = category,
        teachingLanguage = language,
        selectionRequirement = selectionRequirement,
        selectionPoolName = pool,
        capacity = capacity,
        selectedCount = selected,
        hasConflict = conflict,
        source = ShenzhenCourseCatalogSource.AVAILABLE
    )

    private fun requirement(
        name: String,
        required: Double,
        completed: Double,
        nature: String = "",
        language: String = "",
        creditedMooc: Double = 0.0
    ) = ShenzhenCreditRequirement(
        id = "$name|$nature|$language",
        name = name,
        teachingLanguage = language,
        courseNature = nature,
        requiredCredits = required,
        completedCredits = completed,
        remainingCredits = (required - completed).coerceAtLeast(0.0),
        includesMooc = creditedMooc > 0.0,
        creditedMoocCredits = creditedMooc,
        earnedMoocCredits = creditedMooc,
        passed = completed >= required
    )
}
