package cn.limpu.hita.data.source.preference

import android.content.Context
import android.content.SharedPreferences
import cn.limpu.hita.data.model.eas.ExamItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val SP_NAME = "exam_memos"
private const val KEY_ITEMS = "items_v1"

/** Local-only exam records. Remote EAS exam payloads are never written here. */
class ExamMemoStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<ExamItem> = ExamMemoCodec.decode(preferences.getString(KEY_ITEMS, null))

    @Synchronized
    fun upsert(item: ExamItem): ExamItem {
        require(item.isMemo()) { "考试备忘录缺少本地 ID" }
        require(!item.courseName.isNullOrBlank()) { "请填写考试或课程名称" }
        val items = getAll().toMutableList()
        val index = items.indexOfFirst { it.memoId == item.memoId }
        if (index >= 0) items[index] = item else items.add(item)
        persist(items)
        return item
    }

    @Synchronized
    fun delete(memoId: String) {
        val normalized = memoId.trim()
        if (normalized.isEmpty()) return
        persist(getAll().filterNot { it.memoId == normalized })
    }

    private fun persist(items: List<ExamItem>) {
        if (items.isEmpty()) {
            preferences.edit().remove(KEY_ITEMS).apply()
        } else {
            preferences.edit().putString(KEY_ITEMS, ExamMemoCodec.encode(items)).apply()
        }
    }
}

internal object ExamMemoCodec {
    private val listType = object : TypeToken<List<ExamItem>>() {}.type

    fun encode(items: List<ExamItem>): String = Gson().toJson(items, listType)

    fun decode(raw: String?): List<ExamItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Gson().fromJson<List<ExamItem>>(raw, listType).orEmpty()
                .filter { it.isMemo() && !it.courseName.isNullOrBlank() }
        }.getOrDefault(emptyList())
    }

    fun merge(remote: List<ExamItem>, memos: List<ExamItem>): List<ExamItem> =
        (remote.filterNot(ExamItem::isMemo) + memos.filter(ExamItem::isMemo))
            .sortedWith(
                compareBy<ExamItem>(
                    { it.examDate.isNullOrBlank() },
                    { it.examDate.orEmpty() },
                    { it.examTime.orEmpty() },
                    { it.courseName.orEmpty() }
                )
            )
}
