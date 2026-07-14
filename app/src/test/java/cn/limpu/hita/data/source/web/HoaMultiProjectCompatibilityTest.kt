package cn.limpu.hita.data.source.web

import cn.limpu.hita.BuildConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Opt-in black-box compatibility test for the deployed HOA PR server.
 *
 * Run after deploying pr-server:
 * HOA_INTEGRATION_TEST=1 ./gradlew :app:testDebugUnitTest \
 *   --tests 'cn.limpu.hita.data.source.web.HoaMultiProjectCompatibilityTest' --rerun-tasks
 */
class HoaMultiProjectCompatibilityTest {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    @Test
    fun `all indexed multi project repositories support structured preview ops`() {
        assumeTrue(
            "Set HOA_INTEGRATION_TEST=1 to run deployed-server compatibility checks",
            System.getenv("HOA_INTEGRATION_TEST") == "1",
        )

        val search = post(
            "/v1/courses:search",
            """{"keyword":"","campus":"shenzhen"}""",
        )
        checkOk("course index", search)
        val indexed = search.getAsJsonObject("data").getAsJsonArray("results")
            .map { it.asJsonObject }
            .toMutableList()
        val indexedRepositories = indexed.mapTo(mutableSetOf()) {
            it.get("repo")?.asString.orEmpty()
        }
        EXTRA_MULTI_PROJECT_REPOSITORIES
            .filterNot(indexedRepositories::contains)
            .forEach { repository ->
                indexed += JsonObject().apply {
                    addProperty("repo", repository)
                    addProperty("code", repository)
                }
            }

        val executor = Executors.newFixedThreadPool(8)
        val reads = try {
            indexed.map { course ->
                executor.submit<IndexedRead> {
                    val courseCode = course.get("code")?.asString.orEmpty()
                    val repo = course.get("repo")?.asString.orEmpty().ifBlank { courseCode }
                    val targetIdentifier = resolveHoaRepositoryIdentifier(repo, courseCode)
                    if (targetIdentifier.isBlank()) {
                        return@submit IndexedRead(course, null, "missing repository/course identifier in index")
                    }
                    val readRequest = JsonObject().apply {
                        add("target", JsonObject().apply {
                            addProperty("campus", "shenzhen")
                            addProperty("course_code", targetIdentifier)
                        })
                        addProperty("include_toml", true)
                    }
                    runCatching { post("/v1/course:read", readRequest.toString()) }
                        .fold(
                            onSuccess = { IndexedRead(course, it, null) },
                            onFailure = { IndexedRead(course, null, it.message ?: it.javaClass.simpleName) },
                        )
                }
            }.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val tested = mutableListOf<String>()
        var testedChildCourses = 0
        val failures = mutableListOf<String>()

        reads.forEach { indexedRead ->
            val course = indexedRead.course
            val courseCode = course.get("code")?.asString.orEmpty()
            val repo = course.get("repo")?.asString.orEmpty().ifBlank { courseCode }
            val targetIdentifier = resolveHoaRepositoryIdentifier(repo, courseCode)
            if (indexedRead.error != null) {
                failures += "$repo: read failed: ${indexedRead.error}"
                return@forEach
            }
            val read = indexedRead.response ?: return@forEach
            if (!read.get("ok").asBoolean) {
                failures += "$repo: read rejected: ${errorMessage(read)}"
                return@forEach
            }

            val readResult = read.getAsJsonObject("data").getAsJsonObject("result")
            val source = readResult.get("readme_toml")?.asString.orEmpty()
            if (!MULTI_PROJECT_META.containsMatchIn(source)) {
                return@forEach
            }

            tested += repo
            val suffix = targetIdentifier.replace(Regex("[^A-Za-z0-9]+"), "_")
            val childName = "__HITA_COMPAT_${suffix}__"
            val bodyMarker = "HITA_COMPAT_BODY_${suffix}"
            val teacherMarker = "HITA_COMPAT_TEACHER_${suffix}"
            val existingCourseNames = COURSE_META.findAll(readResult.get("readme_md")?.asString.orEmpty())
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            if (existingCourseNames.isEmpty()) {
                failures += "$repo: generated README exposes no child-course metadata"
                return@forEach
            }
            testedChildCourses += existingCourseNames.size

            val expectedByCourse = linkedMapOf(childName to mutableListOf(bodyMarker, teacherMarker))
            val ops = JsonArray().apply {
                add(createCourseOp(childName))
                add(appendSectionOp(childName, bodyMarker))
                add(addTeacherReviewOp(childName, teacherMarker))
                existingCourseNames.forEachIndexed { index, existingCourseName ->
                    val existingSectionMarker = "HITA_EXISTING_SECTION_${suffix}_$index"
                    val existingTeacherMarker = "HITA_EXISTING_TEACHER_${suffix}_$index"
                    expectedByCourse.getOrPut(existingCourseName) { mutableListOf() }
                        .addAll(listOf(existingSectionMarker, existingTeacherMarker))
                    add(appendSectionOp(existingCourseName, existingSectionMarker))
                    add(addTeacherReviewOp(existingCourseName, existingTeacherMarker))
                }
            }
            val requestJson = JsonObject().apply {
                add("target", JsonObject().apply {
                    addProperty("campus", "shenzhen")
                    addProperty("course_code", targetIdentifier)
                })
                add("ops", ops)
            }.toString()

            val preview = runCatching { post("/v1/course:preview", requestJson) }.getOrElse {
                failures += "$repo: preview failed: ${it.message}"
                return@forEach
            }
            if (!preview.get("ok").asBoolean) {
                failures += "$repo: preview rejected: ${errorMessage(preview)}"
                return@forEach
            }

            val result = preview.getAsJsonObject("data").getAsJsonObject("result")
            val outputToml = result.get("readme_toml")?.asString.orEmpty()
            val outputMarkdown = result.get("readme_md")?.asString.orEmpty()
            val misplacedContent = expectedByCourse.mapNotNull { (expectedCourse, markers) ->
                val block = courseBlock(outputMarkdown, expectedCourse)
                when {
                    block == null -> "$expectedCourse: no rendered course block"
                    else -> markers.filterNot(block::contains)
                        .takeIf { it.isNotEmpty() }
                        ?.let { "$expectedCourse: missing ${it.joinToString()}" }
                }
            }
            if (!outputToml.contains("[[courses]]") ||
                !outputToml.contains("[[courses.sections]]") ||
                !outputToml.contains("[[courses.teachers]]") ||
                misplacedContent.isNotEmpty()
            ) {
                failures += buildString {
                    append("$repo: preview output lost array schema or placed rendered content outside its target child course")
                    if (misplacedContent.isNotEmpty()) {
                        append(" (").append(misplacedContent.joinToString("; ")).append(")")
                    }
                }
            }
        }

        println(
            "Validated ${tested.size} multi-project repositories and $testedChildCourses existing child courses: " +
                tested.joinToString()
        )
        assertTrue("No real multi-project repositories found; index/read contract may have regressed", tested.isNotEmpty())
        assertTrue(
            buildString {
                append("multi-project compatibility failures (${failures.size}/${tested.size}):")
                failures.forEach { append("\n- ").append(it) }
            },
            failures.isEmpty(),
        )
    }

    private fun post(path: String, json: String): JsonObject {
        val request = Request.Builder()
            .url(BuildConfig.HOA_BASE_URL.removeSuffix("/") + path)
            .header("Accept", "application/json")
            .apply {
                if (BuildConfig.HOA_API_KEY.isNotBlank()) {
                    header("X-Api-Key", BuildConfig.HOA_API_KEY)
                }
            }
            .post(json.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(body.isNotBlank()) { "$path returned HTTP ${response.code} with an empty body" }
            @Suppress("DEPRECATION")
            return JsonParser().parse(body).asJsonObject
        }
    }

    private fun checkOk(label: String, response: JsonObject) {
        check(response.get("ok")?.asBoolean == true) { "$label failed: ${errorMessage(response)}" }
    }

    private fun errorMessage(response: JsonObject): String {
        return response.getAsJsonObject("error")?.get("message")?.asString ?: response.toString()
    }

    private fun createCourseOp(courseName: String): JsonObject {
        return JsonObject().apply {
            addProperty("op", "create_course")
            addProperty("course_name", courseName)
        }
    }

    private fun appendSectionOp(courseName: String, content: String): JsonObject {
        return JsonObject().apply {
            addProperty("op", "append_course_section_item")
            addProperty("course_name", courseName)
            addProperty("section_title", "兼容性验证")
            add("item", JsonObject().apply {
                addProperty("content", content)
                add("author", testAuthor())
            })
        }
    }

    private fun addTeacherReviewOp(courseName: String, content: String): JsonObject {
        return JsonObject().apply {
            addProperty("op", "add_course_teacher_review")
            addProperty("course_name", courseName)
            addProperty("teacher_name", "兼容性测试教师")
            addProperty("content", content)
            add("author", testAuthor())
        }
    }

    private fun testAuthor(): JsonObject {
        return JsonObject().apply {
            addProperty("name", "compat-test")
            addProperty("link", "")
            addProperty("date", "2026-07")
        }
    }

    private fun courseBlock(markdown: String, courseName: String): String? {
        val courseMetadata = COURSE_META.findAll(markdown).toList()
        val metadata = courseMetadata.firstOrNull {
            it.groupValues[1].trim() == courseName.trim()
        } ?: return null
        val headings = Regex("(?m)^##\\s+(.+?)\\s*$").findAll(markdown).toList()
        val start = headings.lastOrNull { it.range.first < metadata.range.first }?.range?.first
            ?: metadata.range.first
        val end = headings.firstOrNull { it.range.first > metadata.range.first }?.range?.first
            ?: markdown.length
        return markdown.substring(start, end)
    }

    private companion object {
        val EXTRA_MULTI_PROJECT_REPOSITORIES = listOf(
            "Cross-ARCH",
            "Cross-CEEV",
            "Cross-ECON",
            "Cross-EIE",
            "Cross-ENER",
            "Cross-SPST",
            "Cross-Science",
        )
        val MULTI_PROJECT_META = Regex("(?m)^repo_type\\s*=\\s*\"multi-project\"\\s*$")
        val COURSE_META = Regex("""<!--\s*TOML-COURSE:[^>]*\bname="([^"]+)"[^>]*-->""")
    }

    private data class IndexedRead(
        val course: JsonObject,
        val response: JsonObject?,
        val error: String?,
    )
}
