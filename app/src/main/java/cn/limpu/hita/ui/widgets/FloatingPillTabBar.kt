package cn.limpu.hita.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cn.limpu.hita.R

/**
 * 浮动胶囊式底部标签栏，仿 Telegram 风格。
 *
 * 每个 tab 纵向排列：icon 在上，文字在下，始终可见。
 * clipToOutline 裁剪子项到胶囊曲线，实现完美贴合。
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
    private val inactiveTextAlpha = 0.5f

    private val iconSize = dp(18)
    private val itemWidth = dp(60)
    private val itemPadH = dp(6)
    private val itemPadV = dp(7)
    private val gapItems = dp(2)
    private val gapIconLabel = dp(3)
    private val pillElevation = dp(4).toFloat()
    private val activeScale = 1.08f
    private val barPadV = dp(1)

    private val animDuration = 400L
    private val smooth = DecelerateInterpolator()

    private val activeBgColor = Color.argb(0xD9, 0x2A, 0xAB, 0xEE)

    private fun createActiveBg(pos: Int): GradientDrawable {
        val large = dp(999).toFloat()
        val small = dp(10).toFloat()
        val lastIdx = tabItems.size - 1
        return GradientDrawable().apply {
            setColor(activeBgColor)
            cornerRadii = when {
                pos == 0 && pos == lastIdx ->
                    floatArrayOf(large, large, large, large, large, large, large, large)
                pos == 0 ->
                    floatArrayOf(large, large, small, small, small, small, large, large)
                pos == lastIdx ->
                    floatArrayOf(small, small, large, large, large, large, small, small)
                else ->
                    floatArrayOf(small, small, small, small, small, small, small, small)
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_pill_bar)
        // 只有垂直内边距，水平方向由 clipToOutline 裁剪贴合
        setPadding(0, barPadV, 0, barPadV)
        elevation = dp(8).toFloat()

        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = view.height / 2f
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    fun setTabs(items: List<TabItem>) {
        tabItems = items
        selectedPosition = 0
        removeAllViews()
        itemViews.clear()
        iconViews.clear()
        labelViews.clear()

        items.forEachIndexed { index, item ->
            val itemLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(itemPadH, itemPadV, itemPadH, itemPadV)
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
                textSize = 10f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT
                maxLines = 1
                alpha = inactiveTextAlpha
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lp.topMargin = gapIconLabel
                layoutParams = lp
            }

            itemLayout.addView(icon)
            itemLayout.addView(label)

            val isFirst = index == 0
            val isLast = index == items.size - 1
            val lp = LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT)
            // 首尾 item 贴边，中间 item 有间距；clipToOutline 裁剪边缘到曲线
            lp.leftMargin = if (isFirst) 0 else gapItems
            lp.rightMargin = if (isLast) 0 else 0
            itemLayout.layoutParams = lp

            addView(itemLayout)
            itemViews.add(itemLayout)
            iconViews.add(icon)
            labelViews.add(label)
        }

        applyActiveFull(0)
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
        if (position !in tabItems.indices || position == selectedPosition || isAnimating) return
        val oldPos = selectedPosition
        selectedPosition = position
        applyInactiveFull(oldPos)
        applyActiveFull(position)
    }

    // ──────────────────────────────────────
    //  Animation
    // ──────────────────────────────────────

    private fun animateSwitch(from: Int, to: Int) {
        isAnimating = true

        // 旧项瞬间失活，不参与动画
        applyInactiveFull(from)

        // 新项渐入动画
        val newItem = itemViews[to]
        val newIcon = iconViews[to]
        val newLabel = labelViews[to]

        // alpha 先于 setBackground，避免 clipToOutline 下 GradientDrawable 一帧全透闪烁
        val newBg = createActiveBg(to)
        newBg.alpha = 0
        newItem.background = newBg

        // 预置 active tint，用 icon alpha 做渐显，避免 setTint/imageTintList 竞争
        newIcon.imageTintList = activeTint
        newIcon.alpha = inactiveTextAlpha
        newItem.elevation = 0f
        newItem.scaleX = 1f
        newItem.scaleY = 1f
        newLabel.alpha = inactiveTextAlpha

        // elevation 延迟：前 60% 动画保持 elevation=0，后 40% 线性升到 pillElevation
        val elevationDelayFrac = 0.6f
        val elevRampDenom = 1f - elevationDelayFrac

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            interpolator = null
            addUpdateListener {
                val raw = animatedValue as Float
                val f = smooth.getInterpolation(raw)
                val s = easeOutBack(raw)

                newBg.alpha = (f * 255).toInt().coerceIn(0, 255)
                newIcon.alpha = inactiveTextAlpha + f * (1f - inactiveTextAlpha)
                newLabel.alpha = inactiveTextAlpha + f * (1f - inactiveTextAlpha)
                newItem.scaleX = 1f + (activeScale - 1f) * s
                newItem.scaleY = 1f + (activeScale - 1f) * s
                // elevation 等背景可见后再出现，避免透明背景+可见阴影闪烁
                val elevF = ((raw - elevationDelayFrac) / elevRampDenom).coerceIn(0f, 1f)
                newItem.elevation = pillElevation * elevF
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    newItem.elevation = pillElevation
                    newItem.scaleX = activeScale
                    newItem.scaleY = activeScale
                    newIcon.alpha = 1f
                    newLabel.alpha = 1f
                    newLabel.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD, false)
                    isAnimating = false
                }
            })
            start()
        }
    }

    // ──────────────────────────────────────
    //  State helpers
    // ──────────────────────────────────────

    private fun applyActiveFull(pos: Int) {
        val item = itemViews[pos]
        item.background = createActiveBg(pos)
        item.elevation = pillElevation
        item.scaleX = activeScale
        item.scaleY = activeScale
        val icon = iconViews[pos]
        icon.imageTintList = activeTint
        icon.alpha = 1f
        val label = labelViews[pos]
        label.alpha = 1f
        label.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD, false)
    }

    private fun applyInactiveFull(pos: Int) {
        val item = itemViews[pos]
        item.setBackgroundResource(0)
        item.elevation = 0f
        item.scaleX = 1f
        item.scaleY = 1f
        val icon = iconViews[pos]
        icon.imageTintList = inactiveTint
        icon.alpha = 1f
        val label = labelViews[pos]
        label.alpha = inactiveTextAlpha
        label.typeface = Typeface.DEFAULT
    }

    // ──────────────────────────────────────
    //  Utils
    // ──────────────────────────────────────

    /** easeOutBack：末端轻微回弹，仅用于 scale */
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val t1 = t - 1f
        return (1f + c3 * t1 * t1 * t1 + c1 * t1 * t1).coerceIn(0f, 1.05f)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
