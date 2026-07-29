package cn.limpu.hita.data.source.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.limpu.hita.data.model.eas.ScoreCacheEntity
import cn.limpu.hita.data.model.eas.ScoreDetailCacheEntity
import cn.limpu.hita.data.model.eas.ScoreTermCacheEntity

@Dao
interface ScoreCacheDao {

    @Query(
        "SELECT * FROM score_cache WHERE ownerKey = :ownerKey " +
            "AND termYearCode = :termYearCode AND termTermCode = :termTermCode " +
            "AND testType = :testType LIMIT 1"
    )
    fun getScoreSync(
        ownerKey: String,
        termYearCode: String,
        termTermCode: String,
        testType: String,
    ): ScoreCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveScoreSync(entity: ScoreCacheEntity)

    @Query("SELECT * FROM score_term_cache WHERE ownerKey = :ownerKey ORDER BY termYearCode DESC, termTermCode DESC")
    fun getTermsSync(ownerKey: String): List<ScoreTermCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveTermsSync(entities: List<ScoreTermCacheEntity>)

    @Query("SELECT * FROM score_detail_cache WHERE ownerKey = :ownerKey AND cacheKey = :cacheKey LIMIT 1")
    fun getDetailSync(ownerKey: String, cacheKey: String): ScoreDetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveDetailSync(entity: ScoreDetailCacheEntity)

    @Query("DELETE FROM score_cache WHERE ownerKey = :ownerKey")
    fun deleteScoresByOwnerSync(ownerKey: String)

    @Query("DELETE FROM score_term_cache WHERE ownerKey = :ownerKey")
    fun deleteTermsByOwnerSync(ownerKey: String)

    @Query("DELETE FROM score_detail_cache WHERE ownerKey = :ownerKey")
    fun deleteDetailsByOwnerSync(ownerKey: String)

    @Query("DELETE FROM score_cache WHERE cachedAt < :beforeTimestamp")
    fun deleteOldScoresSync(beforeTimestamp: Long)

    @Query("DELETE FROM score_term_cache WHERE cachedAt < :beforeTimestamp")
    fun deleteOldTermsSync(beforeTimestamp: Long)

    @Query("DELETE FROM score_detail_cache WHERE cachedAt < :beforeTimestamp")
    fun deleteOldDetailsSync(beforeTimestamp: Long)
}
