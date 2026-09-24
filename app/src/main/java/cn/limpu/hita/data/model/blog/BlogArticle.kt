package cn.limpu.hita.data.model.blog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blog_article",
    indices = [Index("pubDateMillis"), Index("path")]
)
data class BlogArticle(
    @PrimaryKey val guid: String,
    val title: String,
    val link: String,
    val pubDateMillis: Long,
    val description: String,
    val htmlContent: String,
    val path: String,
)
