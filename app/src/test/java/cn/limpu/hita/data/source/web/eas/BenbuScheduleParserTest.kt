package cn.limpu.hita.data.source.web.eas

import org.junit.Assert.assertEquals
import org.junit.Test

class BenbuScheduleParserTest {
    @Test
    fun parseScheduleHtml_fillsOmittedTeacherAndClassroomWithinSameCourse() {
        val html = """
            <table class="addlist_01">
                <tr>
                    <th></th><th></th><th>星期一</th>
                </tr>
                <tr>
                    <td></td><td>第1-2节</td>
                    <td>信号处理导论<br/>李杨[14]周，[9-13]周正心12</td>
                </tr>
            </table>
        """.trimIndent()

        val courses = BenbuScheduleParser.parseScheduleHtml(html)

        assertEquals(2, courses.size)
        assertEquals(listOf(14), courses[0].weeks)
        assertEquals("李杨", courses[0].teacher)
        assertEquals("正心12", courses[0].classroom)
        assertEquals(listOf(9, 10, 11, 12, 13), courses[1].weeks)
        assertEquals("李杨", courses[1].teacher)
        assertEquals("正心12", courses[1].classroom)
    }

    @Test
    fun parseScheduleHtml_readsClassroomFromSeparateLineAfterWeekRanges() {
        val html = """
            <table class="addlist_01">
                <tr>
                    <th></th><th></th><th>星期一</th>
                </tr>
                <tr>
                    <td>下午</td><td>第5,6节<br/>13:45~15:30</td>
                    <td>智能信号处理导论<br/>李杨[14]周，[9-13]周<br/>正心23</td>
                </tr>
            </table>
        """.trimIndent()

        val courses = BenbuScheduleParser.parseScheduleHtml(html)

        assertEquals(2, courses.size)
        assertEquals(listOf(14), courses[0].weeks)
        assertEquals("李杨", courses[0].teacher)
        assertEquals("正心23", courses[0].classroom)
        assertEquals(5, courses[0].begin)
        assertEquals(2, courses[0].last)
        assertEquals(listOf(9, 10, 11, 12, 13), courses[1].weeks)
        assertEquals("李杨", courses[1].teacher)
        assertEquals("正心23", courses[1].classroom)
        assertEquals(5, courses[1].begin)
        assertEquals(2, courses[1].last)
    }
}
