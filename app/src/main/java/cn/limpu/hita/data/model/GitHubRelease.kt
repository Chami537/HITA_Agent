package cn.limpu.hita.data.model

import com.google.gson.annotations.SerializedName

data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    val name: String? = null,
    @SerializedName("download_count") val downloadCount: Long = 0
)

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String? = null,
    val name: String? = null,
    val prerelease: Boolean? = null,
    val draft: Boolean? = null,
    @SerializedName("html_url")
    val htmlUrl: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset>? = null
)
