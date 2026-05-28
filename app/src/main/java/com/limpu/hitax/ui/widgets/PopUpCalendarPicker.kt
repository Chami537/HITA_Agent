package com.limpu.hitax.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.utils.TimeTools
import com.limpu.hitax.utils.TimeTools.TTY_REPLACE
import java.util.Calendar

class PopUpCalendarPicker : BottomSheetDialogFragment() {

    @StringRes
    var initTitle: Int? = null
    private var initValue: Long? = null
    private var onConfirmListener: OnConfirmListener? = null

    interface OnConfirmListener {
        fun onConfirm(c: Calendar)
    }

    fun setTitle(@StringRes title: Int): PopUpCalendarPicker {
        initTitle = title
        return this
    }

    fun setInitValue(value: Long?): PopUpCalendarPicker {
        initValue = value
        return this
    }

    fun setOnConfirmListener(onConfirmListener: OnConfirmListener): PopUpCalendarPicker {
        this.onConfirmListener = onConfirmListener
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    CalendarPickerScreen(
                        initTitleRes = initTitle,
                        initValue = initValue,
                        onCancel = { dismiss() },
                        onConfirm = { calendar ->
                            onConfirmListener?.onConfirm(calendar)
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPickerScreen(
    @StringRes initTitleRes: Int?,
    initValue: Long?,
    onCancel: () -> Unit,
    onConfirm: (Calendar) -> Unit
) {
    val tokens = HitaTheme.tokens
    val context = LocalContext.current
    val defaultTitle = if (initTitleRes != null) {
        stringResource(initTitleRes)
    } else {
        initValue?.let {
            val c = Calendar.getInstance()
            c.timeInMillis = it
            TimeTools.getDateString(context, c, true, TTY_REPLACE)
        }.orEmpty()
    }
    var selectedDate by remember { mutableLongStateOf(initValue ?: System.currentTimeMillis()) }
    var titleText by remember { mutableStateOf(defaultTitle) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.lg,
                    end = tokens.spacing.lg,
                    top = tokens.spacing.lg,
                    bottom = tokens.spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Card(
                onClick = onCancel,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs)
                )
            }
            Card(
                onClick = { onConfirm(Calendar.getInstance().apply { timeInMillis = selectedDate }) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(start = tokens.spacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs)
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = tokens.spacing.lg),
            factory = { context ->
                android.widget.CalendarView(context).apply {
                    initValue?.let { setDate(it, true, true) }
                    setOnDateChangeListener { _, year, month, dayOfMonth ->
                        val c = Calendar.getInstance()
                        c.set(year, month, dayOfMonth)
                        selectedDate = c.timeInMillis
                        titleText = TimeTools.getDateString(context, c, true, TTY_REPLACE)
                    }
                }
            }
        )
    }
}
