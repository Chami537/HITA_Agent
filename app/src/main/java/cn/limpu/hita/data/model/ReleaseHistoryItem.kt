package cn.limpu.hita.data.model

data class ReleaseHistoryItem(
    val versionName: String,
    val releaseName: String,
    val markdown: String,
    val htmlUrl: String,
    val prerelease: Boolean,
)
