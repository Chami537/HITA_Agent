package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenGradeCourse
import cn.limpu.hita.data.model.eas.ShenzhenGradeAnalysisScope
import cn.limpu.hita.data.model.eas.ShenzhenGradeStatus
import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenGradeParserTest {
    private val term = TermItem("2025-2026", "2025-2026", "2", "春季").apply {
        name = "2026春季"
    }

    @Test
    fun `course list merges selected course with early score by task number`() {
        val courses = ShenzhenGradeParser.parseCourses(
            publishedBody = """{"content":{"list":[]}}""",
            selectedBody = """{"yxkcList":[{
                "rwid":"RW-1","rwh":"2025-2026-2-COMP1001-001",
                "kcdm":"COMP1001","kcmc":"程序设计"
            }]}""",
            earlyBody = """{"pageInfo":{"list":[{
                "rwh":"2025-2026-2-COMP1001-001","kcmc":"程序设计","zzzscj":"88.5"
            }]}}""",
            term = term
        ).orEmpty()

        assertEquals(1, courses.size)
        assertEquals(ShenzhenGradeStatus.EARLY, courses.single().status)
        assertEquals(88.5, courses.single().myScore ?: 0.0, 0.001)
        assertEquals("RW-1", courses.single().taskId)
    }

    @Test
    fun `published grade takes precedence over early result`() {
        val courses = ShenzhenGradeParser.parseCourses(
            publishedBody = """{"content":{"list":[{
                "id":"GRADE-1","rwid":"RW-1","rwh":"2025-2026-2-COMP1001-001",
                "kcmc":"程序设计","xscj":"91"
            }]}}""",
            selectedBody = """{"yxkcList":[]}""",
            earlyBody = """{"pageInfo":{"list":[{
                "rwh":"2025-2026-2-COMP1001-001","kcmc":"程序设计","zzzscj":"90"
            }]}}""",
            term = term
        ).orEmpty()

        assertEquals(ShenzhenGradeStatus.PUBLISHED, courses.single().status)
        assertEquals("GRADE-1", courses.single().recordId)
        assertEquals(91.0, courses.single().myScore ?: 0.0, 0.001)
    }

    @Test
    fun `course list keeps component record id required by new seeFx contract`() {
        val courses = ShenzhenGradeParser.parseCourses(
            publishedBody = """{"content":{"list":[{
                "id":"CJ-1","rwid":"RW-1","rwh":"2025-2026-2-COMP1001-001",
                "kcmc":"程序设计","xscj":"93"
            }]}}""",
            selectedBody = """{"yxkcList":[]}""",
            earlyBody = null,
            term = term
        ).orEmpty()

        assertEquals("CJ-1", courses.single().recordId)
    }

    @Test
    fun `early score falls back to normalized course name when task number differs`() {
        val courses = ShenzhenGradeParser.parseCourses(
            publishedBody = """{"content":{"list":[]}}""",
            selectedBody = """{"yxkcList":[{
                "rwid":"RW-1","rwh":"2025-2026-2-COMP1001-001",
                "kcmc":"计算机组成原理（实验）"
            }]}""",
            earlyBody = """{"pageInfo":{"list":[{
                "rwh":"legacy-task-number","kcmc":"计算机组成原理 (实验)",
                "xnxq":"2026春季","xnxqx":"2025-20262","zzzscj":"88.5"
            }]}}""",
            term = term
        ).orEmpty()

        assertEquals(1, courses.size)
        assertEquals(ShenzhenGradeStatus.EARLY, courses.single().status)
        assertEquals(88.5, courses.single().myScore ?: 0.0, 0.001)
        assertEquals(
            1 to 1,
            ShenzhenGradeParser.earlyScoreDiagnostics(
                """{"pageInfo":{"list":[{"zzzscj":"88.5"}]}}"""
            )
        )
    }

    @Test
    fun `early rows without numeric total do not mark a selected course as visible`() {
        val courses = ShenzhenGradeParser.parseCourses(
            publishedBody = """{"content":{"list":[]}}""",
            selectedBody = """{"yxkcList":[{
                "rwid":"RW-1","rwh":"2025-2026-2-COMP1001-001","kcmc":"课程"
            }]}""",
            earlyBody = """{"pageInfo":{"list":[{
                "rwh":"2025-2026-2-COMP1001-001","kcmc":"课程","zzzscj":""
            }]}}""",
            term = term
        ).orEmpty()

        assertEquals(ShenzhenGradeStatus.SELECTED, courses.single().status)
        assertEquals(1 to 0, ShenzhenGradeParser.earlyScoreDiagnostics(
            """{"pageInfo":{"list":[{"zzzscj":""}]}}"""
        ))
    }

    @Test
    fun `weighted analysis normalizes non one hundred total weight`() {
        val analysis = ShenzhenGradeParser.analyze(
            ShenzhenGradeCourse(
                recordId = "ME",
                taskId = "RW-1",
                courseName = "程序设计"
            ),
            """[
                {"XSCJB_ID":"ME","FXMC":"平时","DF":"80","MF":"100","LJFXBZ":"40"},
                {"XSCJB_ID":"ME","FXMC":"期末","DF":"90","MF":"100","LJFXBZ":"40"},
                {"XSCJB_ID":"OTHER","FXMC":"平时","DF":"60","MF":"100","LJFXBZ":"40"},
                {"XSCJB_ID":"OTHER","FXMC":"期末","DF":"70","MF":"100","LJFXBZ":"40"}
            ]"""
        )

        assertNotNull(analysis)
        requireNotNull(analysis)
        assertEquals(85.0, analysis.myScore ?: 0.0, 0.001)
        assertEquals(1, analysis.myRank)
        assertEquals(75.0, analysis.mean, 0.001)
        assertEquals(2, analysis.students.size)
    }

    @Test
    fun `early score matching reports ambiguous ties`() {
        val analysis = ShenzhenGradeParser.analyze(
            ShenzhenGradeCourse(taskId = "RW-1", courseName = "课程", myScore = 80.0),
            """[
                {"XSCJB_ID":"A","FXMC":"总评","DF":"80","MF":"100","LJFXBZ":"100"},
                {"XSCJB_ID":"B","FXMC":"总评","DF":"80","MF":"100","LJFXBZ":"100"}
            ]"""
        )

        requireNotNull(analysis)
        assertEquals(2, analysis.identityMatchCount)
        assertTrue(analysis.myStudentId == "A" || analysis.myStudentId == "B")
    }

    @Test
    fun `analysis keeps students with blank components and scores blanks as zero`() {
        val analysis = ShenzhenGradeParser.analyze(
            ShenzhenGradeCourse(taskId = "RW-1", courseName = "课程"),
            """[
                {"XSCJB_ID":"COMPLETE","FXMC":"平时","DF":"80","MF":"100","LJFXBZ":"40"},
                {"XSCJB_ID":"COMPLETE","FXMC":"期末","DF":"90","MF":"100","LJFXBZ":"60"},
                {"XSCJB_ID":"BLANK","FXMC":"平时","DF":"80","MF":"100","LJFXBZ":"40"},
                {"XSCJB_ID":"BLANK","FXMC":"期末","DF":"","MF":"100","LJFXBZ":"60"}
            ]"""
        )

        requireNotNull(analysis)
        assertEquals(2, analysis.students.size)
        assertEquals(59.0, analysis.mean, 0.001)
        assertEquals(1, analysis.failCount)
        assertEquals(1, analysis.excludedIncompleteStudentCount)
    }

    @Test
    fun `analysis accepts wrapped list and records response structures`() {
        val course = ShenzhenGradeCourse(taskId = "RW-1", courseName = "课程")
        val row = """{"XSCJB_ID":"A","FXMC":"总评","DF":"88","MF":"100","LJFXBZ":"100"}"""

        val contentList = ShenzhenGradeParser.analyze(
            course,
            """{"code":200,"content":{"list":[$row]}}"""
        )
        val dataRecords = ShenzhenGradeParser.analyze(
            course,
            """{"status":"ok","data":{"records":[$row]}}"""
        )

        assertEquals(88.0, requireNotNull(contentList).mean, 0.001)
        assertEquals(88.0, requireNotNull(dataRecords).mean, 0.001)
    }

    @Test
    fun `new seeFx personal response is marked personal and calculates weighted total`() {
        val course = ShenzhenGradeCourse(
            recordId = "CJ-1",
            taskId = "RW-1",
            courseName = "课程"
        )
        val body = """[
            {"XSCJB_ID":"CJ-1","FXMC":"期末","DF":"90","MF":"100","LJFXBZ":"70"},
            {"XSCJB_ID":"CJ-1","FXMC":"实验","DF":"100","MF":"100","LJFXBZ":"20"},
            {"XSCJB_ID":"CJ-1","FXMC":"作业","DF":"100","MF":"100","LJFXBZ":"10"}
        ]"""

        val analysis = ShenzhenGradeParser.analyze(
            course,
            body,
            ShenzhenGradeAnalysisScope.PERSONAL
        )

        requireNotNull(analysis)
        assertEquals(ShenzhenGradeAnalysisScope.PERSONAL, analysis.scope)
        assertEquals(1, analysis.students.size)
        assertEquals(3, analysis.myComponents.size)
        assertEquals(93.0, analysis.myScore ?: 0.0, 0.001)
    }

    @Test
    fun `analysis diagnostics distinguish empty permission and html responses`() {
        val empty = ShenzhenGradeParser.analysisResponseDiagnostics("[]")
        val forbidden = ShenzhenGradeParser.analysisResponseDiagnostics(
            """{"code":403,"message":"权限不足"}"""
        )
        val html = ShenzhenGradeParser.analysisResponseDiagnostics("<html>login</html>")

        assertEquals("root-array", empty.structure)
        assertEquals(0, empty.rowCount)
        assertEquals("403", forbidden.serverCode)
        assertEquals("权限不足", forbidden.serverMessage)
        assertEquals("html", html.structure)
    }
}
