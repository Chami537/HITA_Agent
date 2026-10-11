package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.DisplayScope
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.TimetableGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Timestamp

class DisplayScopeLogicTest {

    private fun timetable(id: String, start: Long, end: Long, groupId: String? = null): Timetable =
        Timetable().apply {
            this.id = id
            startTime = Timestamp(start)
            endTime = Timestamp(end)
            this.groupId = groupId
        }

    private val dayMs = 24L * 3600 * 1000

    @Test
    fun `default scope picks earliest-starting in-term timetable`() {
        val now = 1000L * dayMs
        val a = timetable("a", 900 * dayMs, 1100 * dayMs)   // 在学期内,开始更早
        val b = timetable("b", 950 * dayMs, 1200 * dayMs)   // 在学期内,开始更晚
        val c = timetable("c", 100 * dayMs, 200 * dayMs)    // 已过期
        val scope = defaultDisplayScope(listOf(a, b, c), now)
        assertEquals(DisplayScope.Single("a"), scope)
    }

    @Test
    fun `default scope falls back to latest term when none in term`() {
        val now = 1000L * dayMs
        val a = timetable("a", 100 * dayMs, 200 * dayMs)
        val b = timetable("b", 300 * dayMs, 400 * dayMs)
        assertEquals(DisplayScope.Single("b"), defaultDisplayScope(listOf(a, b), now))
    }

    @Test
    fun `default scope is null without timetables`() {
        assertNull(defaultDisplayScope(emptyList(), 0L))
    }

    @Test
    fun `default scope returns the whole group of the chosen term`() {
        val group = TimetableGroupEntity().apply { id = "g" }
        val member = timetable("member", 0, 100, groupId = "g")
        assertEquals(DisplayScope.Group("g"), defaultDisplayScope(listOf(member), 50, listOf(group)))
    }

    @Test
    fun `missing group does not make its member inaccessible`() {
        val member = timetable("member", 0, 100, groupId = "gone")
        assertEquals(DisplayScope.Single("member"), defaultDisplayScope(listOf(member), 50, emptyList()))
    }

    @Test
    fun `persisted single scope validity follows timetable existence`() {
        val tt = timetable("a", 0, 100)
        assertTrue(displayScopeValid(DisplayScope.Single("a"), listOf(tt), emptyList()))
        assertFalse(displayScopeValid(DisplayScope.Single("gone"), listOf(tt), emptyList()))
    }

    @Test
    fun `persisted group scope requires group and remaining members`() {
        val group = TimetableGroupEntity().apply { id = "g1"; name = "组合课表 1" }
        val member = timetable("m", 0, 100, groupId = "g1")
        assertTrue(displayScopeValid(DisplayScope.Group("g1"), listOf(member), listOf(group)))
        // 组存在但无成员(最后一份被移出/删除) → 失效回落
        assertFalse(displayScopeValid(DisplayScope.Group("g1"), emptyList(), listOf(group)))
        // 组不存在 → 失效
        assertFalse(displayScopeValid(DisplayScope.Group("g1"), listOf(member), emptyList()))
    }
}
