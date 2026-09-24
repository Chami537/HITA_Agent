package cn.limpu.hita.ui.main.blog

import androidx.lifecycle.ViewModel
import cn.limpu.hita.data.repository.BlogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BlogViewModel @Inject constructor(
    private val blogRepository: BlogRepository,
) : ViewModel() {
    val articles = blogRepository.observeArticles()
    val unreadGuids = blogRepository.unreadGuidsLiveData
    val refreshing = blogRepository.refreshingLiveData
    val syncError = blogRepository.syncErrorLiveData

    fun refresh() = blogRepository.refresh(force = true)

    fun markTabOpened() = blogRepository.markTabOpened()
}
