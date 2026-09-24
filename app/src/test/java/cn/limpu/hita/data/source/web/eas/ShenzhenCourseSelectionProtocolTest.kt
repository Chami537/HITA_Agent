package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ShenzhenCourseSelectionProtocolTest {
    @Test
    fun `typed form inputs preserve the Shenzhen job request identity`() {
        val form = ShenzhenCourseSelectionForm.build(
            EASToken(),
            TermItem("2026-2027", "2026-2027", "1", "秋季"),
            CourseSelectionJobCourse(
                requestId = "request-123",
                taskId = "task-123",
                courseId = "course-123",
                courseCode = "COMP1001",
                courseName = "程序设计",
                teacher = "张老师",
                poolCode = "xx-b-b"
            )
        )

        assertEquals("1", form["p_pylx"])
        assertEquals("2026-2027", form["p_xn"])
        assertEquals("1", form["p_xq"])
        assertEquals("2026-20271", form["p_xnxq"])
        assertEquals("2026-2027", form["p_dqxn"])
        assertEquals("1", form["p_dqxq"])
        assertEquals("2026-20271", form["p_dqxnxq"])
        assertEquals("xx-b-b", form["p_xkfsdm"])
        assertEquals("request-123", form["p_id"])
    }

    @Test
    fun `form preserves every Class prototype field and exact value`() {
        val form = ShenzhenCourseSelectionForm.build(
            studentType = "1",
            termYearCode = "2026-2027",
            termCode = "1",
            poolCode = "xx-b-b",
            requestId = "request-123"
        )

        assertEquals(
            linkedMapOf(
                "cxsfmt" to "0",
                "p_pylx" to "1",
                "mxpylx" to "1",
                "p_sfgldjr" to "0",
                "p_sfredis" to "0",
                "p_sfsyxkgwc" to "0",
                "p_xktjz" to "rwtjzyx",
                "p_chaxunxh" to "",
                "p_gjz" to "",
                "p_skjs" to "",
                "p_xn" to "2026-2027",
                "p_xq" to "1",
                "p_xnxq" to "2026-20271",
                "p_dqxn" to "2026-2027",
                "p_dqxq" to "1",
                "p_dqxnxq" to "2026-20271",
                "p_xkfsdm" to "xx-b-b",
                "p_xiaoqu" to "",
                "p_kkyx" to "",
                "p_kclb" to "",
                "p_xkxs" to "",
                "p_dyc" to "",
                "p_kkxnxq" to "",
                "p_id" to "request-123",
                "p_sfhlctkc" to "0",
                "p_sfhllrlkc" to "0",
                "p_kxsj_xqj" to "",
                "p_kxsj_ksjc" to "",
                "p_kxsj_jsjc" to "",
                "p_kcdm_js" to "",
                "p_kcdm_cxrw" to "",
                "p_kc_gjz" to "",
                "p_xzcxtjz_nj" to "",
                "p_xzcxtjz_yx" to "",
                "p_xzcxtjz_zy" to "",
                "p_xzcxtjz_zyfx" to "",
                "p_xzcxtjz_bj" to "",
                "p_sfxsgwckb" to "1",
                "p_skyy" to "",
                "p_chaxunxkfsdm" to "",
                "pageNum" to "1",
                "pageSize" to "18"
            ),
            form
        )
    }

    @Test
    fun `jg one is unconfirmed for string and numeric JSON values`() {
        val stringResult = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            """{"jg":"1","message":"成功"}"""
        )
        val numericResult = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            """{"jg":1,"message":"成功"}"""
        )

        assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, stringResult.status)
        assertEquals(CourseSelectionCourseStatus.UNCONFIRMED, numericResult.status)
    }

    @Test
    fun `jg minus one is a business failure`() {
        val result = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            """{"jg":-1,"message":"选课失败"}"""
        )

        assertEquals(CourseSelectionCourseStatus.BUSINESS_FAILURE, result.status)
    }

    @Test
    fun `authentication redirect HTML and guidance JSON are never accepted`() {
        val redirectResult = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/authentication/require",
            "<html><title>Loading...</title></html>"
        )
        val guidanceResult = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            """{"code":200,"content":"访问的服务需要身份认证，请引导用户到登录页"}"""
        )

        assertEquals(CourseSelectionCourseStatus.AUTH_REQUIRED, redirectResult.status)
        assertEquals(CourseSelectionCourseStatus.AUTH_REQUIRED, guidanceResult.status)
    }

    @Test
    fun `malformed JSON and non JSON server errors are unknown`() {
        val malformedResult = ShenzhenCourseSelectionResponseParser.parse(
            200,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            "not-json"
        )
        val serverErrorResult = ShenzhenCourseSelectionResponseParser.parse(
            500,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            "server error"
        )

        assertEquals(CourseSelectionCourseStatus.UNKNOWN, malformedResult.status)
        assertEquals(CourseSelectionCourseStatus.UNKNOWN, serverErrorResult.status)
    }

    @Test
    fun `non successful HTTP response never accepts jg one`() {
        val result = ShenzhenCourseSelectionResponseParser.parse(
            500,
            "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
            """{"jg":1,"message":"成功"}"""
        )

        assertEquals(CourseSelectionCourseStatus.UNKNOWN, result.status)
    }

    @Test
    fun `credential echoes are removed at the response boundary including unicode separators`() {
        val maliciousMessages = listOf(
            "Cookie\u00a0=\u00a0SESSION=server-cookie-secret",
            "Authorization\u202f=\u202fBearer server-bearer-secret",
            "se\u200bssion\u200c=server-session-secret"
        )

        maliciousMessages.forEach { malicious ->
            val result = ShenzhenCourseSelectionResponseParser.parse(
                200,
                "https://jw.hitsz.edu.cn/Xsxk/addGouwuche",
                """{"jg":-1,"message":"$malicious"}"""
            )

            assertEquals(CourseSelectionCourseStatus.BUSINESS_FAILURE, result.status)
            assertEquals("", result.message)
        }
    }
}
