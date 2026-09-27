package cn.limpu.hita.data.source.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cn.limpu.hita.data.model.notice.CampusNotice

@Dao
interface CampusNoticeDao {
    @Query("SELECT * FROM campus_notice WHERE campus = :campus ORDER BY pubDateMillis DESC, title ASC LIMIT 30")
    fun observeLatest(campus: String): LiveData<List<CampusNotice>>

    @Query("SELECT * FROM campus_notice WHERE campus = :campus ORDER BY pubDateMillis DESC, title ASC LIMIT 30")
    fun getLatest(campus: String): List<CampusNotice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(notices: List<CampusNotice>)

    @Query("DELETE FROM campus_notice WHERE campus = :campus")
    fun deleteAll(campus: String)

    @Transaction
    fun replaceAll(campus: String, notices: List<CampusNotice>) {
        deleteAll(campus)
        if (notices.isNotEmpty()) {
            upsertAll(notices.take(30))
        }
    }
}
