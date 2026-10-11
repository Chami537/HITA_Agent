package cn.limpu.hita.data.repository

import androidx.lifecycle.LiveData
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.feature.timetableshare.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.Callable
import javax.inject.Inject

class FriendTimetableRepository @Inject constructor(private val db: AppDatabase) {
    private val friends = db.friendTimetableDao()
    private val codec = TimetableShareCodec()

    fun observeFriends(): LiveData<List<FriendTimetableEntity>> = friends.observeAll()
    fun observeFriendsFlow() = friends.observeAllFlow()
    fun observeFriend(shareId: String): LiveData<FriendTimetableEntity?> = friends.observe(shareId)

    suspend fun preview(message: String): FriendImportPreview = withContext(Dispatchers.IO) {
        val snapshot = codec.decodeMessage(message)
        makePreview(snapshot, codec.digest(snapshot), friends.getSync(snapshot.shareId))
    }

    suspend fun confirm(preview: FriendImportPreview): FriendSaveResult = withContext(Dispatchers.IO) {
        // Revalidate even caller-created previews, and store only canonical complete snapshots.
        val json = codec.canonicalJson(preview.snapshot)
        val snapshot = codec.decodeJson(json)
        val digest = codec.digest(snapshot)
        val context = currentCoroutineContext()
        context.ensureActive()
        db.runInTransaction(Callable<FriendSaveResult> {
            val current = friends.getSync(snapshot.shareId)
            if (current?.contentDigest != preview.expectedDigest) {
                return@Callable FriendSaveResult.NeedsConfirmation(makePreview(snapshot, digest, current))
            }
            val currentValid = current?.let(::isValidSnapshot) ?: true
            if (!currentValid && preview.kind == ImportKind.SAME) {
                return@Callable FriendSaveResult.NeedsConfirmation(makePreview(snapshot, digest, current))
            }
            if (currentValid && current?.contentDigest == digest) {
                return@Callable FriendSaveResult.Unchanged(snapshot.shareId)
            }
            val now = System.currentTimeMillis()
            val entity = FriendTimetableEntity(snapshot.shareId, snapshot.nickname, current?.remark,
                snapshot.term.termName, snapshot.term.startMillis, snapshot.term.endMillis,
                snapshot.mode.name, json, digest, current?.importedAt ?: now, now)
            context.ensureActive()
            if (current == null) friends.insertSync(entity) else friends.updateSync(entity)
            context.ensureActive()
            FriendSaveResult.Saved(snapshot.shareId)
        })
    }

    suspend fun rename(shareId: String, remark: String?) = withContext(Dispatchers.IO) {
        friends.renameSync(shareId, remark)
    }

    /** Local ICS snapshots still require an explicit confirmation before replacing a friend. */
    suspend fun importSnapshot(snapshot: SharedTimetable): FriendSaveResult = withContext(Dispatchers.IO) {
        val canonical = codec.decodeJson(codec.canonicalJson(snapshot))
        val current = friends.getSync(canonical.shareId)
        val preview = makePreview(canonical, codec.digest(canonical), current)
        if (preview.kind == ImportKind.UPDATE) FriendSaveResult.NeedsConfirmation(preview)
        else confirm(preview)
    }

    suspend fun delete(shareId: String) = withContext(Dispatchers.IO) { friends.deleteSync(shareId) }

    suspend fun readSnapshot(row: FriendTimetableEntity): SharedTimetable = withContext(Dispatchers.IO) {
        decodeSnapshot(row)
    }

    private fun decodeSnapshot(row: FriendTimetableEntity): SharedTimetable =
        codec.decodeJson(row.snapshotJson).also {
            if (!row.matchesSnapshot(it, codec)) throw ShareFormatException(ShareError.INVALID_FIELDS)
        }

    private fun isValidSnapshot(row: FriendTimetableEntity): Boolean = try {
        decodeSnapshot(row)
        true
    } catch (_: ShareFormatException) {
        false
    }

    /** 重新导出口令：原样重编码已存快照，保留 shareId 与昵称；键为展示名（备注优先）。 */
    suspend fun exportTokens(shareIds: List<String>): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        shareIds.map { shareId ->
            val entity = friends.getSync(shareId) ?: throw IllegalStateException("friend $shareId missing")
            val displayName = entity.remark?.takeIf { it.isNotBlank() } ?: entity.nickname
            displayName to codec.encode(decodeSnapshot(entity))
        }
    }

    private fun makePreview(snapshot: SharedTimetable, digest: String, current: FriendTimetableEntity?) =
        FriendImportPreview(snapshot, if (current != null && !isValidSnapshot(current)) ImportKind.UPDATE
            else FriendImportPolicy.classify(digest, current?.contentDigest),
            current?.contentDigest, current?.remark)
}

internal fun FriendTimetableEntity.matchesSnapshot(snapshot: SharedTimetable, codec: TimetableShareCodec): Boolean =
    shareId == snapshot.shareId && nickname == snapshot.nickname && mode == snapshot.mode.name &&
        termName == snapshot.term.termName && startMillis == snapshot.term.startMillis &&
        endMillis == snapshot.term.endMillis && contentDigest == codec.digest(snapshot)
