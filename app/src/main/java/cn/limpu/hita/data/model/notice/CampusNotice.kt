package cn.limpu.hita.data.model.notice

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "campus_notice",
    indices = [Index("pubDateMillis")],
)
data class CampusNotice(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val pubDateMillis: Long,
)
