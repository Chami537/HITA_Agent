package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.TermItem
import com.google.gson.JsonParser

/** Builds the one-course Shenzhen selection form without reading Android state or executing requests. */
internal object ShenzhenCourseSelectionForm {
    fun build(
        token: EASToken,
        term: TermItem,
        course: CourseSelectionJobCourse
    ): Map<String, String> = build(
        studentType = token.getStudentType(),
        termYearCode = term.yearCode,
        termCode = term.termCode,
        poolCode = course.poolCode,
        requestId = course.requestId
    )

    fun build(
        studentType: String,
        termYearCode: String,
        termCode: String,
        poolCode: String,
        requestId: String
    ): Map<String, String> = linkedMapOf(
        "cxsfmt" to "0",
        "p_pylx" to studentType,
        "mxpylx" to "1",
        "p_sfgldjr" to "0",
        "p_sfredis" to "0",
        "p_sfsyxkgwc" to "0",
        "p_xktjz" to "rwtjzyx",
        "p_chaxunxh" to "",
        "p_gjz" to "",
        "p_skjs" to "",
        "p_xn" to termYearCode,
        "p_xq" to termCode,
        "p_xnxq" to "$termYearCode$termCode",
        "p_dqxn" to termYearCode,
        "p_dqxq" to termCode,
        "p_dqxnxq" to "$termYearCode$termCode",
        "p_xkfsdm" to poolCode,
        "p_xiaoqu" to "",
        "p_kkyx" to "",
        "p_kclb" to "",
        "p_xkxs" to "",
        "p_dyc" to "",
        "p_kkxnxq" to "",
        "p_id" to requestId,
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
    )
}

internal data class ShenzhenCourseSelectionResponse(
    val status: CourseSelectionCourseStatus,
    val message: String = ""
)

/** Classifies a single Shenzhen selection response; authentication always takes precedence over JSON fields. */
internal object ShenzhenCourseSelectionResponseParser {
    fun parse(statusCode: Int, responseUrl: String?, body: String): ShenzhenCourseSelectionResponse {
        if (ShenzhenWebAuthenticationClassifier.isExpired(statusCode, responseUrl, body)) {
            return ShenzhenCourseSelectionResponse(CourseSelectionCourseStatus.AUTH_REQUIRED)
        }
        if (statusCode !in 200..299) {
            return ShenzhenCourseSelectionResponse(CourseSelectionCourseStatus.UNKNOWN)
        }

        val json = runCatching { JsonParser().parse(body) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return ShenzhenCourseSelectionResponse(CourseSelectionCourseStatus.UNKNOWN)
        val resultStatus = when (json.get("jg")?.takeIf { it.isJsonPrimitive }?.asString) {
            "1" -> CourseSelectionCourseStatus.UNCONFIRMED
            "-1" -> CourseSelectionCourseStatus.BUSINESS_FAILURE
            else -> CourseSelectionCourseStatus.UNKNOWN
        }
        val message = listOf("message", "msg")
            .firstNotNullOfOrNull { key ->
                json.get(key)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
        return ShenzhenCourseSelectionResponse(resultStatus, message)
    }
}
