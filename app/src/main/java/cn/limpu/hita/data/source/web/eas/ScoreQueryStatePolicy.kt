package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ScoreQueryResult
import com.limpu.component.data.DataState

/** Keeps successful score data visible while an optional Web enrichment requests re-login. */
internal object ScoreQueryStatePolicy {
    fun sessionExpired(
        successfulResult: ScoreQueryResult,
        message: String = "深圳 Web 会话已失效"
    ): DataState<ScoreQueryResult> =
        DataState(successfulResult, DataState.STATE.NOT_LOGGED_IN).apply {
            this.message = message
        }
}
