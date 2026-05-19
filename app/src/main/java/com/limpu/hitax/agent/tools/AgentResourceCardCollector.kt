package com.limpu.hitax.agent.tools

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.limpu.hitax.data.model.resource.AgentResourceCard

object AgentResourceCardCollector {
    private val gson = Gson()
    private val cardListType = object : TypeToken<List<AgentResourceCard>>() {}.type

    fun toJson(cards: List<AgentResourceCard>): String {
        return if (cards.isEmpty()) "" else gson.toJson(cards)
    }

    fun fromJson(json: String?): List<AgentResourceCard> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<AgentResourceCard>>(json, cardListType).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
