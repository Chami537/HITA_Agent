package cn.limpu.hita.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.limpu.hita.data.repository.CourseSelectionJobCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CourseSelectionBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var coordinator: CourseSelectionJobCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                coordinator.recoverAfterBoot()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
