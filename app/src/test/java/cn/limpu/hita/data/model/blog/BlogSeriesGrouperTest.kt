package cn.limpu.hita.data.model.blog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogSeriesGrouperTest {
    @Test
    fun `group nests three path levels under nearest ancestor`() {
        val parent = article("https://hoa.moe/blog/auto-survival-guide", "指南", 3, "auto-survival-guide")
        val imu = article("https://hoa.moe/blog/auto-survival-guide/imu", "IMU", 2, "auto-survival-guide/imu")
        val bmi = article(
            "https://hoa.moe/blog/auto-survival-guide/imu/bmi088",
            "BMI088",
            1,
            "auto-survival-guide/imu/bmi088",
        )
        val standalone = article("https://hoa.moe/blog/macbook-guide", "MacBook", 4, "macbook-guide")

        val roots = BlogSeriesGrouper.group(listOf(bmi, standalone, imu, parent))
        assertEquals(listOf("macbook-guide", "auto-survival-guide"), roots.map { it.article.path })
        assertFalse(roots[0].isSeries)

        val series = roots[1]
        assertTrue(series.isSeries)
        assertEquals(listOf("auto-survival-guide/imu"), series.children.map { it.article.path })
        assertEquals(
            listOf("auto-survival-guide/imu/bmi088"),
            series.children.single().children.map { it.article.path },
        )
    }

    @Test
    fun `group treats missing parent as its own root`() {
        val orphan = article(
            "https://hoa.moe/blog/course-selection-auto/distributive-guidance-for-23",
            "23 级",
            1,
            "course-selection-auto/distributive-guidance-for-23",
        )
        val roots = BlogSeriesGrouper.group(listOf(orphan))
        assertEquals(1, roots.size)
        assertEquals(orphan.guid, roots.single().article.guid)
        assertFalse(roots.single().isSeries)
    }

    private fun article(guid: String, title: String, rank: Long, path: String): BlogArticle {
        return BlogArticle(
            guid = guid,
            title = title,
            link = guid,
            pubDateMillis = rank,
            description = title,
            htmlContent = "<p>$title</p>",
            path = path,
        )
    }
}
