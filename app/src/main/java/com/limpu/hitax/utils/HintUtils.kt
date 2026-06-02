package com.limpu.hitax.utils

import android.content.Context
import com.limpu.hitax.data.model.timetable.EventItem

object HintUtils {
    const val HINT_PULL_DOWN = "pull_down"

    fun getHints(@Suppress("UNUSED_PARAMETER") context: Context): List<EventItem> {
        return emptyList()
    }

    fun clickHint(context: Context, hint: EventItem) {
        val sp = context.getSharedPreferences("hint", Context.MODE_PRIVATE)
        sp.edit().putBoolean(hint.id, true).apply()
    }
}
