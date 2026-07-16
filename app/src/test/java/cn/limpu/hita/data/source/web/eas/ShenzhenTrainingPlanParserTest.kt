package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenTrainingPlanLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenTrainingPlanParserTest {
    @Test
    fun `undergraduate plans parse and match current student major`() {
        val plans = ShenzhenTrainingPlanParser.parsePlans(
            """{"content":{"pages":1,"list":[
                {"fah":"CS-2023","bgid":"B1","famc":"计算机科学与技术培养方案","zymc":"计算机科学与技术专业","njdm":"2023","yxmc":"计算机科学与技术学院"},
                {"fah":"AUTO-2023","famc":"自动化培养方案","zymc":"自动化","njdm":"2023"}
            ]}}""",
            ShenzhenTrainingPlanLevel.UNDERGRADUATE
        ).orEmpty()

        val matched = ShenzhenTrainingPlanParser.matchPersonalPlans(plans, "计算机科学与技术")

        assertEquals(2, plans.size)
        assertEquals("CS-2023", matched.single().id)
        assertEquals("B1", matched.single().changeId)
    }

    @Test
    fun `undergraduate courses retain category term credits and hours`() {
        val courses = ShenzhenTrainingPlanParser.parseCourses(
            """{"content":{"list":[{
                "fah":"CS-2023","kcdm":"CS101","kcmc":"程序设计","xf":"3",
                "xszxs":"48","xsllxs":"32","xssyxs":"16","sfbx":"1",
                "kclbmc":"专业基础课","tjkkxnxq":"2023-2024-1"
            }]}}"""
        ).orEmpty()

        assertEquals("CS101", courses.single().courseCode)
        assertEquals(3.0, courses.single().credits ?: 0.0, 0.0)
        assertEquals(48.0, courses.single().totalHours ?: 0.0, 0.0)
        assertTrue(courses.single().required == true)
        assertEquals("专业基础课", courses.single().courseCategory)
    }

    @Test
    fun `postgraduate group tree and complete course response combine`() {
        val plan = requireNotNull(
            ShenzhenTrainingPlanParser.parsePlans(
                """{"content":{"list":[{"fah":"PG-1","bgid":"PB-1","famc":"硕士方案","zymc":"计算机科学与技术","bbh":"202603"}]}}""",
                ShenzhenTrainingPlanLevel.POSTGRADUATE
            )
        ).single()
        val groups = ShenzhenTrainingPlanParser.parseGroups(
            """[{"kzid":"ROOT","kzmc":"学位课","yqxdxf":"10"},
                 {"kzid":"CORE","fkzid":"ROOT","kzmc":"专业核心课","kzsfbx":"1","yqxdms":"2"}]"""
        ).orEmpty()
        val courses = ShenzhenTrainingPlanParser.parseCourses(
            """{"content":{"list":[{"fah":"PG-1","kzid":"CORE","kzmc":"专业核心课","kcdm":"CS501","kcmc":"高级算法","xf":"3","sfbx":"0"}]}}"""
        ).orEmpty()
        val detail = ShenzhenTrainingPlanParser.combine(plan, groups, courses)

        assertEquals("ROOT", groups.last().parentId)
        assertTrue(groups.last().required == true)
        assertFalse(courses.single().required == true)
        assertEquals("专业核心课", detail.categories.single().name)
        assertEquals(3.0, detail.totalCredits, 0.0)
    }

    @Test
    fun `identity and page count parse nested payloads`() {
        val identity = ShenzhenTrainingPlanParser.parseIdentity(
            """{"content":{"list":[{"ZYMC":"软件工程","NJMC":"2024级","PYLX":"1"}]}}"""
        )

        requireNotNull(identity)
        assertEquals("软件工程", identity.major)
        assertEquals("2024级", identity.grade)
        assertEquals(3, ShenzhenTrainingPlanParser.parsePageCount("""{"content":{"pages":3}}"""))
    }
}
