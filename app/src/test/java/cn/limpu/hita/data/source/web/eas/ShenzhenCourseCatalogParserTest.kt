package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenCourseCatalogParserTest {
    @Test
    fun `selection module current term overrides generic current term`() {
        val id = ShenzhenCourseCatalogParser.parseSelectionTermId(
            """{"p_dqxn":"2025-2026","p_dqxq":"3","p_dqxnxq":"2025-20263"}"""
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
    fun `available course page maps capacity and schedule`() {
        val page = ShenzhenCourseCatalogParser.parsePage(
            body = """{
                "kxrwList": {
                    "total": 21,
                    "pageNum": 1,
                    "pageSize": 20,
                    "list": [{
                        "rwh":"TASK-1","kcdm":"COMP1001","kcmc":"程序设计",
                        "dgjsmc":"张老师","xf":"3.0","kkyxmc":"计算机学院",
                        "pkjgmx":"周一 1-2节 教学楼I 101","bksrl":"40","bksyxrs":"38"
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
        assertEquals(40, page.items.single().capacity)
        assertEquals(38, page.items.single().selectedCount)
        assertEquals("限选", page.items.single().selectionPoolName)
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
                        "rwh":"TASK-2","kcdm":"MATH5001","kcmc":"高等数学专题",
                        "bksrl":"50","bksyxrs":"45","yjsrl":"12","yjsyxrs":"9"
                    }]
                }
            }""",
            source = ShenzhenCourseCatalogSource.SCHOOL,
            studentType = "2"
        )

        requireNotNull(page)
        assertFalse(page.hasNextPage)
        assertEquals(12, page.items.single().capacity)
        assertEquals(9, page.items.single().selectedCount)
    }
}
