package com.limpu.hitax.ui.main.timetable.panel

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.view.HapticFeedbackConstants
import android.view.View
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.databinding.FragmentTimetablePanelBinding
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FragmentTimetablePanel : TransparentModeledBottomSheetDialog<TimetablePanelViewModel, FragmentTimetablePanelBinding>() {


    override fun initViews(view: View) {
        bindLiveData()
        binding?.reset?.setOnClickListener {
            viewModel.startResetColor()
        }
        binding?.from?.setOnClickListener {
            viewModel.startDateLiveData.value?.let {
                val minuteValue = it % 100
                val hour = it / 100
                TimePickerDialog(requireContext(), { view, hourOfDay, minute ->
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.changeStartDate(hourOfDay, minute)
                }, hour, minuteValue, true)
                        .show()
            }
        }
        binding?.drawbglines?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDrawBGLines(isChecked)
        }
        binding?.colorEnable?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setColorEnable(isChecked)
        }
        binding?.fadeEnable?.setOnCheckedChangeListener{_,isChecked->
            viewModel.setFadeEnable(isChecked)
        }
        binding?.periodLabel?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPeriodLabelEnabled(isChecked)
        }
        binding?.autoReimport?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoReimportEnabled(isChecked)
            if (isChecked) {
                viewModel.triggerAutoReimportNow()
            }
        }
        binding?.scrimSeekbar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let { viewModel.setScrimOpacity(it.progress) }
            }
        })
        binding?.cardOpacitySeekbar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let { viewModel.setCardOpacity(it.progress) }
            }
        })

    }

    @SuppressLint("SetTextI18n")
    fun bindLiveData() {
        viewModel.drawBGLinesLiveData.observe(this) {
            binding?.drawbglines?.isChecked = it
        }
        viewModel.startDateLiveData.observe(this) {
            binding?.from?.text = TimeInDay(it/100,it%100).toString()
        }
        viewModel.colorEnableLiveData.observe(this) {
            binding?.colorEnable?.isChecked = it
        }
        viewModel.fadeEnableLiveData.observe(this) {
            binding?.fadeEnable?.isChecked = it
        }
        viewModel.periodLabelLiveData.observe(this) {
            binding?.periodLabel?.isChecked = it
        }
        viewModel.autoReimportLiveData.observe(this) {
            binding?.autoReimport?.isChecked = it
        }
        viewModel.scrimOpacityLiveData.observe(this) {
            binding?.scrimSeekbar?.progress = it
        }
        viewModel.cardOpacityLiveData.observe(this) {
            binding?.cardOpacitySeekbar?.progress = it
        }
    }


    override fun getLayoutId(): Int {
        return R.layout.fragment_timetable_panel
    }

    override fun getViewModelClass(): Class<TimetablePanelViewModel> {
        return TimetablePanelViewModel::class.java
    }

    override fun initViewBinding(v: View): FragmentTimetablePanelBinding {
        return FragmentTimetablePanelBinding.bind(v)
    }
}
