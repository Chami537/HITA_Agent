package com.limpu.hitax.ui.credit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.databinding.ItemCreditCategoryBinding

private val TYPE_COLORS = mapOf(
    TermSubject.TYPE.COM_A to Color.parseColor("#304ffe"),
    TermSubject.TYPE.COM_B to Color.parseColor("#5c6bc0"),
    TermSubject.TYPE.OPT_A to Color.parseColor("#26a69a"),
    TermSubject.TYPE.OPT_B to Color.parseColor("#66bb6a"),
    TermSubject.TYPE.MOOC to Color.parseColor("#ff7043")
)

private val TYPE_LABELS = mapOf(
    TermSubject.TYPE.COM_A to R.string.credit_type_required,
    TermSubject.TYPE.COM_B to R.string.credit_type_required_check,
    TermSubject.TYPE.OPT_A to R.string.credit_type_limited,
    TermSubject.TYPE.OPT_B to R.string.credit_type_elective,
    TermSubject.TYPE.MOOC to R.string.credit_type_mooc
)

class CreditCategoryAdapter(
    private val mContext: Context,
    mBeans: MutableList<CreditCategorySummary>,
    private val onEditGoal: (CreditCategorySummary) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<CreditCategoryAdapter.Holder>() {

    var items: List<CreditCategorySummary> = mBeans
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(val binding: ItemCreditCategoryBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCreditCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val data = items[position]
        val b = holder.binding

        // 颜色圆点
        b.typeDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                TYPE_COLORS[data.type] ?: Color.parseColor("#9e9e9e")
            )

        // 类型标签
        b.typeLabel.text = mContext.getString(
            TYPE_LABELS[data.type] ?: R.string.credit_type_unknown
        )

        // 学分值 + 课程数
        b.creditValue.text = mContext.getString(R.string.credit_format, data.totalCredits)
        b.subjectCountText.text = mContext.resources.getQuantityString(
            R.plurals.credit_subject_count, data.subjectCount, data.subjectCount
        )

        // 目标编辑按钮
        b.editGoalBtn.isVisible = true
        b.editGoalBtn.setOnClickListener { onEditGoal(data) }

        // 进度条
        if (data.goalCredits != null && data.goalCredits > 0) {
            b.progressSection.isVisible = true
            val progress = (data.totalCredits / data.goalCredits * 100).toInt().coerceIn(0, 100)
            b.goalProgress.progress = progress
            b.goalText.text = mContext.getString(
                R.string.credit_goal_progress_format, data.totalCredits, data.goalCredits
            )
        } else {
            b.progressSection.isVisible = false
        }

        // 领域展开/收起
        b.expandBtn.isVisible = data.fieldBreakdown.size > 1
        b.expandBtn.text = if (data.expanded) {
            mContext.getString(R.string.credit_collapse_detail)
        } else {
            mContext.getString(R.string.credit_expand_detail)
        }

        // 领域分布
        b.fieldBreakdown.removeAllViews()
        if (data.expanded) {
            b.fieldBreakdown.isVisible = true
            for (field in data.fieldBreakdown) {
                val row = TextView(mContext).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4 * mContext.resources.displayMetrics.density).toInt()
                    }
                    text = mContext.getString(
                        R.string.credit_field_row, field.fieldName, field.totalCredits
                    )
                    setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            mContext, R.color.on_surface_variant
                        )
                    )
                    textSize = 12f
                }
                b.fieldBreakdown.addView(row)
            }
        } else {
            b.fieldBreakdown.isVisible = false
        }
        b.expandBtn.setOnClickListener {
            val updated = data.copy(expanded = !data.expanded)
            (items as MutableList)[position] = updated
            notifyItemChanged(position)
        }
    }
}
