package cn.limpu.hita.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.TermSubject
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.TimetableGroupEntity
import cn.limpu.hita.data.source.preference.EasPreferenceSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.sql.Timestamp

@RunWith(AndroidJUnit4::class)
class TimetableSourceDisplayMetadataTest {
    private class IsolatedApplication(context: Context, private val directory: File) : Application() {
        init { attachBaseContext(context) }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }

    private fun fixture(action: (Application, AppDatabase, Timetable) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "source-display-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val application = IsolatedApplication(context, directory)
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        try {
            val table = Timetable().apply {
                id = "table"; name = "源端名称"; code = "2026-2027-1"
                startTime = Timestamp(1788105600000L); endTime = Timestamp(startTime.time + 120 * 86400000L)
            }
            db.timetableDao().saveTimetableSync(table)
            db.subjectDao().saveSubjectSync(TermSubject().apply { id = "subject"; timetableId = table.id; name = "旧课程" })
            db.eventItemDao().insertEventSync(EventItem().apply {
                id = "event"; timetableId = table.id; subjectId = "subject"; name = "旧课程"
                from = Timestamp(table.startTime.time + 8 * 3600000L); to = Timestamp(from.time + 3600000L)
            })
            action(application, db, table)
        } finally { db.close(); directory.deleteRecursively() }
    }

    private fun renameAndGroup(db: AppDatabase, table: Timetable) {
        db.timetableGroupDao().saveGroupSync(TimetableGroupEntity().apply { id = "group"; name = "我的组合" })
        table.name = "本地自定义名称"; table.groupId = "group"
        db.timetableDao().saveTimetableSync(table)
    }

    @Test fun restoringSourceSnapshotRetainsCurrentLocalNameAndGroup() = fixture { app, db, table ->
        val store = TimetableSnapshotStore(app, db)
        val term = TermItem("2026-2027", "2026-2027", "1", "秋季")
        val snapshot = requireNotNull(store.capture("owner", term, table, TimetableSnapshotKind.IMPORTED))
        renameAndGroup(db, table)
        val event = db.eventItemDao().getEventsOfTimetableSync(table.id).single().apply { name = "新课程" }
        db.eventItemDao().updateEventSync(event)
        store.restore("owner", snapshot.id)
        val restored = requireNotNull(db.timetableDao().getTimetableByIdSync(table.id))
        assertEquals("本地自定义名称", restored.name)
        assertEquals("group", restored.groupId)
        assertEquals(listOf("table"), db.timetableGroupDao().getMembersSync("group").map { it.id })
        assertEquals("旧课程", db.eventItemDao().getEventsOfTimetableSync(table.id).single().name)
    }

    @Test fun failedSourceRestoreRollsBackDeletedCoursesAndDisplayMetadata() = fixture { app, db, table ->
        val store = TimetableSnapshotStore(app, db)
        val snapshot = requireNotNull(store.capture("owner", TermItem("2026-2027", "2026-2027", "1", "秋季"),
            table, TimetableSnapshotKind.IMPORTED))
        renameAndGroup(db, table)
        val event = db.eventItemDao().getEventsOfTimetableSync(table.id).single().apply { name = "当前课程" }
        db.eventItemDao().updateEventSync(event)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON events BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END")
        try { store.restore("owner", snapshot.id); fail("must reach the injected write failure") }
        catch (failure: android.database.sqlite.SQLiteException) { assertTrue(failure.message.orEmpty().contains("injected restore failure")) }
        assertEquals("当前课程", db.eventItemDao().getEventsOfTimetableSync(table.id).single().name)
        assertEquals("group", db.timetableDao().getTimetableByIdSync(table.id)!!.groupId)
        assertEquals("本地自定义名称", db.timetableDao().getTimetableByIdSync(table.id)!!.name)
        assertEquals("旧课程", db.subjectDao().getSubjectsSync(table.id).single().name)
    }

    @Test fun heldSourceAdoptionDoesNotRenameOrUngroupPersonalTimetable() = fixture { app, db, table ->
        renameAndGroup(db, table)
        val changes = TimetableChangeStore(app)
        changes.recordHeldBatch(TimetableHeldBatch(1, table.code.orEmpty(), table.id, "源端不同名称", table.code.orEmpty(),
            table.startTime.time, table.scheduleStructure, 1, 1, 1,
            listOf(MergeCourse("subject", "源端课程", null, listOf(MergeLesson("源端课程", "A101", "老师",
                table.startTime.time + 9 * 3600000L, table.startTime.time + 10 * 3600000L, 1, 1))))))
        TimetableSourceApplier(app, EasPreferenceSource(app), changes, TimetableMutationLock(), db).adoptHeldBatch()
        val adopted = requireNotNull(db.timetableDao().getTimetableByIdSync(table.id))
        assertEquals("本地自定义名称", adopted.name)
        assertEquals("group", adopted.groupId)
        assertEquals("源端课程", db.eventItemDao().getEventsOfTimetableSync(table.id).single().name)
        assertNull(changes.currentState().heldBatch)
    }
}
