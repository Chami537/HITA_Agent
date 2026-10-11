package cn.limpu.hita.data.timetableshare

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.limpu.hita.R
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.repository.FriendTimetableRepository
import cn.limpu.hita.data.repository.TimetableExportRepository
import cn.limpu.hita.feature.timetableshare.protocol.*
import cn.limpu.hita.ui.timetable.friend.FriendTimetableViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendImportConfirmationInstrumentedTest {
    @Test fun changedDuplicatePreviewRequiresReconfirmationBeforeNavigation() = runBlocking {
        checkStaleDuplicate(deleted = false)
    }

    @Test fun deletedDuplicatePreviewRequiresNewImportConfirmationBeforeNavigation() = runBlocking {
        checkStaleDuplicate(deleted = true)
    }

    private suspend fun checkStaleDuplicate(deleted: Boolean) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val repository = FriendTimetableRepository(db)
            val codec = TimetableShareCodec()
            val original = fullFixture()
            val token = codec.encode(original)
            repository.confirm(repository.preview(token))
            val model = withContext(Dispatchers.Main) {
                FriendTimetableViewModel(repository, TimetableExportRepository(db, application)).also {
                    store.put("friend", it)
                    it.showImport()
                    it.editInput(token)
                    it.parse()
                }
            }
            withContext(Dispatchers.Main) {
                withTimeout(10000) { while (model.form.loading) delay(10) }
                assertEquals(ImportKind.SAME, model.form.preview?.kind)
            }
            if (deleted) repository.delete(original.shareId)
            else repository.confirm(repository.preview(codec.encode(original.copy(nickname = "Updated"))))
            withContext(Dispatchers.Main) {
                model.confirm()
                withTimeout(10000) { while (model.form.saving) delay(10) }
                assertNull(model.openShareId)
                assertTrue(model.importOpen)
                assertEquals(if (deleted) ImportKind.NEW else ImportKind.UPDATE, model.form.preview?.kind)
                assertEquals(R.string.friend_import_changed, model.form.errorResource)
                assertTrue(model.form.canConfirm)
            }
            if (deleted) assertNull(db.friendTimetableDao().getSync(original.shareId))
            else assertEquals("Updated", db.friendTimetableDao().getSync(original.shareId)!!.nickname)
            withContext(Dispatchers.Main) {
                model.confirm()
                withTimeout(10000) { while (model.form.saving) delay(10) }
                assertFalse(model.importOpen)
                assertEquals(original.shareId, model.openShareId)
            }
            assertEquals(original.nickname, db.friendTimetableDao().getSync(original.shareId)!!.nickname)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
        }
    }
}
