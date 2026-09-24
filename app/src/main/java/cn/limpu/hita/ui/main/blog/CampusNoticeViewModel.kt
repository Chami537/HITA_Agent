package cn.limpu.hita.ui.main.blog

import androidx.lifecycle.ViewModel
import cn.limpu.hita.data.repository.CampusNoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CampusNoticeViewModel @Inject constructor(
    private val repository: CampusNoticeRepository,
) : ViewModel() {
    val notices = repository.observeNotices()
    val refreshing = repository.refreshing
    val syncError = repository.syncError

    fun syncOnPageOpen() = repository.syncOnPageOpen()

    fun refresh() = repository.refresh(force = true)
}
