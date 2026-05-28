package com.limpu.hitax.ui.event

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme

@SuppressLint("ValidFragment")
class FragmentTimeInfoSheet : BottomSheetDialogFragment(), EventItemFragment.EventParent {

    var mode = 0
    private var currentPosition = 0
    private var events: MutableList<EventItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val eventsArg = it.getSerializable("events") as List<EventItem>
            events.addAll(eventsArg)
            mode = it.getInt("mode")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    TimeInfoSheetContent(
                        events = events,
                        fragmentManager = childFragmentManager,
                        eventParent = this@FragmentTimeInfoSheet,
                        initialPosition = currentPosition
                    )
                }
            }
        }
    }

    fun hasMultiEvents(): Boolean {
        return events.size > 1
    }

    companion object {
        fun newInstance(events: ArrayList<EventItem>): FragmentTimeInfoSheet {
            val d = Bundle()
            d.putSerializable("events", events)
            val fe = FragmentTimeInfoSheet()
            fe.arguments = d
            return fe
        }
    }

    override fun callDismiss() {
        dismiss()
    }
}

@Composable
private fun TimeInfoSheetContent(
    events: List<EventItem>,
    fragmentManager: androidx.fragment.app.FragmentManager,
    eventParent: EventItemFragment.EventParent,
    initialPosition: Int
) {
    val tokens = HitaTheme.tokens
    var currentPosition by remember { mutableIntStateOf(initialPosition) }
    val showTabs = events.size > 1

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTabs) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    TabLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        events.forEach { event ->
                            addTab(newTab().setText(event.name))
                        }
                        addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab) {
                                val newPos = tab.position
                                val transaction = fragmentManager.beginTransaction()
                                if (newPos > currentPosition) {
                                    transaction.setCustomAnimations(
                                        R.anim.fragment_slide_from_right,
                                        R.anim.fragment_slide_to_left
                                    )
                                } else {
                                    transaction.setCustomAnimations(
                                        R.anim.fragment_slide_from_left,
                                        R.anim.fragment_slide_to_right
                                    )
                                }
                                transaction.replace(
                                    android.R.id.content,
                                    EventItemFragment.newInstance(events[newPos], eventParent)
                                ).commitAllowingStateLoss()
                                currentPosition = newPos
                            }

                            override fun onTabUnselected(tab: TabLayout.Tab) {}
                            override fun onTabReselected(tab: TabLayout.Tab) {}
                        })
                    }
                }
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                android.widget.FrameLayout(context).apply {
                    id = android.R.id.content
                    fragmentManager.beginTransaction()
                        .add(
                            android.R.id.content,
                            EventItemFragment.newInstance(events[0], eventParent),
                            "f"
                        ).commit()
                }
            }
        )
    }
}
