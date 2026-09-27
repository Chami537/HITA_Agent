package cn.limpu.hita.ui.main.blog

import androidx.lifecycle.ViewModel
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.repository.CampusNoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CampusNoticeViewModel @Inject constructor(
    private val repository: CampusNoticeRepository,
) : ViewModel() {
    val refreshing = repository.refreshing
    val syncError = repository.syncError

    fun noticesFor(campus: EASToken.Campus) = repository.observeNotices(campus)

    fun syncOnPageOpen(campus: EASToken.Campus) = repository.syncOnPageOpen(campus)

    fun refresh(campus: EASToken.Campus) = repository.refresh(campus, force = true)
}
