package com.limpu.hitax.ui.main.agent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.limpu.hitax.R
import com.limpu.hitax.agent.tools.SearchExternalResourceTool
import com.limpu.hitax.data.model.resource.AgentResourceCard
import com.limpu.hitax.databinding.ItemAgentChatMessageBinding
import com.limpu.hitax.ui.resource.UnifiedResourceSearchActivity
import com.limpu.hitax.utils.ActivityUtils
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import com.limpu.hitax.utils.LogUtils

class AgentChatMessageAdapter : RecyclerView.Adapter<AgentChatMessageAdapter.MessageHolder>() {
    private val items = mutableListOf<AgentChatMessage>()
    private var markwon: Markwon? = null

    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingPosition = 0
    private val thinkingTexts = listOf("正在思考", "正在思考.", "正在思考..", "正在思考...")
    private val thinkingRunnable = object : Runnable {
        override fun run() {
            items.forEachIndexed { index, message ->
                if (message.role == AgentChatMessage.Role.ASSISTANT && message.isPlaceholder) {
                    notifyItemChanged(index)
                }
            }
            thinkingPosition = (thinkingPosition + 1) % thinkingTexts.size
            thinkingHandler.postDelayed(this, 500)
        }
    }

    private fun startThinkingAnimation() {
        thinkingHandler.removeCallbacks(thinkingRunnable)
        thinkingHandler.postDelayed(thinkingRunnable, 500)
    }

    private fun stopThinkingAnimation() {
        thinkingHandler.removeCallbacks(thinkingRunnable)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        startThinkingAnimation()
    }

    class MessageHolder(val binding: ItemAgentChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    private fun getMarkwon(context: Context): Markwon {
        return markwon ?: run {
            val builder = Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())

            try {
                builder.usePlugin(JLatexMathPlugin.create(13f))
            } catch (e: Exception) {
                LogUtils.e("Failed to enable JLatexMathPlugin", e)
            }

            builder.build().also { markwon = it }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        val binding = ItemAgentChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return MessageHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        stopThinkingAnimation()
    }

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val item = items[position]

        holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
        holder.binding.messageCard.strokeWidth = 0
        holder.binding.messageText.setTextColor(Color.BLACK)

        val layoutParams = holder.binding.messageCard.layoutParams as FrameLayout.LayoutParams
        when (item.role) {
            AgentChatMessage.Role.USER -> {
                holder.binding.messageText.text = item.text
                holder.binding.thinkingIndicator.visibility = View.GONE
                holder.binding.thinkingHeader.visibility = View.GONE
                holder.binding.thinkingText.visibility = View.GONE
                holder.binding.resourceCardsHeader.visibility = View.GONE
                holder.binding.resourceCardsContainer.visibility = View.GONE
                holder.binding.resourceCardsContainer.removeAllViews()
                layoutParams.gravity = Gravity.END

                val blueColor = Color.parseColor("#304ffe")
                holder.binding.messageCard.setCardBackgroundColor(blueColor)
                holder.binding.messageCard.strokeWidth = 0
                holder.binding.messageText.setTextColor(Color.WHITE)
            }

            AgentChatMessage.Role.ASSISTANT -> {
                if (item.isPlaceholder) {
                    holder.binding.messageText.text = item.text
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.grayA5))
                    holder.binding.thinkingIndicator.visibility = View.VISIBLE
                    holder.binding.thinkingStatusText.text = thinkingTexts[thinkingPosition]
                    holder.binding.thinkingHeader.visibility = View.GONE
                    if (item.thinking.isNullOrBlank()) {
                        holder.binding.thinkingText.visibility = View.GONE
                    } else {
                        holder.binding.thinkingText.visibility = View.VISIBLE
                        holder.binding.thinkingText.text = item.thinking
                    }
                    holder.binding.resourceCardsHeader.visibility = View.GONE
                    holder.binding.resourceCardsContainer.visibility = View.GONE
                    holder.binding.resourceCardsContainer.removeAllViews()
                } else {
                    holder.binding.thinkingIndicator.visibility = View.GONE
                    getMarkwon(holder.itemView.context).setMarkdown(holder.binding.messageText, item.text)
                    holder.binding.messageText.movementMethod = LinkMovementMethod.getInstance()
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.black))

