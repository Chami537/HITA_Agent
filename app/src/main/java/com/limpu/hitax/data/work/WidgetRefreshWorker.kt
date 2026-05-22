package com.limpu.hitax.data.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.limpu.hitax.ui.widgets.WidgetUtils

class WidgetRefreshWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        WidgetUtils.sendRefreshToAll(applicationContext)
        return Result.success()
    }
}
