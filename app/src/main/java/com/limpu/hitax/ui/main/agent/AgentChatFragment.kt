package com.limpu.hitax.ui.main.agent

import android.app.AlertDialog
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.viewModels
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentSession
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.model.chat.ChatSession
import com.limpu.hitax.databinding.FragmentAgentChatBinding
import com.limpu.hitax.ui.base.HiltBaseFragment
import com.limpu.hitax.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AgentChatFragment : HiltBaseFragment<FragmentAgentChatBinding>() {

    @Inject lateinit var agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>

    protected val viewModel: AgentChatViewModel by viewModels()

    override fun initViewBinding(): FragmentAgentChatBinding =
        FragmentAgentChatBinding.inflate(layoutInflater)
    private var agentSession: AgentSession<TimetableAgentInput, TimetableAgentOutput>? = null
    private lateinit var messageAdapter: AgentChatMessageAdapter
    private var sessionList: List<ChatSession> = emptyList()
    private var baseInputBottomMargin = 0
    private var baseMessageListBottomPadding = 0

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setPendingAttachment(it)
        }
    }

    override fun initViews(view: View) {
        viewModel.ensureSession()

        messageAdapter = AgentChatMessageAdapter()
        binding?.messageList?.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = messageAdapter
        }
        setupKeyboardInsets()

        binding?.sendButton?.setOnClickListener { sendMessage() }
        binding?.attachButton?.setOnClickListener { openFilePicker() }
        binding?.newSessionButton?.setOnClickListener { viewModel.createNewSession() }
        binding?.deleteSessionButton?.setOnClickListener { showDeleteSessionDialog() }

        binding?.inputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) { list ->
            messageAdapter.submitList(list)
            if (list.isNotEmpty()) {
                binding?.messageList?.scrollToPosition(list.size - 1)
            }
        }
        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            sessionList = sessions
            val names = sessions.map { it.title }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding?.sessionSpinner?.adapter = adapter
            val current = viewModel.currentSessionId
            val idx = sessions.indexOfFirst { it.id == current }.coerceAtLeast(0)
            binding?.sessionSpinner?.setSelection(idx, false)
        }
        binding?.sessionSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, position: Int, id: Long) {
                sessionList.getOrNull(position)?.let { viewModel.switchToSession(it.id) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        binding?.sessionSpinner?.setOnLongClickListener {
            val current = sessionList.getOrNull(binding?.sessionSpinner?.selectedItemPosition ?: 0)
            if (current != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除会话")
                    .setMessage("删除「${current.title}」的聊天记录？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteSession(current)
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            true
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding?.sendButton?.isEnabled = !loading
            binding?.inputField?.isEnabled = !loading
        }
        viewModel.pendingAttachment.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                val fileName = getFileName(uri)
                binding?.attachmentIndicator?.text = "📎 $fileName"
                binding?.attachmentIndicator?.visibility = View.VISIBLE
            } else {
                binding?.attachmentIndicator?.visibility = View.GONE
            }
        }
    }

    private fun setupKeyboardInsets() {
        val root = binding?.root ?: return
        val inputContainer = binding?.inputContainer ?: return
        val messageList = binding?.messageList ?: return
        val inputLayoutParams = inputContainer.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        baseInputBottomMargin = inputLayoutParams.bottomMargin
        baseMessageListBottomPadding = messageList.paddingBottom

        binding?.inputField?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                ViewCompat.requestApplyInsets(root)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            root.setPadding(
                root.paddingLeft,
                statusBarHeight,
                root.paddingRight,
                root.paddingBottom
            )

            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val rootLocation = IntArray(2)
            root.getLocationOnScreen(rootLocation)
            val rootBottomOnScreen = rootLocation[1] + root.height
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val keyboardOffset = if (imeVisible) {
                (rootBottomOnScreen - visibleFrame.bottom).coerceAtLeast(0)
            } else {
                0
            }

            inputLayoutParams.bottomMargin = baseInputBottomMargin + keyboardOffset
            inputContainer.layoutParams = inputLayoutParams
            messageList.setPadding(
                messageList.paddingLeft,
                messageList.paddingTop,
                messageList.paddingRight,
                baseMessageListBottomPadding + keyboardOffset
            )

            if (keyboardOffset > 0 && messageAdapter.itemCount > 0) {
                messageList.post { messageList.scrollToPosition(messageAdapter.itemCount - 1) }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun getFileName(uri: Uri): String {
        // 优先从 URI path 中提取真实文件名（避免显示名称被应用修改）
        val path = uri.path
        if (path != null && path.contains("/")) {
            val nameFromPath = path.substringAfterLast("/")
            // 如果路径中的文件名包含常见后缀，优先使用
            if (nameFromPath.contains(".") && !nameFromPath.endsWith(".mht")) {
                return nameFromPath
            }
        }

        // 其次尝试从 ContentResolver 查询显示名称
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val displayName = it.getString(nameIndex)
                    // 如果显示名称是 .mht 但包含 ".pdf"，尝试提取真实名称
                    if (displayName.endsWith(".mht", ignoreCase = true) && displayName.contains(".pdf", ignoreCase = true)) {
                        val pdfIndex = displayName.indexOf(".pdf", ignoreCase = true)
                        if (pdfIndex > 0) {
                            val realName = displayName.substring(0, pdfIndex + 4)
                            return realName
                        }
                    }
                    return displayName
                }
            }
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun sendMessage() {
        val text = binding?.inputField?.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        if (viewModel.isLoading.value == true) return

        binding?.inputField?.text?.clear()

        val attachmentUri = viewModel.pendingAttachment.value
        viewModel.clearPendingAttachment()

        if (attachmentUri != null) {
            sendWithAttachment(text, attachmentUri)
        } else {
            doSend(text)
        }
    }

    private fun showDeleteSessionDialog() {
        val current = sessionList.getOrNull(binding?.sessionSpinner?.selectedItemPosition ?: 0)
        if (current != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("删除会话")
                .setMessage("删除「${current.title}」的聊天记录？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteSession(current)
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    // 文件附件数量限制
    private val MAX_ATTACHMENTS_PER_MESSAGE = 3 // 每次对话最多附件数

    private fun sendWithAttachment(text: String, uri: Uri) {
        agentSession?.dispose()
        agentSession = null
        viewModel.sendWithAttachment(text, uri, agentProvider)
    }

    private fun openFilePicker() {
        // 检查是否已有待发送的附件
        if (viewModel.pendingAttachment.value != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("附件提示")
                .setMessage("您已经添加了一个附件，请先发送当前消息后再添加新附件。\n\n每条消息最多支持 ${MAX_ATTACHMENTS_PER_MESSAGE} 个附件。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }

        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun loadCMapResources() {
        val cmapDir = "com/tom_roush/pdfbox/resources/cmap/"

        val assets = context?.assets
        if (assets == null) {
            LogUtils.e( "[PDF-CMap] ❌ Assets 为 null，无法加载 CMap")
            return
        }

        val cmapFiles = assets.list(cmapDir)
        if (cmapFiles == null) {
            LogUtils.e( "[PDF-CMap] ❌ 无法列出 CMap 目录")
            return
        }

        try {
            val cMapManagerClass = Class.forName("com.tom_roush.pdfbox.pdmodel.font.CMapManager")
            val cMapParserClass = Class.forName("com.tom_roush.fontbox.cmap.CMapParser")

            // 尝试访问 CMapManager 的预定义 CMap 缓存
            // 查找所有可能的字段名
            val possibleFieldNames = listOf(
                "PREDEFINED_CMAPS",
                "predefinedCMaps",
                "cmapCache",
                "CMAP_CACHE"
            )

            var cacheField: java.lang.reflect.Field? = null
            for (fieldName in possibleFieldNames) {
                try {
                    cacheField = cMapManagerClass.getDeclaredField(fieldName)
                    break
                } catch (e: NoSuchFieldException) {
                    // 继续尝试下一个字段名
                }
            }

            if (cacheField != null) {
                cacheField.isAccessible = true
                cacheField.get(null)
            }

            // 尝试直接解析 CMap 并注册
            val parseMethod = cMapParserClass.getDeclaredMethod("parse", java.io.InputStream::class.java)
            parseMethod.isAccessible = true

            // 尝试创建 CMap 对象并注册
            for (cmapFile in cmapFiles) {
                try {
                    // 只注册关键的 CMap
                    if (cmapFile !in listOf("Identity-H", "Identity-V", "Adobe-GB1-UCS2", "Adobe-CNS1-UCS2", "Adobe-Japan1-UCS2", "Adobe-Korea1-UCS2")) {
                        continue
                    }

                    val inputStream = assets.open("$cmapDir$cmapFile")
                    parseMethod.invoke(null, inputStream)
                    inputStream.close()
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
            LogUtils.e( "[PDF-CMap] ❌ 加载 CMap 资源时出错: ${e::class.simpleName} - ${e.message}", e)
        }
    }

    private fun doSend(text: String) {
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = text))

        agentSession?.dispose()
        agentSession = null

        viewModel.sendToLlm(
            text = text,
            agentProvider = agentProvider,
        )
    }

    /**
     * 生成debug版本的详细错误信息
     * 仅在debug build中显示堆栈跟踪和技术细节
     */

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroyView() {
        agentSession?.dispose()
        agentSession = null
        super.onDestroyView()
    }
}
