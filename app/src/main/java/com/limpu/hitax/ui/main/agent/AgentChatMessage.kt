package com.limpu.hitax.ui.main.agent

import com.limpu.hitax.data.model.resource.AgentResourceCard

data class AgentChatMessage(
    val role: Role,
    val text: String,
    val thinking: String? = null,
    val isThinkingExpanded: Boolean = false,
    val areResourceCardsExpanded: Boolean = true,
    val isPlaceholder: Boolean = false,
    val resourceCards: List<AgentResourceCard> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Role {
        USER,
        ASSISTANT,
        TRACE,
    }
}
