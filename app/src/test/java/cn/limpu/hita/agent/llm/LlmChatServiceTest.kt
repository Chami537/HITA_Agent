package cn.limpu.hita.agent.llm

import cn.limpu.hita.agent.tools.ReActToolRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LlmChatServiceTest {

    @Test
    fun `registry contains supported tools`() {
        val registry = ReActToolRegistry.createDefault()
        val tools = listOf(
            "get_timetable", "add_activity", "search_empty_classroom", "search_course", "get_course_detail",
            "search_external_resource", "search_teacher", "web_search", "rag_search",
            "search_timetable",
            "crawl_page", "crawl_site", "crawl_status", "submit_review",
        )
        tools.forEach { name ->
            assertNotNull("Tool $name should be registered", registry.get(name))
        }
    }

    @Test
    fun `react prompt tool list matches registry`() {
        val registryNames = ReActToolRegistry.createDefault().names().sorted()
        val prompt = listOf(
            File("src/main/assets/react_system_prompt.txt"),
            File("app/src/main/assets/react_system_prompt.txt"),
        ).first { it.exists() }.readText()
        val promptNames = Regex("""(?m)^\d+\.\s+([a-z_][a-z_]*)""")
            .findAll(prompt)
            .map { it.groupValues[1] }
            .toList()
            .sorted()

        assertEquals(registryNames, promptNames)
    }

    @Test
    fun `registry is case-insensitive`() {
        val registry = ReActToolRegistry.createDefault()
        assertNotNull(registry.get("GET_TIMETABLE"))
        assertNotNull(registry.get("Get_Timetable"))
    }

    @Test
    fun `registry normalizes common model tool name variants`() {
        val registry = ReActToolRegistry.createDefault()
        val expected = registry.get("get_course_detail")

        assertNotNull(expected)
        assertEquals(expected, registry.get("get-course-detail"))
        assertEquals(expected, registry.get("get course detail"))
        assertEquals(expected, registry.get(" GET.COURSE.DETAIL "))
    }

    @Test
    fun `unknown tool returns null`() {
        val registry = ReActToolRegistry.createDefault()
        assertNull(registry.get("nonexistent_tool"))
    }

    @Test
    fun `registry returns same instance for same name`() {
        val registry = ReActToolRegistry.createDefault()
        assertEquals(registry.get("get_timetable"), registry.get("GET_TIMETABLE"))
    }

    @Test
    fun `registry does not contain unregistered tool`() {
        val registry = ReActToolRegistry.createDefault()
        assertNull(registry.get("delete_timetable"))
        assertNull(registry.get("update_course"))
        assertNull(registry.get("brave_answer"))
    }
}
