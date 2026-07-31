package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.EASToken

/** Source selection for Shenzhen core academic data shared by the normal app flows. */
internal object ShenzhenCoreDataSourcePolicy {
    fun shouldUseWebFallback(token: EASToken): Boolean =
        token.accessToken.isNullOrBlank() && token.hasShenzhenWebSession()
}
