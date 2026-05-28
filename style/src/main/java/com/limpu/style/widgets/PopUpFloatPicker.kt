package com.limpu.style.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.limpu.style.databinding.DialogBottomFloatPickerBinding
import java.util.*
import kotlin.math.roundToInt

class PopUpFloatPicker :
    TransparentBottomSheetDialog<DialogBottomFloatPickerBinding>() {
    private var a = 0
    private var b = 0
    private var mOnDialogConformListener: OnDialogConformListener? = null

    @StringRes
    private var init_title = 0
    private var init_a: Int = 0
    private var init_b: Int = 0

    override fun createViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogBottomFloatPickerBinding {
        return DialogBottomFloatPickerBinding.inflate(inflater, container, false)
    }

    interface OnDialogConformListener {
        fun onClick(result:Float)
    }

    fun setDialogTitle(@StringRes title: Int): PopUpFloatPicker {
        init_title = title
        return this
    }

    fun setInitialValue(x:Float): PopUpFloatPicker {
        init_a = x.toInt()
        init_b = (10*(x-init_a)).roundToInt()
        return this
    }

    fun setOnDialogConformListener(m: OnDialogConformListener): PopUpFloatPicker {
        mOnDialogConformListener = m
        return this
    }

    override fun initViews(v: View) {
        val aTexts: MutableList<String> = ArrayList()
        val bTexts: MutableList<String> = ArrayList()
        for (i in 0..9) aTexts.add(""+i)
        for (i in 0..9) bTexts.add(""+i)
        binding.a.setEntries(aTexts)
        binding.b.setEntries(bTexts)
        binding.a.setOnWheelChangedListener { _, _, newIndex ->
            a = newIndex
        }
        binding.b.setOnWheelChangedListener { _, _, newIndex ->
           b = newIndex
        }
        binding.confirm.setOnClickListener {
            mOnDialogConformListener?.onClick(
                a.toFloat()+b.toFloat()/10
            )
            dismiss()
        }
        binding.cancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.pickTimeLayout.visibility = View.VISIBLE
        binding.title.setText(init_title)
        binding.a.currentIndex = init_a
        binding.b.currentIndex = init_b
    }

    init {
        isCancelable = false
    }
}
