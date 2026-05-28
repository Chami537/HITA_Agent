package com.limpu.hitax.ui.main.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.data.source.preference.CourseReminderStore
import com.limpu.hitax.data.work.CourseReminderScheduler
import com.limpu.hitax.ui.credit.CreditStatsActivity
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.eas.classroom.EmptyClassroomActivity
import com.limpu.hitax.ui.eas.exam.ExamActivity
import com.limpu.hitax.ui.eas.imp.ImportTimetableActivity
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.ui.eas.score.ScoreInquiryActivity
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.ActivityUtils.CourseResourceMode
import com.limpu.hitax.utils.IcsImportUtils
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.hitauser.util.ImageUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NavigationFragment : androidx.fragment.app.Fragment() {

    @Inject lateinit var localUserRepository: LocalUserRepository
    @Inject lateinit var easRepository: EASRepository
    @Inject lateinit var timetableRepository: TimetableRepository

    private val viewModel: NavigationViewModel by viewModels()
    private var reminderEnabledState by mutableStateOf(false)

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAvatarLocally(it) }
    }

    private val selectIcsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importICS(it) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableCourseReminder()
        } else {
            reminderEnabledState = false
            Toast.makeText(requireContext(), "需要通知权限才能发送课程提醒", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        reminderEnabledState = CourseReminderStore(requireContext()).isEnabled()
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    NavigationScreen(
                        viewModel = viewModel,
                        localUser = localUserRepository.getLoggedInUser(),
                        easToken = easRepository.getEasToken(),
                        reminderEnabled = reminderEnabledState,
                        onAvatarClick = { showAvatarPicker() },
                        onUserClick = { openUserCard() },
                        onTimetableManager = { ActivityUtils.startTimetableManager(requireContext()) },
                        onRecentTimetable = {
                            viewModel.recentTimetableLiveData.value?.let {
                                ActivityUtils.startTimetableDetailActivity(requireContext(), it.id)
                            }
                        },
                        onImportTimetable = {
                            ActivityUtils.startActivity(requireContext(), ImportTimetableActivity::class.java)
                        },
                        onImportIcs = { selectIcsLauncher.launch(IcsImportUtils.pickerMimeTypes()) },
                        onExam = { openExam() },
                        onScores = { ActivityUtils.startActivity(requireContext(), ScoreInquiryActivity::class.java) },
                        onEmptyClassroom = { ActivityUtils.startActivity(requireContext(), EmptyClassroomActivity::class.java) },
                        onCreditStats = { ActivityUtils.startActivity(requireContext(), CreditStatsActivity::class.java) },
                        onCourseLookup = {
                            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.VIEW)
                        },
                        onCourseSubmit = {
                            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.SUBMIT)
                        },
                        onToggleReminder = { toggleCourseReminder(!reminderEnabledState) },
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        reminderEnabledState = CourseReminderStore(requireContext()).isEnabled()
        viewModel.startRefresh()
    }

    private fun showAvatarPicker() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("更换头像")
            .setItems(arrayOf("从相册选择", "取消")) { _, which ->
                if (which == 0) pickAvatarLauncher.launch("image/*")
            }
            .show()
    }

    private fun saveAvatarLocally(uri: Uri) {
        val ctx = requireContext().applicationContext
        Thread {
            try {
                val destFile = java.io.File(ctx.filesDir, "avatar_local.jpg")
                val futureTarget = Glide.with(ctx)
                    .asFile()
                    .load(uri)
                    .override(512, 512)
                    .centerCrop()
                    .submit()
                val tempFile = futureTarget.get()
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
                localUserRepository.changeLocalAvatar("local://${destFile.absolutePath}")
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "头像更换成功", Toast.LENGTH_SHORT).show()
                    Glide.get(ctx).clearMemory()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "图片处理失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun openUserCard() {
        val localUser = localUserRepository.getLoggedInUser()
        if (localUser.isValid()) {
            ActivityUtils.startProfileActivity(requireContext(), localUser.id, null)
        } else {
            ActivityUtils.showEasVerifyWindow<Activity>(
                requireActivity(),
                easRepository,
                directTo = null,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        window.dismiss()
                    }
                    override fun onFailed(window: PopUpLoginEAS) {}
                }
            )
        }
    }

    private fun openExam() {
        ActivityUtils.showEasVerifyWindow(
            requireActivity(),
            easRepository,
            directTo = ExamActivity::class.java,
            onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                override fun onSuccess(window: PopUpLoginEAS) {
                    ActivityUtils.startActivity(requireActivity(), ExamActivity::class.java)
                    window.dismiss()
                }

                override fun onFailed(window: PopUpLoginEAS) {}
            }
        )
    }

    private fun toggleCourseReminder(enable: Boolean) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    enableCourseReminder()
                } else {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                enableCourseReminder()
            }
        } else {
            reminderEnabledState = false
            CourseReminderStore(requireContext()).setEnabled(false)
            CourseReminderScheduler.autoSchedule(requireContext())
            Toast.makeText(requireContext(), "课程提醒已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importICS(uri: Uri) {
        val context = requireContext()
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(context, "无法读取所选 ICS 文件", Toast.LENGTH_SHORT).show()
                return
            }
            timetableRepository.importFromICSAsNewTimetable(inputStream, IcsImportUtils.getDisplayName(context, uri))
                .observe(viewLifecycleOwner) { result ->
                    when (result.state) {
                        com.limpu.component.data.DataState.STATE.SUCCESS -> {
                            val importResult = result.data ?: return@observe
                            Toast.makeText(
                                context,
                                "已创建课表“${importResult.timetableName}”，导入 ${importResult.importedCount} 个课程",
                                Toast.LENGTH_SHORT
                            ).show()
                            ActivityUtils.startTimetableDetailActivity(context, importResult.timetableId)
                        }
                        com.limpu.component.data.DataState.STATE.FETCH_FAILED -> {
                            Toast.makeText(context, "导入失败: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                        else -> Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableCourseReminder() {
        reminderEnabledState = true
        val reminderStore = CourseReminderStore(requireContext())
        reminderStore.setEnabled(true)
        CourseReminderScheduler.autoSchedule(requireContext())
        Toast.makeText(requireContext(), "课程提醒已开启（上课前10分钟提醒）", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun NavigationScreen(
    viewModel: NavigationViewModel,
    localUser: UserLocal,
    easToken: EASToken,
    reminderEnabled: Boolean,
    onAvatarClick: () -> Unit,
    onUserClick: () -> Unit,
    onTimetableManager: () -> Unit,
    onRecentTimetable: () -> Unit,
    onImportTimetable: () -> Unit,
    onImportIcs: () -> Unit,
    onExam: () -> Unit,
    onScores: () -> Unit,
    onEmptyClassroom: () -> Unit,
    onCreditStats: () -> Unit,
    onCourseLookup: () -> Unit,
    onCourseSubmit: () -> Unit,
    onToggleReminder: () -> Unit,
) {
    val tokens = HitaTheme.tokens
    val recentTimetable by viewModel.recentTimetableLiveData.observeAsState()
    val timetableCount by viewModel.timetableCountLiveData.observeAsState(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        UserCard(
            localUser = localUser,
            easToken = easToken,
            onAvatarClick = onAvatarClick,
            onClick = onUserClick,
        )
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            ShortcutCard(
                title = stringResource(R.string.recent_timetable),
                subtitle = recentTimetable?.name ?: stringResource(R.string.none),
                icon = R.drawable.ic_nav_timetable,
                onClick = onRecentTimetable,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
            )
            ShortcutCard(
                title = stringResource(R.string.title_timetable_manager),
                subtitle = if (timetableCount == 0) {
                    stringResource(R.string.no_timetable)
                } else {
                    stringResource(R.string.timetable_count_format, timetableCount)
                },
                icon = R.drawable.ic_list,
                onClick = onTimetableManager,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
            )
        }
        NavigationGroup(title = "课表工具") {
            NavigationRow(icon = R.drawable.ic_import, title = stringResource(R.string.import_timetable), onClick = onImportTimetable)
            NavigationRow(icon = R.drawable.ic_baseline_cloud_download_24, title = "导入 ICS 课表", subtitle = "从日历文件导入课程", onClick = onImportIcs)
        }
        NavigationGroup(title = stringResource(R.string.navi_jw_title)) {
            NavigationRow(icon = R.drawable.ic_baseline_today_24, title = stringResource(R.string.ade_exam), onClick = onExam)
            NavigationRow(icon = R.drawable.ic_baseline_format_list_bulleted_24, title = stringResource(R.string.jw_tabs_cj), onClick = onScores)
            NavigationRow(icon = R.drawable.ic_baseline_location_city_24, title = stringResource(R.string.shortcut_empty_classroom_short), onClick = onEmptyClassroom)
            NavigationRow(icon = R.drawable.ic_baseline_format_list_bulleted_24, title = stringResource(R.string.navi_credit_stats), onClick = onCreditStats)
        }
        NavigationGroup(title = stringResource(R.string.navi_course_resource_title)) {
            NavigationRow(icon = R.drawable.ic_baseline_search_24, title = stringResource(R.string.navi_course_lookup), subtitle = stringResource(R.string.navi_course_lookup_sub), onClick = onCourseLookup)
            NavigationRow(icon = R.drawable.ic_baseline_edit_24, title = stringResource(R.string.navi_course_submit_pr), subtitle = stringResource(R.string.navi_course_submit_pr_sub), onClick = onCourseSubmit)
        }
        NavigationGroup(title = "设置") {
            NavigationRow(
                icon = R.drawable.ic_baseline_access_alarm_24,
                title = "课程提醒",
                subtitle = "上课前10分钟提醒",
                onClick = onToggleReminder,
                trailing = {
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { onToggleReminder() }
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(tokens.spacing.xl))
    }
}

@Composable
private fun UserCard(
    localUser: UserLocal,
    easToken: EASToken,
    onAvatarClick: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = HitaTheme.tokens
    val context = LocalContext.current
    val localAvatar = localUser.avatar?.takeIf { it.startsWith("local://") }
    val isLocalValid = localUser.isValid()
    val title: String
    val subtitle: String
    val avatarSource: String?
    if (isLocalValid) {
        title = localUser.nickname.orEmpty()
        subtitle = localUser.username.orEmpty()
        avatarSource = localAvatar ?: localUser.avatar
    } else if (easToken.isLogin()) {
        title = easToken.name?.ifBlank { easToken.stuId?.ifBlank { easToken.username } }
            ?: easToken.stuId?.ifBlank { easToken.username }
            ?: easToken.username
            ?: stringResource(R.string.eas_account_not_logged_in_title)
        subtitle = buildString {
            val primary = easToken.stuId?.trim().orEmpty()
            val secondary = listOf(easToken.school, easToken.major, easToken.grade, easToken.className)
                .mapNotNull { info -> info?.trim()?.takeIf(String::isNotBlank) }
                .joinToString(" · ")
            append(primary)
            if (secondary.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(secondary)
            }
        }.ifBlank { easToken.username.orEmpty() }
        avatarSource = localAvatar
    } else {
        title = stringResource(R.string.eas_account_not_logged_in_title)
        subtitle = stringResource(R.string.eas_account_not_logged_in_subtitle)
        avatarSource = localAvatar
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(tokens.radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
                factory = {
                    ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { imageView ->
                    if (avatarSource.isNullOrBlank()) {
                        imageView.setImageResource(R.drawable.place_holder_avatar)
                    } else {
                        ImageUtils.loadAvatarInto(context, avatarSource, imageView)
                    }
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.md)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.spacing.xs)
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = HitaTheme.tokens
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(tokens.radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIcon(icon = icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.md)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun NavigationGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val tokens = HitaTheme.tokens
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(
            start = tokens.spacing.lg,
            top = tokens.spacing.lg,
            end = tokens.spacing.lg,
            bottom = tokens.spacing.xs
        )
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.lg),
        shape = RoundedCornerShape(tokens.radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = tokens.spacing.xs)
        ) {
            content()
        }
    }
}

@Composable
private fun NavigationRow(
    icon: Int,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tokens = HitaTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIcon(icon = icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = tokens.spacing.md)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CircleIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}
