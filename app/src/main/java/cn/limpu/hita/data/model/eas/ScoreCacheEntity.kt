package cn.limpu.hita.data.model.eas

import androidx.room.Entity
import androidx.room.Index

/** A score query is cached as a whole so its list and official summary stay in sync. */
@Entity(
    tableName = "score_cache",
    primaryKeys = ["ownerKey", "termYearCode", "termTermCode", "testType"],
    indices = [Index(value = ["ownerKey", "cachedAt"])]
)
data class ScoreCacheEntity(
    val ownerKey: String,
    val termYearCode: String,
    val termTermCode: String,
    val testType: String,
    val scoresJson: String,
    val summaryJson: String?,
    val cachedAt: Long,
)

/** Cached term metadata lets the score screen remain navigable when the EAS session expires. */
@Entity(
    tableName = "score_term_cache",
    primaryKeys = ["ownerKey", "termYearCode", "termTermCode"],
    indices = [Index(value = ["ownerKey", "cachedAt"])]
)
data class ScoreTermCacheEntity(
    val ownerKey: String,
    val termYearCode: String,
    val yearName: String,
    val termTermCode: String,
    val termName: String,
    val isCurrent: Boolean,
    val cachedAt: Long,
)

/** Cached Shenzhen grade-page payloads, including personal component scores. */
@Entity(
    tableName = "score_detail_cache",
    primaryKeys = ["ownerKey", "cacheKey"],
    indices = [Index(value = ["ownerKey", "cachedAt"])]
)
data class ScoreDetailCacheEntity(
    val ownerKey: String,
    val cacheKey: String,
    val payloadJson: String,
    val cachedAt: Long,
)
