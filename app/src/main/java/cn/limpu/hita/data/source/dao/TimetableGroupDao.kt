package cn.limpu.hita.data.source.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.TimetableGroupEntity

data class TimetableWithDisplayGroup(
    @Embedded val timetable: Timetable,
    @Relation(parentColumn = "groupId", entityColumn = "id")
    val groups: List<TimetableGroupEntity>,
)

@Dao
interface TimetableGroupDao {
    /** Read both scope tables from one committed snapshot. Room observes the relation too. */
    @Transaction
    @Query("SELECT * FROM timetable ORDER BY startTime DESC")
    fun observeDisplayTimetables(): LiveData<List<TimetableWithDisplayGroup>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveGroupSync(group: TimetableGroupEntity)

    @Query("SELECT * FROM timetable_group ORDER BY createdAt ASC")
    fun getGroups(): LiveData<List<TimetableGroupEntity>>

    @Query("SELECT * FROM timetable_group ORDER BY createdAt ASC")
    fun getGroupsSync(): List<TimetableGroupEntity>

    @Query("SELECT * FROM timetable_group WHERE id = :id")
    fun getGroupByIdSync(id: String): TimetableGroupEntity?

    @Query("UPDATE timetable_group SET name = :name WHERE id = :id")
    fun renameGroupSync(id: String, name: String): Int

    /** 组内成员数；空组由 repository 层删除，这里只读。 */
    @Query("SELECT COUNT(*) FROM timetable WHERE groupId = :groupId")
    fun countMembersSync(groupId: String): Int

    @Query("SELECT * FROM timetable WHERE groupId = :groupId ORDER BY startTime ASC")
    fun getMembersSync(groupId: String): List<cn.limpu.hita.data.model.timetable.Timetable>

    @Query("UPDATE timetable SET groupId = :groupId WHERE id = :timetableId")
    fun setTimetableGroupSync(timetableId: String, groupId: String?)

    @Query("UPDATE timetable SET groupId = NULL WHERE groupId = :groupId")
    fun clearGroupMembersSync(groupId: String)

    @Delete
    fun deleteGroupSync(group: TimetableGroupEntity)

    @Query("DELETE FROM timetable_group WHERE id = :groupId")
    fun deleteGroupByIdSync(groupId: String)

    /** 生成默认组名用：已有的「组合课表」系名字。 */
    @Query("SELECT name FROM timetable_group WHERE name LIKE :prefix || '%'")
    fun getGroupNamesWithPrefixSync(prefix: String): List<String>
}
