package cn.limpu.hita.ui.main.timetable.panel

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.TimeInDay
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FragmentTimetablePanel : BottomSheetDialogFragment() {

    private val viewModel: TimetablePanelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        retainInstance = true
        setStyle(STYLE_NORMAL, com.limpu.style.R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val wrapped = ContextThemeWrapper(requireContext(), com.limpu.style.R.style.AppTheme)
        return ComposeView(wrapped).apply {
            setContent {
                HitaComposeTheme() {
                    TimetablePanelScreen(
                        viewModel = viewModel,
                        onPickStartTime = { value ->
                            val minuteValue = value % 100
                            val hour = value / 100
                            TimePickerDialog(
                                requireContext(),
                                { pickerView, hourOfDay, minute ->
                                    pickerView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.changeStartDate(hourOfDay, minute)
                                },
                                hour,
                                minuteValue,
                                true
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetablePanelScreen(
    viewModel: TimetablePanelViewModel,
    onPickStartTime: (Int) -> Unit,
) {
    val startTime by viewModel.startDateLiveData.observeAsState(830)
    val drawBgLines by viewModel.drawBGLinesLiveData.observeAsState(true)
    val colorEnable by viewModel.colorEnableLiveData.observeAsState(true)
    val fadeEnable by viewModel.fadeEnableLiveData.observeAsState(true)
    val periodLabel by viewModel.periodLabelLiveData.observeAsState(false)
    val autoReimport by viewModel.autoReimportLiveData.observeAsState(false)
    val scrimOpacity by viewModel.scrimOpacityLiveData.observeAsState(30)
    val cardOpacity by viewModel.cardOpacityLiveData.observeAsState(85)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = HitaTheme.tokens.spacing.xl)
    ) {
        SectionTitle("显示设置")
        SettingRow(
            title = stringResource(R.string.timetable_start_time),
            trailing = {
                Text(
                    text = TimeInDay(startTime / 100, startTime % 100).toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onPickStartTime(startTime) }
                        .padding(horizontal = HitaTheme.tokens.spacing.md, vertical = HitaTheme.tokens.spacing.xs)
                )
            }
        )
        SwitchSettingRow(
            title = stringResource(R.string.draw_bg_dashed_lines),
            checked = drawBgLines,
            onCheckedChange = viewModel::setDrawBGLines
        )

        SectionTitle("个性化与外观")
        SwitchSettingRow(
            title = stringResource(R.string.curriculum_manager_enable_color),
            checked = colorEnable,
            onCheckedChange = viewModel::setColorEnable
        )
        SwitchSettingRow(
            title = stringResource(R.string.fade_enabled),
            checked = fadeEnable,
            onCheckedChange = viewModel::setFadeEnable
        )
        SettingRow(
            title = "科目颜色",
            trailing = {
                ResetColorButton(onClick = viewModel::startResetColor)
            }
        )
        SwitchSettingRow(
            title = stringResource(R.string.timetable_label_mode_period),
            checked = periodLabel,
            onCheckedChange = viewModel::setPeriodLabelEnabled
        )

        SectionTitle("数据管理")
        SwitchSettingRow(
            title = stringResource(R.string.timetable_auto_reimport),
            checked = autoReimport,
            onCheckedChange = {
                viewModel.setAutoReimportEnabled(it)
                if (it) viewModel.triggerAutoReimportNow()
            }
        )
        SliderSetting(
            title = stringResource(R.string.wallpaper_scrim_opacity),
            value = scrimOpacity,
            valueRange = 0f..80f,
            onValueChangeFinished = viewModel::setScrimOpacity
        )
        SliderSetting(
            title = stringResource(R.string.card_opacity),
            value = cardOpacity,
            valueRange = 20f..100f,
            onValueChangeFinished = viewModel::setCardOpacity
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HitaTheme.tokens.spacing.xl,
                top = HitaTheme.tokens.spacing.xl,
                end = HitaTheme.tokens.spacing.xl,
                bottom = HitaTheme.tokens.spacing.sm
            )
    )
}

@Composable
private fun SettingRow(
    title: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = HitaTheme.tokens.spacing.xl,
                vertical = HitaTheme.tokens.spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(title = title) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ResetColorButton(onClick: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier.clickable {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                start = HitaTheme.tokens.spacing.sm,
                top = HitaTheme.tokens.spacing.xs,
                end = HitaTheme.tokens.spacing.md,
                bottom = HitaTheme.tokens.spacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_color_lens_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(HitaTheme.tokens.spacing.xs))
            Text(
                text = stringResource(R.string.curriculum_manager_randomly_allocate_color),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: (Int) -> Unit,
) {
    var current by remember(value) {
        mutableFloatStateOf(value.toFloat())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = HitaTheme.tokens.spacing.xl,
                vertical = HitaTheme.tokens.spacing.sm
            )
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
        Slider(
            value = current,
            onValueChange = { current = it },
            valueRange = valueRange,
            onValueChangeFinished = { onValueChangeFinished(current.toInt()) }
        )
        Spacer(modifier = Modifier.height(HitaTheme.tokens.spacing.xs))
    }
}
