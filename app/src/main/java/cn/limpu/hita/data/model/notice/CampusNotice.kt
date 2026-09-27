package cn.limpu.hita.data.model.notice

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "campus_notice",
    primaryKeys = ["campus", "id"],
    indices = [Index("pubDateMillis")],
)
data class CampusNotice(
    val campus: String,
    val id: String,
    val title: String,
    val url: String,
    val pubDateMillis: Long,
)
