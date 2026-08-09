package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseAttachmentKind
import cn.limpu.hita.data.model.eas.ShenzhenSelectionOpenTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenCourseCatalogParserTest {
    @Test
    fun `selection pools come from current teaching response and are deduplicated`() {
        val pools = ShenzhenCourseCatalogParser.parseSelectionPools(
            """{"xkgzszList":[
                {"xkfsdm":"xx-b-b","xkfsmc":"限选"},
                {"XKFSDM":"xx-b-b","XKFSMC":"重复"},
                {"xkfsdm":"shsj-b-b","xkfsmc":"社会实践"}
            ]}"""
        )

        assertEquals(listOf("xx-b-b", "shsj-b-b"), pools.map { it.code })
        assertEquals(listOf("限选", "社会实践"), pools.map { it.name })
    }

    @Test
    fun `selection module current term overrides generic current term`() {
        val id = ShenzhenCourseCatalogParser.parseSelectionTermId(
            """{
                "p_xn":"2026-2027","p_xq":"1","p_xnxq":"2026-20271",
                "p_dqxn":"2025-2026","p_dqxq":"3","p_dqxnxq":"2025-20263"
            }"""
        )

        assertEquals("2026-2027-1", id)
    }

    @Test
    fun `selection term falls back to academic current term when target is blank`() {
        val id = ShenzhenCourseCatalogParser.parseSelectionTermId(
            """{"content":{"p_xn":"","p_xq":"","p_dqxn":"2025-2026","p_dqxq":"3"}}"""
        )

        assertEquals("2025-2026-3", id)
    }

    @Test
    fun `web terms preserve current term marker`() {
        val terms = ShenzhenCourseCatalogParser.parseTerms(
            """[
                {"XN":"2025-2026","XNMC":"2025-2026","XQ":"2","XQMC":"春季","XNXQMC":"2026春季","SFDQXQ":"0"},
                {"XN":"2025-2026","XNMC":"2025-2026","XQ":"3","XQMC":"夏季","XNXQMC":"2026夏季","SFDQXQ":"1"}
            ]"""
        ).orEmpty()

        assertEquals(2, terms.size)
        assertEquals("2025-2026-3", terms.last().id)
        assertTrue(terms.last().isCurrent)
    }

    @Test
    fun `course selection dropdown parses nested lowercase terms including next autumn`() {
        val terms = ShenzhenCourseCatalogParser.parseTerms(
            """{"content":[
                {"xn":"2026-2027","xq":"1","xnxq":"2026-20271","xnmc":"2026-2027学年","xqmc":"秋季学期"},
                {"xn":"2025-2026","xq":"3","xnxq":"2025-20263","xnmc":"2025-2026学年","xqmc":"夏季学期"}
            ]}"""
        ).orEmpty()

        assertEquals(listOf("2026-2027-1", "2025-2026-3"), terms.map { it.id })
        assertEquals("2026-2027学年秋季学期", terms.first().name)
    }

    @Test
    fun `available course page maps capacity and schedule`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "kxrwList": {
                    "total": 21,
                    "pageNum": 1,
                    "pageSize": 20,
                    "list": [{
                        "id":"RW-1","rwh":"TASK-1","kcid":"COURSE-1","kcdm":"COMP1001","kcmc":"程序设计",
                        "dgjsmc":"张老师","xf":"3.0","kkyxmc":"计算机学院",
                        "xkyq":"计算机轨道",
                        "pkjgmx":"<div><p>周一 1-2节</p><p>教学楼I 101</p></div>",
                        "bksrl":"40","bksyxrs":"38"
                    }]
                }
            }""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1",
            selectionPoolName = "限选"
        )

        requireNotNull(page)
        assertEquals(21, page.total)
        assertTrue(page.hasNextPage)
        assertEquals("程序设计", page.items.single().courseName)
        assertEquals("COURSE-1", page.items.single().courseId)
        assertEquals("TASK-1", page.items.single().taskNumber)
        assertEquals("TASK-1", page.items.single().taskId)
        assertEquals("RW-1", page.items.single().selectionRequestId)
        assertEquals(40, page.items.single().capacity)
        assertEquals(38, page.items.single().selectedCount)
        assertEquals("限选", page.items.single().selectionPoolName)
        assertEquals("计算机轨道", page.items.single().selectionRequirement)
        assertEquals("周一 1-2节 教学楼I 101", page.items.single().schedule)
    }

    @Test
    fun `school page uses postgraduate capacity`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "rwList": {
                    "total": 1,
                    "pageNum": 1,
                    "pageSize": 20,
                    "list": [{
                        "id":"RW-2","rwh":"TASK-2","kcdm":"MATH5001","kcmc":"高等数学专题",
                        "bksrl":"50","bksyxrs":"45","yjsrl":"12","yjsyxrs":"9"
                    }]
                }
            }""",
            source = ShenzhenCourseCatalogSource.SCHOOL,
            studentType = "2"
        )

        requireNotNull(page)
        assertFalse(page.hasNextPage)
        assertEquals("TASK-2", page.items.single().taskId)
        assertEquals("RW-2", page.items.single().selectionRequestId)
        assertEquals(12, page.items.single().capacity)
        assertEquals(9, page.items.single().selectedCount)
    }

    @Test
    fun `selected course array and conflict fields are parsed`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "yxkcList":[{
                    "id":"RW-3","rwh":"TASK-3","kcdm":"COMP2001","kcmc":"算法",
                    "xf":"3","sfkct":"1","ctkcxx":"与 已选课程 冲突",
                    "pkjgmx":"<p>1-16周,星期一第1-2节 T3401</p>"
                }]
            }""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )

        requireNotNull(page)
        assertEquals(1, page.items.size)
        assertTrue(page.items.single().hasConflict)
        assertEquals("与 已选课程 冲突", page.items.single().conflictDescription)
        assertEquals("1-16周,星期一第1-2节 T3401", page.items.single().schedule)
        assertEquals((1..16).toList(), page.items.single().meetings.single().weeks)
        assertEquals(1, page.items.single().meetings.single().weekday)
        assertEquals(2, page.items.single().meetings.single().endPeriod)
    }

    @Test
    fun `selected operation timestamp is not treated as a meeting`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "yxkcList":[{
                    "id":"ROW-1","rwh":"TASK-1","kcdm":"COMP2001","kcmc":"算法",
                    "xksj":"2026-07-26 10:30:00",
                    "pkjgmx":"<p>1-8单周,星期三第3-4节 [T3401]</p>"
                }]
            }""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )

        requireNotNull(page)
        val item = page.items.single()
        assertFalse(item.schedule.contains("2026-07-26"))
        assertEquals(listOf(1, 3, 5, 7), item.meetings.single().weeks)
        assertEquals(3, item.meetings.single().weekday)
    }

    @Test
    fun `selection opening time parses zoned and Shenzhen local values`() {
        val zoned = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
            """{"ktxkkssj":"2026-08-10T09:30:15+08:00"}"""
        )
        val local = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
            """{"ksrq":"2026-08-10 09:30:15"}"""
        )

        assertEquals(1_786_325_415_000L, zoned?.epochMillis)
        assertEquals(1_786_325_415_000L, local?.epochMillis)
        assertEquals("2026-08-10T09:30:15+08:00", zoned?.rawValue)
    }

    @Test
    fun `selection opening time prefers ktxkkssj and ignores malformed values`() {
        val preferred = ShenzhenCourseCatalogParser.parseSelectionOpenTime(
            """{"ktxkkssj":"2026-08-10 10:00:00","ksrq":"2026-08-10 11:00:00"}"""
        )

        assertEquals("2026-08-10 10:00:00", preferred?.rawValue)
        assertNull(ShenzhenCourseCatalogParser.parseSelectionOpenTime("""{"ksrq":"not-a-time"}"""))
    }

    @Test
    fun `available page resolves course then page then selection rule opening time`() {
        val coursePage = ShenzhenCourseCatalogParser.parsePage(
            body = """{"ksrq":"2026-08-10 09:00:00","kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course","ktxkkssj":"2026-08-10 08:00:00"}]}}""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )
        assertEquals(
            ShenzhenSelectionOpenTimeSource.COURSE,
            coursePage?.items?.single()?.selectionOpenTime?.source
        )

        val pageFallback = ShenzhenCourseCatalogParser.parsePage(
            body = """{"ktxkkssj":"2026-08-10 09:00:00","kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course"}]}}""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )
        assertEquals(
            ShenzhenSelectionOpenTimeSource.POOL_OR_PAGE,
            pageFallback?.items?.single()?.selectionOpenTime?.source
        )

        val ruleFallback = ShenzhenCourseCatalogParser.parsePage(
            body = """{"xkgzszOne":{"ksrq":"2026-08-10 10:00:00"},"kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course"}]}}""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )
        assertEquals(
            ShenzhenSelectionOpenTimeSource.SELECTION_RULE,
            ruleFallback?.items?.single()?.selectionOpenTime?.source
        )
    }

    @Test
    fun `xksj remains excluded from selection opening time`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{"kxrwList":{"list":[{"id":"request","kcdm":"CS101","kcmc":"Course","xksj":"2026-08-10 10:00:00"}]}}""",
            source = ShenzhenCourseCatalogSource.AVAILABLE,
            studentType = "1"
        )

        assertNull(page?.items?.single()?.selectionOpenTime)
    }

    @Test
    fun `structured school meeting supports binary week mask`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "rwList":{"list":[{
                    "rwh":"TASK-9","kcdm":"MATH1001","kcmc":"数学",
                    "sksjList":[{
                        "xqj":"5","ksjc":"7","jsjc":"8",
                        "zc":"110100000000000000000000000000000",
                        "skjs":"李老师","jasmc":"A101"
                    }]
                }]}
            }""",
            source = ShenzhenCourseCatalogSource.SCHOOL,
            studentType = "1"
        )

        val meeting = requireNotNull(page).items.single().meetings.single()
        assertEquals(listOf(1, 2, 4), meeting.weeks)
        assertEquals(5, meeting.weekday)
        assertEquals("A101", meeting.location)
        assertTrue(page.items.single().isFollowable)
    }

    @Test
    fun `course detail maps description and bilingual syllabus attachments`() {
        val attachments = ShenzhenCourseCatalogParser.parseAttachments(
            body = """{
                "code":200,
                "content":{
                    "kcxxbgbEntity":{
                        "kcjjfname":"课程简介.docx",
                        "kcjjsname":"/document/kcgl/kcjj/intro.docx"
                    },
                    "kcdgbentity":{
                        "kczwdgwjm":"中文教学大纲.doc",
                        "kczwdgurl":"/document/kcgl/kcdg/zh.doc",
                        "kczwdgsize":"49152",
                        "kcywdgwjm":"English syllabus.pdf",
                        "kcywdgurl":"/document/kcgl/kcdg/en.pdf",
                        "kcywdgsize":"2048"
                    }
                }
            }""",
            courseId = "COURSE-1"
        ).orEmpty()

        assertEquals(3, attachments.size)
        assertEquals(
            ShenzhenCourseAttachmentKind.COURSE_DESCRIPTION,
            attachments[0].kind
        )
        assertEquals("/document/kcgl/kcjj/intro.docx", attachments[0].serverPath)
        assertEquals(ShenzhenCourseAttachmentKind.CHINESE_SYLLABUS, attachments[1].kind)
        assertEquals(49152L, attachments[1].sizeBytes)
        assertEquals(ShenzhenCourseAttachmentKind.ENGLISH_SYLLABUS, attachments[2].kind)
        assertEquals("COURSE-1", attachments[2].courseId)
    }

    @Test
    fun `course detail without published files returns empty attachment list`() {
        val attachments = ShenzhenCourseCatalogParser.parseAttachments(
            body = """{
                "code":200,
                "content":{
                    "kcxxbgbEntity":{"kcjjfname":null,"kcjjsname":null},
                    "kcdgbentity":{
                        "kczwdgwjm":null,
                        "kczwdgurl":null,
                        "kcywdgwjm":null,
                        "kcywdgurl":null
                    }
                }
            }""",
            courseId = "COURSE-1"
        )

        requireNotNull(attachments)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun `course description requires both display name and server path`() {
        val attachments = ShenzhenCourseCatalogParser.parseAttachments(
            body = """{
                "content":{
                    "kcxxbgbEntity":{"kcjjfname":"简介.docx","kcjjsname":null},
                    "kcdgbentity":{}
                }
            }""",
            courseId = "COURSE-1"
        )

        requireNotNull(attachments)
        assertTrue(attachments.isEmpty())
    }
}
