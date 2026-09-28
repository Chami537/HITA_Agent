package cn.limpu.hita.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding

abstract class HiltBaseFragmentWithReceiver<V : ViewBinding> : HiltBaseFragment<V>() {
    abstract var receiver: BroadcastReceiver
    abstract fun getIntentFilter(): IntentFilter

    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context ?: return
        // targetSdk 35: a dynamic receiver without an export flag crashes the process on launch.
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            try {
                context?.unregisterReceiver(receiver)
            } catch (_: Exception) { }
            receiverRegistered = false
        }
    }
}
