package com.limpu.style.widgets

import android.content.DialogInterface
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.annotation.StringRes
import com.limpu.style.R
import com.limpu.style.databinding.DialogBottomTextBinding
import com.limpu.style.databinding.DialogBottomUpdateBinding

/**
 * 圆角的文本框底部弹窗
 */
class PopUpUpdate : TransparentBottomSheetDialog<DialogBottomUpdateBinding>() {


    @StringRes
    var init_title: Int? = null
    private var init_text: CharSequence? = null
    var onActionListener: OnActionListener? = null
    private var actionHandled = false

    interface OnActionListener {
        fun onConfirm()
        fun onCancel()
        fun onSkip()
    }

    override fun onStart() {
        super.onStart()
        binding.text.requestFocus()
    }

    fun setTitle(@StringRes title: Int): PopUpUpdate {
        init_title = title
        return this
    }

    fun setDialogCancelable(cancelable: Boolean): PopUpUpdate {
        isCancelable = cancelable
        return this
    }

    fun setText(text: CharSequence?): PopUpUpdate {
        init_text = text
        return this
    }

    fun setOnActionListener(lis: OnActionListener): PopUpUpdate {
        this.onActionListener = lis
        return this
    }


    override fun initViews(v: View) {
        if (init_title != null) {
            binding.title.setText(init_title!!)
        }
        if (!TextUtils.isEmpty(init_text)) {
            binding.text.text = init_text
            binding.text.movementMethod = LinkMovementMethod.getInstance()
            binding.text.visibility = View.VISIBLE
        } else {
            binding.text.movementMethod = null
            binding.text.visibility = View.GONE
        }
        if (isCancelable) {
            binding.cancel.visibility = View.VISIBLE
        } else {
            binding.cancel.visibility = View.GONE
        }
        binding.skip.setOnClickListener {
            actionHandled = true
            onActionListener?.onSkip()
            dismiss()
        }
        binding.cancel.setOnClickListener {
            actionHandled = true
            onActionListener?.onCancel()
            dismiss() }
        binding.confirm.setOnClickListener {

                actionHandled = true
                onActionListener?.onConfirm()

            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        actionHandled = true
        onActionListener?.onCancel()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!actionHandled) {
            actionHandled = true
            onActionListener?.onCancel()
        }
        super.onDismiss(dialog)
    }


    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_update
    }

    override fun initViewBinding(v: View): DialogBottomUpdateBinding{
        return DialogBottomUpdateBinding.bind(v)
    }
}
