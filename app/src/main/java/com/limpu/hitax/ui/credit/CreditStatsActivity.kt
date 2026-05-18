package com.limpu.hitax.ui.credit

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivityCreditStatsBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.style.widgets.PopUpFloatPicker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreditStatsActivity : HiltBaseActivity<ActivityCreditStatsBinding>() {

    private val viewModel: CreditStatsViewModel by viewModels()
    private lateinit var categoryAdapter: CreditCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        categoryAdapter = CreditCategoryAdapter(
            this,
            mutableListOf()
        ) { category ->
            showGoalPicker(category)
        }

        binding.categoryList.layoutManager = LinearLayoutManager(this)
        binding.categoryList.adapter = categoryAdapter

        viewModel.creditStats.observe(this) { state ->
            if (state.isEmpty) {
                binding.emptyView.visibility = View.VISIBLE
                binding.summaryCard.visibility = View.GONE
                binding.categoryList.visibility = View.GONE
                binding.footerNote.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.summaryCard.visibility = View.VISIBLE
                binding.categoryList.visibility = View.VISIBLE
                binding.footerNote.visibility = View.VISIBLE

                binding.totalCreditsValue.text = String.format("%.1f", state.totalCredits)
                binding.totalSubjectsValue.text = state.totalSubjects.toString()
                binding.spaCreditsValue.text = String.format("%.1f", state.spaCredits)
                binding.nonSpaCreditsValue.text = String.format("%.1f", state.nonSpaCredits)

                categoryAdapter.items = state.categories
            }
        }
    }

    private fun showGoalPicker(category: CreditCategorySummary) {
        val picker = PopUpFloatPicker()
            .setDialogTitle(R.string.credit_set_goal)
            .setInitialValue(category.goalCredits ?: category.totalCredits)
        picker.setOnDialogConformListener(object : PopUpFloatPicker.OnDialogConformListener {
            override fun onClick(result: Float) {
                if (result <= 0f) {
                    viewModel.removeGoal(category.type)
                } else {
                    viewModel.setGoal(category.type, result)
                }
            }
        })
        picker.show(supportFragmentManager, "goal_picker")
    }

    override fun initViewBinding(): ActivityCreditStatsBinding {
        return ActivityCreditStatsBinding.inflate(layoutInflater)
    }
}
