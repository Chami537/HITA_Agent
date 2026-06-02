package cn.limpu.hita.data.source.web

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.limpu.component.data.DataState
import com.limpu.component.web.BaseWebSource
import cn.limpu.hita.data.model.GitHubRelease
import cn.limpu.hita.data.source.web.service.GitHubService

class GitHubWebSource(context: Context) : BaseWebSource<GitHubService>(
    context,
    baseUrl = "https://api.github.com/"
) {
    override fun getServiceClass(): Class<GitHubService> {
        return GitHubService::class.java
    }

    fun listReleases(owner: String, repo: String): LiveData<DataState<List<GitHubRelease>>> {
        return service.listReleases(owner, repo).map { input ->
            if (input != null) {
                return@map DataState(input)
            }
            DataState(DataState.STATE.FETCH_FAILED)
        }
    }

}
