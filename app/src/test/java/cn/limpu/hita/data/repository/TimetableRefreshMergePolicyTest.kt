package cn.limpu.hita.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimetableRefreshMergePolicyTest {

    @Test
    fun plan_adoptsTimeAndPlaceChangesWhenLessonCountUnchanged() {
        val local = listOf(
            course("高等数学（A）", lessons = 2, from = mondayAt(8, 0), place = "正心楼 101")
        )
        val incoming = listOf(
            course("高等数学（A）", lessons = 2, from = mondayAt(10, 5), place = "诚意楼 202")
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
    fun plan_reportsNothingWhenSourceMatchesLocal() {
        val local = listOf(
            course("高等数学（A）", lessons = 2, from = mondayAt(8, 0)),
            course("计算机导论", lessons = 2, from = mondayAt(14, 0))
        )

        val plan = TimetableRefreshMergePolicy.plan(local, local, emptyMap(), NOW)

        assertTrue(plan.updated.isEmpty())
        assertTrue(plan.added.isEmpty())
        assertTrue(plan.kept.isEmpty())
        assertTrue(plan.decisions.isEmpty())
        assertFalse(plan.hasChanges)
    }

    @Test
    fun plan_describesSlotLevelFieldChanges() {
        val local = listOf(
            MergeCourse(
                subjectId = "s1",
                name = "高等数学（A）",
                code = null,
                lessons = listOf(
                    lesson(dow = 1, fromNumber = 1, lastNumber = 2, clock = 8 * 60, week = 1, place = "正心楼 101"),
                    lesson(dow = 1, fromNumber = 1, lastNumber = 2, clock = 8 * 60, week = 2, place = "正心楼 101"),
                    lesson(dow = 3, fromNumber = 6, lastNumber = 7, clock = 14 * 60, week = 1)
                )
            )
        )
        val incoming = listOf(
            MergeCourse(
                subjectId = "s1",
                name = "高等数学（A）",
                code = null,
                lessons = listOf(
                    // 同一槽位：上课时间与地点都变了
                    lesson(dow = 1, fromNumber = 1, lastNumber = 2, clock = 10 * 60 + 5, week = 1, place = "诚意楼 202", teacher = "李四"),
                    lesson(dow = 1, fromNumber = 1, lastNumber = 2, clock = 10 * 60 + 5, week = 2, place = "诚意楼 202", teacher = "李四"),
                    // 原周三的课调到周五：减少一个槽位、新增一个槽位
                    lesson(dow = 5, fromNumber = 6, lastNumber = 7, clock = 14 * 60, week = 1)
                )
            )
        )

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        val change = plan.updated.single()
        val monday = change.slotChanges.single { it.dow == 1 && it.kind == SlotChangeKind.ADJUSTED }
        assertEquals("10:05-10:50", monday.clock)
        assertEquals("08:00-08:45", monday.clockBefore)
        assertEquals("诚意楼 202", monday.place)
        assertEquals("正心楼 101", monday.placeBefore)
        assertEquals("李四", monday.teacher)
        assertEquals("张三", monday.teacherBefore)
        assertTrue(change.timeAdjusted)
        assertTrue(change.placeAdjusted)
        assertTrue(change.teacherAdjusted)

        val removed = change.slotChanges.single { it.kind == SlotChangeKind.REMOVED }
        assertEquals(3, removed.dow)
        assertEquals(6, removed.fromNumber)
        val added = change.slotChanges.single { it.kind == SlotChangeKind.ADDED }
        assertEquals(5, added.dow)
        assertEquals("14:00-14:45", added.clock)
    }

    @Test
    fun plan_reportsRenameAsChange() {
        val local = listOf(course("高等数学", code = "MATH1001", lessons = 2, from = mondayAt(8, 0)))
        val incoming = listOf(course("高等数学（A）", code = "MATH1001", lessons = 2, from = mondayAt(8, 0)))

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        val change = plan.updated.single()
        assertEquals("高等数学（A）", change.name)
        assertEquals("高等数学", change.previousName)
        assertTrue(change.slotChanges.isEmpty())
    }

    @Test
    fun plan_keptCoursesCarryReasonAndCounts() {
        val local = listOf(
            course("计算机导论", lessons = 2, from = mondayAt(8, 0)),
            course("信号与系统", lessons = 4, from = mondayAt(14, 0))
        )
        val incoming = listOf(
            course("信号与系统", lessons = 2, from = mondayAt(14, 0))
        )

        val plan = TimetableRefreshMergePolicy.plan(local, incoming, emptyMap(), NOW)

        val missing = plan.kept.single { it.name == "计算机导论" }
        assertEquals(CourseVetoReason.MISSING_FROM_SOURCE, missing.reason)
        assertEquals(2, missing.localLessonCount)
        assertEquals(0, missing.incomingLessonCount)
        val reduced = plan.kept.single { it.name == "信号与系统" }
        assertEquals(CourseVetoReason.LESSON_COUNT_REDUCED, reduced.reason)
        assertEquals(4, reduced.localLessonCount)
        assertEquals(2, reduced.incomingLessonCount)
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
                fromMillis = from + index * WEEK_MILLIS,
                toMillis = from + index * WEEK_MILLIS + 45 * 60 * 1000,
                fromNumber = index + 1,
                lastNumber = 2,
                weekOfTerm = index + 1
            )
        }
    )

    /** 2026-09-07（周一）当天时刻。 */
    private fun mondayAt(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 7, hour, minute, 0)
        }
        check(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            "fixture date must be a Monday"
        }
        return calendar.timeInMillis
    }

    private fun lesson(
        dow: Int,
        fromNumber: Int,
        lastNumber: Int,
        clock: Int,
        week: Int,
        place: String = "正心楼 101",
        teacher: String = "张三"
    ) = MergeLesson(
        name = "高等数学（A）",
        place = place,
        teacher = teacher,
        fromMillis = mondayAt(clock / 60, clock % 60) +
            (dow - 1) * DAY_MILLIS + (week - 1) * WEEK_MILLIS,
        toMillis = mondayAt(clock / 60, clock % 60) + 45 * 60 * 1000 +
            (dow - 1) * DAY_MILLIS + (week - 1) * WEEK_MILLIS,
        fromNumber = fromNumber,
        lastNumber = lastNumber,
        weekOfTerm = week
    )

    private companion object {
        const val NOW = 1_000_000_000_000L
        const val HOUR = 60 * 60 * 1000L
        const val TWO_DAYS = 48 * 60 * 60 * 1000L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
}
