package cn.limpu.hita.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableRefreshMergePolicyTest {

    @Test
    fun plan_adoptsTimeAndPlaceChangesWhenLessonCountUnchanged() {
        val local = listOf(
            course("高等数学（A）", lessons = 2, place = "正心楼 101", from = 1000)
        )
        val incoming = listOf(
            course("高等数学（A）", lessons = 2, place = "诚意楼 202", from = 5000)
        )

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertFalse(plan.holdBatch)
        assertEquals(1, plan.adopt.size)
        assertEquals(1, plan.updated.size)
        assertTrue(plan.updated.single().timeAdjusted)
        assertTrue(plan.updated.single().placeAdjusted)
        assertTrue(plan.kept.isEmpty())
        assertTrue(plan.decisions.isEmpty())
        assertTrue(plan.vetoes.isEmpty())
    }

    @Test
    fun plan_adoptsAddedLessonsAndNewCourses() {
        val local = listOf(course("线性代数", lessons = 2, from = 1000))
        val incoming = listOf(
            course("线性代数", lessons = 3, from = 1000),
            course("大学物理", lessons = 2, from = 2000)
        )

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertEquals(2, plan.adopt.size)
        assertEquals(listOf("大学物理"), plan.added.map { it.name })
        assertEquals(1, plan.updated.single().lessonCountDelta)
        assertTrue(plan.vetoes.isEmpty())
    }

    @Test
    fun plan_keepsCourseMissingFromSource() {
        val local = listOf(
            course("高等数学（A）", lessons = 2, from = 1000),
            course("计算机导论", lessons = 2, from = 2000)
        )
        val incoming = listOf(course("高等数学（A）", lessons = 2, from = 1000))

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertEquals(1, plan.adopt.size)
        assertEquals(listOf("计算机导论"), plan.kept.map { it.name })
        assertTrue(plan.decisions.isEmpty())
        assertEquals(1, plan.vetoes.size)
        assertEquals(1, plan.vetoes.values.single().observationCount)
    }

    @Test
    fun plan_keepsWholeCourseWhenLessonCountReduced() {
        val local = listOf(course("信号与系统", lessons = 4, from = 1000))
        val incoming = listOf(course("信号与系统", lessons = 2, from = 1000))

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertTrue(plan.adopt.isEmpty())
        assertEquals(listOf("信号与系统"), plan.kept.map { it.name })
        assertEquals(1, plan.vetoes.size)
    }

    @Test
    fun plan_matchesRenamedCourseByCode() {
        val local = listOf(course("高等数学", code = "MATH1001", lessons = 2, from = 1000))
        val incoming = listOf(course("高等数学（A）", code = "MATH1001", lessons = 2, from = 1000))

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertEquals(1, plan.adopt.size)
        assertTrue(plan.kept.isEmpty())
        assertEquals("高等数学（A）", plan.updated.single().name)
    }

    @Test
    fun plan_holdsBatchWhenOverlapTooLow() {
        val local = (1..4).map { course("本地课程$it", lessons = 2, from = it * 1000L) }
        val incoming = (1..4).map { course("源端课程$it", lessons = 2, from = it * 1000L) }

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        assertTrue(plan.holdBatch)
        assertTrue(plan.adopt.isEmpty())
        assertEquals(4, plan.holdLocalCount)
        assertEquals(4, plan.holdIncomingCount)
        assertEquals(0, plan.holdMatchedCount)
    }

    @Test
    fun plan_adoptsAllWhenLocalIsEmpty() {
        val incoming = (1..5).map { course("课程$it", lessons = 2, from = it * 1000L) }

        val plan = TimetableRefreshMergePolicy.plan(emptyList(), incoming, emptyMap(), NOW)

        assertFalse(plan.holdBatch)
        assertEquals(5, plan.adopt.size)
    }

    @Test
    fun plan_upgradesVetoToDecisionAfterThreeObservationsSpanning48Hours() {
        val local = listOf(
            course("计算机导论", lessons = 2, from = 1000),
            course("高等数学（A）", lessons = 2, from = 2000),
            course("大学物理", lessons = 2, from = 3000)
        )
        val incoming = listOf(
            course("高等数学（A）", lessons = 2, from = 2000),
            course("大学物理", lessons = 2, from = 3000)
        )
        var vetoes = emptyMap<String, CourseVetoRecord>()

        // 第一次：仅记录，静默保留
        var plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW)
        vetoes = plan.vetoes
        assertTrue(plan.decisions.isEmpty())
        assertEquals(listOf("计算机导论"), plan.kept.map { it.name })

        // 第二次：一小时后，仍静默保留
        plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW + HOUR)
        vetoes = plan.vetoes
        assertTrue(plan.decisions.isEmpty())

        // 第三次：满 48 小时且累计 3 次观察 → 升级为待用户确认
        plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW + TWO_DAYS)

        assertEquals(1, plan.decisions.size)
        val decision = plan.decisions.single()
        assertEquals("计算机导论", decision.name)
        assertEquals(CourseVetoReason.MISSING_FROM_SOURCE, decision.reason)
        assertNull(decision.incoming)
        assertEquals(3, decision.observationCount)
    }

    @Test
    fun plan_upgradesReducedLessonCountToDecisionAfterWindow() {
        val local = listOf(course("信号与系统", lessons = 4, from = 1000))
        val incoming = listOf(course("信号与系统", lessons = 2, from = 1000))
        var vetoes = emptyMap<String, CourseVetoRecord>()
        var plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW)
        vetoes = plan.vetoes
        plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW + HOUR)
        vetoes = plan.vetoes
        plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW + TWO_DAYS)

        val decision = plan.decisions.single()
        assertEquals(CourseVetoReason.LESSON_COUNT_REDUCED, decision.reason)
        assertEquals(4, decision.localLessonCount)
        assertEquals(2, decision.incoming?.lessonCount)
    }

    @Test
    fun plan_clearsVetoWhenCourseReappears() {
        val local = listOf(
            course("计算机导论", lessons = 2, from = 1000),
            course("高等数学（A）", lessons = 2, from = 2000)
        )
        val partial = listOf(course("高等数学（A）", lessons = 2, from = 2000))
        var vetoes = TimetableRefreshMergePolicy
            .plan(local, partial, emptyMap(), NOW)
            .vetoes
        assertTrue(vetoes.isNotEmpty())

        val plan = TimetableRefreshMergePolicy.plan(local, local, vetoes, NOW + HOUR)

        assertTrue(plan.vetoes.isEmpty())
        assertTrue(plan.kept.isEmpty())
        assertEquals(2, plan.adopt.size)
    }

    @Test
    fun plan_doesNotHoldBatchWhenMissingCoursesAreAlreadyTracked() {
        val local = listOf(
            course("计算机导论", lessons = 2, from = 1000),
            course("大学物理", lessons = 2, from = 2000)
        )
        val incoming = listOf(course("全新课程", lessons = 2, from = 3000))

        // 首次：出现未追踪的新缺失 → 整批挂起等用户决策
        val first = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)
        assertTrue(first.holdBatch)
        assertEquals(2, first.holdUnmatchedLocal.size)

        // 用户选择"保留当前课表"后缺失被追踪，同一模式再次出现不再挂起
        val vetoes = first.holdUnmatchedLocal.associate { course ->
            TimetableRefreshMergePolicy.courseKey(course.name, course.code) to
                CourseVetoRecord(
                    courseKey = TimetableRefreshMergePolicy.courseKey(course.name, course.code),
                    name = course.name,
                    firstSeenMillis = NOW,
                    lastSeenMillis = NOW,
                    observationCount = 1
                )
        }
        val plan = TimetableRefreshMergePolicy.plan(local, incoming, vetoes, NOW + HOUR)

        assertFalse(plan.holdBatch)
        assertEquals(2, plan.kept.size)
        assertEquals(listOf("全新课程"), plan.added.map { it.name })
    }

    private fun course(
        name: String,
        code: String? = null,
        lessons: Int,
        from: Long,
        place: String = "正心楼 101"
    ) = MergeCourse(
        subjectId = "subject-$name",
        name = name,
        code = code,
        lessons = (0 until lessons).map { index ->
            MergeLesson(
                name = name,
                place = place,
                teacher = "张三",
                fromMillis = from + index * 1000,
                toMillis = from + index * 1000 + 500,
                fromNumber = index + 1,
                lastNumber = 2
            )
        }
    )

    private companion object {
        const val NOW = 1_000_000_000_000L
        const val HOUR = 60 * 60 * 1000L
        const val TWO_DAYS = 48 * 60 * 60 * 1000L
    }
}
