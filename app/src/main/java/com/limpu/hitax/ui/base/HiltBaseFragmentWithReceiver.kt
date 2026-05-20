package com.limpu.hitax.ui.base

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Bundle
import androidx.viewbinding.ViewBinding

abstract class HiltBaseFragmentWithReceiver<V : ViewBinding> : HiltBaseFragment<V>() {
    abstract var receiver: BroadcastReceiver
    abstract fun getIntentFilter(): IntentFilter

    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().registerReceiver(receiver, getIntentFilter())
        receiverRegistered = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            try {
                requireContext().unregisterReceiver(receiver)
            } catch (_: Exception) { }
            receiverRegistered = false
        }
    }
}
