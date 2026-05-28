package com.limpu.hitax.ui.base

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.viewbinding.ViewBinding

class ComposeViewBinding(
    private val composeView: ComposeView
) : ViewBinding {
    override fun getRoot(): View = composeView
}
