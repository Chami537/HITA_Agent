package com.limpu.hitax.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.limpu.hitax.R

/**
 * 浮动胶囊式底部标签栏，仿 Telegram 风格。
 *
 * 动画策略：FLIP（First-Last-Invert-Play）
 * 1. 记录切换前各 item 位置
 * 2. 瞬间应用 active/inactive 布局
 * 3. 用 translationX/Y 补偿偏移 → 视觉上回到旧位置
 * 4. 同一 ValueAnimator 驱动：位置回弹 + label 缩放 + 图标 tint + 背景 alpha
 */
class FloatingPillTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class TabItem(val iconRes: Int, val label: String)

    fun interface OnTabSelectedListener {
        fun onTabSelected(position: Int)
    }

    private val itemViews = mutableListOf<LinearLayout>()
    private val iconViews = mutableListOf<ImageView>()
    private val labelViews = mutableListOf<TextView>()
    private var tabItems = listOf<TabItem>()
    private var selectedPosition = 0
    private var listener: OnTabSelectedListener? = null
    private var isAnimating = false

    private val activeColor = Color.WHITE
    private val inactiveColor = 0x80FFFFFF.toInt()
    private val activeTint = ColorStateList.valueOf(activeColor)
    private val inactiveTint = ColorStateList.valueOf(inactiveColor)

    private val iconSize = dp(22)
    private val pad = dp(8)
    private val padLeftActive = dp(12)
    private val padRightActive = dp(16)
    private val gapItems = dp(2)
    private val gapIconLabel = dp(6)
    private val pillElevation = dp(4).toFloat()

    private val animDuration = 350L
    private val spring = OvershootInterpolator(1.2f)

    // Pre-load drawables to avoid inflation during animation
    private val activeBgDrawable: Drawable by lazy {
        context.getDrawable(R.drawable.bg_pill_item_active)!!
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_pill_bar)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        elevation = dp(8).toFloat()
    }

    fun setTabs(items: List<TabItem>) {
        tabItems = items
        selectedPosition = 0
        removeAllViews()
        itemViews.clear()
        iconViews.clear()
        labelViews.clear()

        items.forEachIndexed { index, item ->
            // Each tab is a FrameLayout-like: LinearLayout with background overlay + content
            val itemLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!isAnimating) selectTab(index)
                }
            }

            val icon = ImageView(context).apply {
                setImageResource(item.iconRes)
                imageTintList = inactiveTint
                layoutParams = LayoutParams(iconSize, iconSize)
            }

            val label = TextView(context).apply {
                text = item.label
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD, false)
                maxLines = 1
                visibility = GONE
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lp.leftMargin = gapIconLabel
                layoutParams = lp
            }

            itemLayout.addView(icon)
            itemLayout.addView(label)

            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            if (index < items.size - 1) lp.rightMargin = gapItems
            itemLayout.layoutParams = lp

            addView(itemLayout)
            itemViews.add(itemLayout)
            iconViews.add(icon)
            labelViews.add(label)
        }

        // Init first tab as active
        applyActiveLayout(0)
    }

    fun selectTab(position: Int) {
        if (position == selectedPosition || position !in tabItems.indices || isAnimating) return
        val oldPos = selectedPosition
        selectedPosition = position
        animateSwitch(oldPos, position)
        listener?.onTabSelected(position)
    }

    fun setOnTabSelectedListener(l: OnTabSelectedListener) {
        listener = l
    }

    fun setSelectedTab(position: Int) {
        if (position !in tabItems.indices || position == selectedPosition) return
        val oldPos = selectedPosition
        selectedPosition = position
        applyInactiveFull(oldPos)
        applyActiveLayout(position)
    }

    // ──────────────────────────────────────
    //  FLIP animation
    // ──────────────────────────────────────

    private fun animateSwitch(from: Int, to: Int) {
        isAnimating = true

        // === FIRST: record current positions ===
        val oldLefts = itemViews.map { it.left.toFloat() }

        // === LAST: apply layout changes ===
        applyInactiveLayoutOnly(from)
        applyActiveLayoutOnly(to)

        // Intercept pre-draw to capture new positions + apply compensation before render
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)

                val newLefts = itemViews.map { it.left.toFloat() }

                // === INVERT: translate back to old positions ===
                for (i in itemViews.indices) {
                    itemViews[i].translationX = oldLefts[i] - newLefts[i]
                }

                // === PLAY: start unified animation ===
                startFlipAnimation(from, to, oldLefts, newLefts)
                return true
            }
        })
    }

    private fun startFlipAnimation(
        from: Int, to: Int,
        oldLefts: List<Float>, newLefts: List<Float>
    ) {
        val oldLabel = labelViews[from]
        val newLabel = labelViews[to]
        val oldIcon = iconViews[from]
        val newIcon = iconViews[to]
        val oldItem = itemViews[from]
        val newItem = itemViews[to]

        // Old: copy active background to allow independent alpha animation
        val oldBg = activeBgDrawable.constantState?.newDrawable()?.mutate()
        oldItem.background = oldBg

        // New: set background, initially transparent, icon already at active tint
        val newBg = activeBgDrawable.constantState?.newDrawable()?.mutate()
        newItem.background = newBg
        newBg?.alpha = 0
        newItem.elevation = pillElevation

        newLabel.alpha = 0f
        newLabel.scaleX = 0f
        newLabel.scaleY = 0f
        newIcon.imageTintList = activeTint

        oldLabel.alpha = 1f
        oldLabel.scaleX = 1f
        oldLabel.scaleY = 1f

        val aR = (activeColor shr 16) and 0xFF
        val aG = (activeColor shr 8) and 0xFF
        val aB = activeColor and 0xFF
        val iR = (inactiveColor shr 16) and 0xFF
        val iG = (inactiveColor shr 8) and 0xFF
        val iB = inactiveColor and 0xFF

        val oldDrw = oldIcon.drawable
        val newDrw = newIcon.drawable

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            interpolator = spring
            addUpdateListener {
                val t = animatedValue as Float

                // ── Position: slide items to final layout ──
                for (i in itemViews.indices) {
                    itemViews[i].translationX = (oldLefts[i] - newLefts[i]) * (1f - t)
                }

                // ── Old: collapse ──
                oldLabel.alpha = 1f - t
                oldLabel.scaleX = 1f - t
                oldLabel.scaleY = 1f - t
                oldBg?.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                oldDrw.setTint(argb(lerpByte(aR, iR, t), lerpByte(aG, iG, t), lerpByte(aB, iB, t)))

                // ── New: expand ──
                newLabel.alpha = t
                newLabel.scaleX = t
                newLabel.scaleY = t
                newBg?.alpha = (t * 255).toInt().coerceIn(0, 255)
                newDrw.setTint(argb(lerpByte(iR, aR, t), lerpByte(iG, aG, t), lerpByte(iB, aB, t)))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    itemViews.forEach { it.translationX = 0f }
                    applyInactiveFull(from)
                    applyActiveFull(to)
                    isAnimating = false
                }
            })
            start()
        }
    }

    // ──────────────────────────────────────
    //  State helpers
    // ──────────────────────────────────────

    /** Layout-only: padding + visibility (triggers measure/layout) */
    private fun applyActiveLayoutOnly(pos: Int) {
        val item = itemViews[pos]
        item.setPadding(padLeftActive, pad, padRightActive, pad)
        labelViews[pos].visibility = VISIBLE
    }

    private fun applyInactiveLayoutOnly(pos: Int) {
        val item = itemViews[pos]
        item.setPadding(pad, pad, pad, pad)
        labelViews[pos].visibility = GONE
    }

    /** Full apply (layout + visuals) for initial state or finalize */
    private fun applyActiveFull(pos: Int) {
        val item = itemViews[pos]
        item.setPadding(padLeftActive, pad, padRightActive, pad)
        item.setBackgroundResource(R.drawable.bg_pill_item_active)
        item.elevation = pillElevation
        iconViews[pos].imageTintList = activeTint
        val label = labelViews[pos]
        label.visibility = VISIBLE
        label.alpha = 1f
        label.scaleX = 1f
        label.scaleY = 1f
    }

    private fun applyActiveLayout(pos: Int) {
        applyActiveFull(pos)
    }

    private fun applyInactiveFull(pos: Int) {
        val item = itemViews[pos]
        item.setBackgroundResource(0)
        item.elevation = 0f
        item.setPadding(pad, pad, pad, pad)
        iconViews[pos].imageTintList = inactiveTint
        val label = labelViews[pos]
        label.visibility = GONE
        label.alpha = 0f
        label.scaleX = 1f
        label.scaleY = 1f
    }

    // ──────────────────────────────────────
    //  Utils
    // ──────────────────────────────────────

    private fun lerpByte(a: Int, b: Int, t: Float): Int =
        (a + ((b - a) * t).toInt()).coerceIn(0, 255)

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
