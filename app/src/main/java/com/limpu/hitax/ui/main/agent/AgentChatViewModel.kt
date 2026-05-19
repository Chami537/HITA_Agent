package com.limpu.hitax.ui.main.agent

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.llm.ChatMessage
import com.limpu.hitax.agent.llm.LlmChatResult
import com.limpu.hitax.agent.llm.LlmChatService
import com.limpu.hitax.agent.llm.chatWithAttachment
import com.limpu.hitax.agent.tools.AgentResourceCardCollector
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.chat.ChatMessageEntity
import com.limpu.hitax.data.model.chat.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

internal fun nextSessionIdAfterDeletion(
    deletedSessionId: String,
    currentSessionId: String?,
    remainingLatestSessionId: String?,
): String? {
    return if (deletedSessionId == currentSessionId) remainingLatestSessionId else currentSessionId
}

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    private val db = AppDatabase.getDatabase(application)
    private val sessionDao = db.chatSessionDao()
    private val messageDao = db.chatMessageDao()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val _messages = MutableLiveData<List<AgentChatMessage>>(emptyList())
    val messages: LiveData<List<AgentChatMessage>> = _messages

    private val _status = MutableLiveData("")
    val status: LiveData<String> = _status

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _pendingAttachment = MutableLiveData<Uri?>(null)
    val pendingAttachment: LiveData<Uri?> = _pendingAttachment

    val sessions = sessionDao.getAll()

    @Volatile
    private var messageList: List<AgentChatMessage> = emptyList()

    // 按 session 隔离的状态
    private val sessionChatHistories = mutableMapOf<String, MutableList<ChatMessage>>()
    private val sessionPlaceholders = mutableMapOf<String, AgentChatMessage>()
    private val sessionLoadingStates = mutableMapOf<String, Boolean>()

    @Volatile
    var currentSessionId: String? = null
        private set

    private fun publishMessages() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _messages.value = messageList
        } else {
            _messages.postValue(messageList)
        }
    }

    private fun publishStatus(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _status.value = text
        } else {
            _status.postValue(text)
        }
    }

    private fun publishLoading(loading: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _isLoading.value = loading
        } else {
            _isLoading.postValue(loading)
        }
    }

    fun addMessage(message: AgentChatMessage) {
        val sid = currentSessionId ?: return
        synchronized(this) {
            messageList = messageList + message
            publishMessages()
        }
        ioExecutor.execute {
            messageDao.save(
                ChatMessageEntity(
                    sessionId = sid,
                    role = message.role.name,
                    text = message.text,
                    resourceCardsJson = AgentResourceCardCollector.toJson(message.resourceCards),
                    timestampMs = message.timestampMs,
                )
            )
            sessionDao.updateTitle(sid, deriveTitle(), System.currentTimeMillis())
        }
    }

    fun updateOrCreatePlaceholder(text: String, thinking: String? = null, targetSessionId: String? = null) {
        val sid = targetSessionId ?: currentSessionId ?: return
        if (sid != currentSessionId) return
        synchronized(this) {
            val existing = sessionPlaceholders[sid]
            if (existing != null) {
                val idx = messageList.indexOf(existing)
                if (idx >= 0) {
                    val updated = existing.copy(
                        text = text,
                        thinking = thinking ?: existing.thinking,
                    )
                    messageList = messageList.toMutableList().apply { set(idx, updated) }
                    sessionPlaceholders[sid] = updated
                    publishMessages()
                    return
                }
            }
            val newPlaceholder = AgentChatMessage(
                role = AgentChatMessage.Role.ASSISTANT,
                text = text,
                thinking = thinking,
                isPlaceholder = true,
            )
            sessionPlaceholders[sid] = newPlaceholder
            messageList = messageList + newPlaceholder
            publishMessages()
        }
    }

    fun replacePlaceholder(finalMessage: AgentChatMessage, targetSessionId: String? = null) {
        val sid = targetSessionId ?: currentSessionId ?: return
        if (sid == currentSessionId) {
            synchronized(this) {
                val existing = sessionPlaceholders[sid]
                if (existing != null) {
                    val idx = messageList.indexOf(existing)
                    if (idx >= 0) {
                        messageList = messageList.toMutableList().apply { set(idx, finalMessage) }
                    } else {
                        messageList = messageList + finalMessage
                    }
                    sessionPlaceholders.remove(sid)
                    publishMessages()
                } else {
                    messageList = messageList + finalMessage
                    sessionPlaceholders.remove(sid)
                    publishMessages()
                }
            }
        } else {
            sessionPlaceholders.remove(sid)
        }
        ioExecutor.execute {
            messageDao.save(
                ChatMessageEntity(
                    sessionId = sid,
                    role = finalMessage.role.name,
                    text = finalMessage.text,
                    resourceCardsJson = AgentResourceCardCollector.toJson(finalMessage.resourceCards),
                    timestampMs = finalMessage.timestampMs,
                )
            )
            sessionDao.updateTitle(sid, deriveTitle(), System.currentTimeMillis())
        }
    }

    private fun deriveTitle(): String {
        val firstUser = messageList.firstOrNull { it.role == AgentChatMessage.Role.USER }
        return firstUser?.text?.take(20)?.plus(if ((firstUser.text.length) > 20) "…" else "")
            ?: "新对话"
    }

    fun setStatus(text: String) {
        publishStatus(text)
    }

    fun setLoading(loading: Boolean) {
        val sid = currentSessionId ?: return
        sessionLoadingStates[sid] = loading
        publishLoading(loading)
    }

    fun setPendingAttachment(uri: Uri?) {
        _pendingAttachment.value = uri
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun createNewSession() {
        val session = ChatSession()
        currentSessionId = session.id
        sessionChatHistories[session.id] = mutableListOf()
        sessionPlaceholders.remove(session.id)
        sessionLoadingStates[session.id] = false
        messageList = emptyList()
        publishMessages()
        publishStatus("")
        publishLoading(false)
        ioExecutor.execute { sessionDao.save(session) }
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == currentSessionId) return
        currentSessionId = sessionId
        sessionChatHistories.getOrPut(sessionId) { mutableListOf() }
        ioExecutor.execute {
            val entities = messageDao.getBySessionSync(sessionId)
            val restored = entities.map { e ->
                AgentChatMessage(
                    role = AgentChatMessage.Role.valueOf(e.role),
                    text = e.text,
                    resourceCards = AgentResourceCardCollector.fromJson(e.resourceCardsJson),
                    timestampMs = e.timestampMs,
                )
            }
            val restoredHistory = restored.filter {
                it.role == AgentChatMessage.Role.USER || it.role == AgentChatMessage.Role.ASSISTANT
            }.map {
                ChatMessage(
                    role = if (it.role == AgentChatMessage.Role.USER) "user" else "assistant",
                    content = it.text,
                )
            }
            synchronized(this) {
                messageList = restored
                sessionChatHistories[sessionId] = restoredHistory.toMutableList()
                sessionPlaceholders.remove(sessionId)
            }
            _messages.postValue(restored)
            _isLoading.postValue(sessionLoadingStates[sessionId] ?: false)
            _status.postValue("")
        }
    }

    fun deleteSession(session: ChatSession) {
        ioExecutor.execute {
            messageDao.deleteBySession(session.id)
            sessionDao.delete(session)
            sessionChatHistories.remove(session.id)
            sessionPlaceholders.remove(session.id)
            sessionLoadingStates.remove(session.id)
            val nextSessionId = nextSessionIdAfterDeletion(
                deletedSessionId = session.id,
                currentSessionId = currentSessionId,
                remainingLatestSessionId = sessionDao.getLatest()?.id,
            )
            when {
                nextSessionId == null -> createNewSession()
                nextSessionId != currentSessionId -> switchToSession(nextSessionId)
            }
        }
    }

    fun ensureSession() {
        if (currentSessionId != null) return
        ioExecutor.execute {
            val latest = sessionDao.getLatest()
            if (latest != null) {
                switchToSession(latest.id)
            } else {
                createNewSession()
            }
        }
    }

    fun sendToLlm(
        text: String,
        agentProvider: com.limpu.hitax.agent.core.AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    ) {
        ensureSession()
        val sid = currentSessionId ?: return
        val history = sessionChatHistories.getOrPut(sid) { mutableListOf() }
        history.add(ChatMessage(role = "user", content = text))

        viewModelScope.launch {
            sessionLoadingStates[sid] = true
            if (sid == currentSessionId) publishLoading(true)

            LlmChatService.chat(
                history = history.toList(),
                timetableId = null,
                application = application,
                agentProvider = agentProvider,
                onTrace = { trace ->
                    if (currentSessionId != sid) return@chat
                    val statusText = when (trace.stage) {
                        "react_start" -> "正在分析您的问题…"
                        "react_step" -> {
                            val action = trace.message.substringAfter("→ ", "").trim()
                            when {
                                action.contains("get_timetable") -> "正在查询课表…"
                                action.contains("search_timetable") -> "正在搜索课表事件…"
                                action.contains("search_external_resource") -> "正在搜索课程资料…"
                                action.contains("search_course") -> "正在搜索课程信息…"
                                action.contains("get_course_detail") -> "正在获取课程详情…"
                                action.contains("search_teacher") -> "正在搜索教师信息…"
                                action.contains("web_search") -> "正在搜索网页…"
                                action.contains("brave_answer") -> "正在搜索答案…"
                                action.contains("rag_search") -> "正在搜索知识库…"
                                action.contains("crawl_page") -> "正在爬取网页…"
                                action.contains("crawl_site") -> "正在爬取网站…"
                                action.contains("submit_review") -> "正在提交评价…"
                                action.contains("add_activity") -> "正在添加活动…"
                                else -> "正在思考…"
                            }
                        }
                        else -> "正在处理…"
                    }
                    val currentThinking = if (trace.stage == "react_step") {
                        trace.payload.ifBlank { trace.message }
                    } else {
                        null
                    }
                    updateOrCreatePlaceholder(statusText, currentThinking, targetSessionId = sid)
                },
                onResult = { result ->
                    sessionLoadingStates[sid] = false
                    if (sid == currentSessionId) publishLoading(false)
                    when (result) {
                        is LlmChatResult.Success -> {
                            history.add(ChatMessage(role = "assistant", content = result.text))
                            if (currentSessionId == sid) {
                                replacePlaceholder(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = result.text,
                                    thinking = result.thinking,
                                    resourceCards = result.resourceCards,
                                ), targetSessionId = sid)
                                setStatus("完成")
                            }
                        }
                        is LlmChatResult.Error -> {
                            if (currentSessionId == sid) {
                                replacePlaceholder(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = "操作失败: ${result.error}",
                                ), targetSessionId = sid)
                                setStatus("失败")
                            }
                        }
                    }
                },
            )
        }
    }

    fun sendToLlmWithAttachment(
        text: String,
        fileName: String,
        base64Content: String,
        mimeType: String,
        agentProvider: com.limpu.hitax.agent.core.AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    ) {
        ensureSession()
        val sid = currentSessionId ?: return
        val history = sessionChatHistories.getOrPut(sid) { mutableListOf() }

        val userMessage = "$text\n\n[文件: $fileName]"
        history.add(ChatMessage(role = "user", content = userMessage))

        viewModelScope.launch {
            sessionLoadingStates[sid] = true
            if (sid == currentSessionId) publishLoading(true)

            LlmChatService.chatWithAttachment(
                history = history.toList(),
                attachmentBase64 = base64Content,
                attachmentMimeType = mimeType,
                timetableId = null,
                application = application,
                agentProvider = agentProvider,
                onTrace = { trace: AgentTraceEvent ->
                    if (currentSessionId != sid) return@chatWithAttachment
                    val statusText = when (trace.stage) {
                        "react_start" -> "正在分析附件…"
                        "react_step" -> {
                            val action = trace.message.substringAfter("→ ", "").trim()
                            when {
                                action.contains("get_timetable") -> "正在查询课表…"
                                action.contains("search_external_resource") -> "正在搜索课程资料…"
                                action.contains("search_course") -> "正在搜索课程信息…"
                                action.contains("get_course_detail") -> "正在获取课程详情…"
                                action.contains("search_teacher") -> "正在搜索教师信息…"
                                action.contains("web_search") -> "正在搜索网页…"
                                action.contains("brave_answer") -> "正在搜索答案…"
                                action.contains("rag_search") -> "正在搜索知识库…"
                                else -> "正在思考…"
                            }
                        }
                        else -> "正在处理…"
                    }
                    val currentThinking = if (trace.stage == "react_step") {
                        trace.payload.ifBlank { trace.message }
                    } else {
                        null
                    }
                    updateOrCreatePlaceholder(statusText, currentThinking, targetSessionId = sid)
                },
                onResult = { result: LlmChatResult ->
                    sessionLoadingStates[sid] = false
                    if (sid == currentSessionId) publishLoading(false)
                    when (result) {
                        is LlmChatResult.Success -> {
                            if (!history.any { it.role == "assistant" && it.content == result.text }) {
                                history.add(ChatMessage(role = "assistant", content = result.text))
                            }
                            if (currentSessionId == sid) {
                                replacePlaceholder(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = result.text,
                                    thinking = result.thinking,
                                    resourceCards = result.resourceCards,
                                ), targetSessionId = sid)
                            }
                        }
                        is LlmChatResult.Error -> {
                            if (currentSessionId == sid) {
                                replacePlaceholder(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = "抱歉，处理过程中出现错误：${result.error}"
                                ), targetSessionId = sid)
                            }
                        }
                    }
                },
            )
        }
    }
}
