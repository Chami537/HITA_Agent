package cn.limpu.hita.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.ui.event.FragmentTimeInfoSheet
import java.util.*

object EventsUtils {
    fun showEventItem(context: Context, eventItem: EventItem) {
        val activity = context as? AppCompatActivity ?: return
        val list: ArrayList<EventItem> = ArrayList<EventItem>()
        list.add(eventItem)
        FragmentTimeInfoSheet.newInstance(list).show(activity.supportFragmentManager, "event")
    }

    fun showEventItem(context: Context, eventItems: List<EventItem>) {
        val activity = context as? AppCompatActivity ?: return
        val list: ArrayList<EventItem> = ArrayList<EventItem>(eventItems)
        FragmentTimeInfoSheet.newInstance(list)
            .show(activity.supportFragmentManager, "event")
    }

    /**
     * 获得当前是第几节课
     * num为节数*10（+5）
     */
    fun getCurrentNumberText(context: Context,num:Int): String {
        val base = num / 10
        val plus = num % 10
        return if (base == 0) {
            context.getString(R.string.before_first_class)
        } else if (base == 12 && plus != 0) {
            context.getString(R.string.after_last_class)
        } else {
            if (plus == 0) context.getString(
                R.string.class_number_what,
                base
            ) else context.getString(R.string.class_after_number_what, base)
        }
    }

}