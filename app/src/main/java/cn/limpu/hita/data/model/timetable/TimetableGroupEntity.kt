package cn.limpu.hita.data.model.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 组合课表：多份「我的课表」的命名分组，主课表按组同屏显示。
 * 子课表通过 [Timetable.groupId] 归属；组内最后一份子课表移出或删除后组自动删除。
 */
@Entity(tableName = "timetable_group")
class TimetableGroupEntity {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var createdAt: Long = System.currentTimeMillis()
}
