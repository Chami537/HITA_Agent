package com.limpu.hitax.data.source.preference

import android.content.Context
import android.content.SharedPreferences
import com.limpu.hitax.data.model.timetable.TermSubject

private const val SP_NAME = "credit_goals"

class CreditGoalStore constructor(context: Context) {
    private val preference: SharedPreferences =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getGoal(type: TermSubject.TYPE): Float? {
        val v = preference.getFloat(type.name, -1f)
        return if (v < 0) null else v
    }

    fun setGoal(type: TermSubject.TYPE, credits: Float) {
        preference.edit().putFloat(type.name, credits).apply()
    }

    fun removeGoal(type: TermSubject.TYPE) {
        preference.edit().remove(type.name).apply()
    }

    fun getAllGoals(): Map<TermSubject.TYPE, Float> {
        val result = mutableMapOf<TermSubject.TYPE, Float>()
        for (type in TermSubject.TYPE.entries) {
            if (type == TermSubject.TYPE.TAG) continue
            getGoal(type)?.let { result[type] = it }
        }
        return result
    }
}
