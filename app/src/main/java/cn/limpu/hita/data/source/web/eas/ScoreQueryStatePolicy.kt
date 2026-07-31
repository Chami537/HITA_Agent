package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ScoreQueryResult
import com.limpu.component.data.DataState

/** Keeps API score data successful when only optional Web enrichment has expired. */
internal object ScoreQueryStatePolicy {
    fun sessionExpired(
        successfulResult: ScoreQueryResult,
        message: String = "深圳 Web 会话已失效"
    ): DataState<ScoreQueryResult> =
        DataState(successfulResult, DataState.STATE.SUCCESS).apply {
            this.message = message
        }
}