                    if (item.thinking != null) {
                        holder.binding.thinkingHeader.visibility = View.VISIBLE
                        holder.binding.thinkingHeader.text = if (item.isThinkingExpanded) "▼ 思考过程" else "▶ 思考过程"
                        holder.binding.thinkingHeader.setOnClickListener {
                            toggleThinking(position)
                        }

                        if (item.isThinkingExpanded) {
                            holder.binding.thinkingText.visibility = View.VISIBLE
                            holder.binding.thinkingText.text = item.thinking
                        } else {
                            holder.binding.thinkingText.visibility = View.GONE
                        }
                    } else {
                        holder.binding.thinkingHeader.visibility = View.GONE
                        holder.binding.thinkingText.visibility = View.GONE
                    }
                    bindResourceCards(holder, item, position)
                }
                layoutParams.gravity = Gravity.START
                holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
                holder.binding.messageCard.strokeWidth = 0
            }

            AgentChatMessage.Role.TRACE -> {
            }
        }
        holder.binding.messageCard.layoutParams = layoutParams
    }

    private fun bindResourceCards(holder: MessageHolder, item: AgentChatMessage, position: Int) {
        val cards = item.resourceCards
        val header = holder.binding.resourceCardsHeader
        val container = holder.binding.resourceCardsContainer
        container.removeAllViews()

        if (cards.isEmpty()) {
            header.visibility = View.GONE
            container.visibility = View.GONE
            return
        }

        header.visibility = View.VISIBLE
        header.text = if (item.areResourceCardsExpanded) "▼ 相关资料 ${cards.size} 个" else "▶ 相关资料 ${cards.size} 个"
        header.setOnClickListener { toggleResourceCards(position) }

        container.visibility = if (item.areResourceCardsExpanded) View.VISIBLE else View.GONE
        if (!item.areResourceCardsExpanded) return

        cards.forEach { card ->
            container.addView(createResourceCardView(holder.itemView.context, card))
        }
    }

    private fun createResourceCardView(context: Context, card: AgentResourceCard): View {
        val outer = MaterialCardView(context).apply {
            radius = dp(context, 8).toFloat()
            cardElevation = 0f
            strokeWidth = dp(context, 1)
            setStrokeColor(context.getColor(R.color.outline_variant))
            setCardBackgroundColor(resolveColor(context, R.attr.backgroundColorSecond))
            isClickable = true
            isFocusable = true
            foreground = context.getDrawable(android.R.drawable.list_selector_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 6)
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 10), dp(context, 8), dp(context, 8), dp(context, 8))
        }

        val textGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = card.title.ifBlank { "未命名资料" }
            setTextColor(resolveColor(context, R.attr.textColorPrimary))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitle = TextView(context).apply {
            text = card.subtitle.ifBlank { card.path }
            setTextColor(resolveColor(context, R.attr.textColorSecondary))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        textGroup.addView(title)
        textGroup.addView(subtitle)

        val source = TextView(context).apply {
            text = card.sourceTag.ifBlank { "资料" }
            textSize = 11f
            setTextColor(context.getColor(R.color.white))
            gravity = Gravity.CENTER
            setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
            background = context.getDrawable(R.drawable.element_rounded_button_bg_primary)
        }

        row.addView(textGroup)
        row.addView(source)
        outer.addView(row)

        outer.setOnClickListener {
            if (card.source == SearchExternalResourceTool.SOURCE_HOA) {
                ActivityUtils.startCourseReadmeActivity(
                    context,
                    card.repoName.ifBlank { card.path },
                    card.title,
                    card.courseCode,
                    card.repoType.ifBlank { "normal" },
                )
                return@setOnClickListener
            }
            context.startActivity(Intent(context, UnifiedResourceSearchActivity::class.java).apply {
                putExtra("browsePath", card.path)
                putExtra("browseSource", card.source)
                putExtra("browseTitle", card.title)
                putExtra("query", card.query.ifBlank { card.title })
            })
        }
        return outer
    }

    private fun toggleThinking(position: Int) {
        val item = items[position]
        if (item.role == AgentChatMessage.Role.ASSISTANT && item.thinking != null) {
            items[position] = item.copy(isThinkingExpanded = !item.isThinkingExpanded)
            notifyItemChanged(position)
        }
    }

    private fun toggleResourceCards(position: Int) {
        val item = items[position]
        if (item.role == AgentChatMessage.Role.ASSISTANT && item.resourceCards.isNotEmpty()) {
            items[position] = item.copy(areResourceCardsExpanded = !item.areResourceCardsExpanded)
            notifyItemChanged(position)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun resolveColor(context: Context, attr: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, Color.BLACK)
        } finally {
            typedArray.recycle()
        }
    }

    fun submitList(newItems: List<AgentChatMessage>) {
        items.clear()
        val filtered = newItems.filter { it.role != AgentChatMessage.Role.TRACE }
        items.addAll(filtered)
        notifyDataSetChanged()
    }
}
