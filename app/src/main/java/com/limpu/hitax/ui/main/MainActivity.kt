package com.limpu.hitax.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.EasSettingsRepository
import com.limpu.hitax.data.repository.KEY_WALLPAPER_PATH
import com.limpu.hitax.data.repository.TimetableStyleRepository
import com.limpu.hitax.ui.about.ActivityAbout
import com.limpu.hitax.ui.about.UserAgreementDialog
import com.limpu.hitax.ui.base.ComposeViewBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.ui.event.add.PopupAddEvent
import com.limpu.hitax.ui.main.agent.AgentChatFragment
import com.limpu.hitax.ui.main.navigation.NavigationFragment
import com.limpu.hitax.ui.main.timeline.FragmentTimeLine
import com.limpu.hitax.ui.main.timetable.TimetableFragment
import com.limpu.hitax.ui.main.timetable.panel.FragmentTimetablePanel
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.LogUtils
import com.limpu.hitax.utils.WallpaperColorAnalyzer
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.style.ThemeTools
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : HiltBaseActivity<ComposeViewBinding>(),
    TimetableFragment.MainPageController, FragmentTimeLine.MainPageController {

    companion object {
        private const val STATE_SELECTED_TAB = "selected_tab"
    }

    @Inject lateinit var localUserRepository: LocalUserRepository
    @Inject lateinit var easRepository: EASRepository
    @Inject lateinit var timetableStyleRepository: TimetableStyleRepository

    protected val viewModel: MainViewModel by viewModels()

    private val autoReimportIntervalMs = 12 * 60 * 60 * 1000L
    private var autoReimportAttempted = false
    private var checkedUpdate = false
    private var lastCheckTs: Long = 0

    private var selectedTab by mutableIntStateOf(0)
    private var drawerOpen by mutableStateOf(false)
    private var todayTitle by mutableStateOf("")
    private var timetableTitle by mutableStateOf("")
    private var timetableDisplayName by mutableStateOf("")
    private var showTimetableName by mutableStateOf(false)
    private var themeIcon by mutableIntStateOf(R.drawable.ic_moon_auto)
    private var wallpaperBitmap by mutableStateOf<Bitmap?>(null)
    private var wallpaperVisible by mutableStateOf(false)
    private var wallpaperScrimOpacity by mutableIntStateOf(0)
    private var wallpaperTitleColor by mutableStateOf(AndroidColor.WHITE)
    private var wallpaperLabelColor by mutableStateOf(AndroidColor.WHITE)

    private val easTokenObserver = Observer<com.limpu.hitax.data.model.eas.EASToken> {
        refreshDrawerState()
    }

    private var drawerState by mutableStateOf(DrawerUserState())

    override fun initViewBinding(): ComposeViewBinding {
        return ComposeViewBinding(ComposeView(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB, selectedTab) ?: selectedTab
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = AndroidColor.TRANSPARENT
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    override fun initViews() {
        todayTitle = getString(R.string.maintab_today)
        (binding.root as ComposeView).setContent {
            HitaComposeTheme() {
                MainScreen(
                    selectedTab = selectedTab,
                    drawerOpen = drawerOpen,
                    todayTitle = todayTitle,
                    timetableTitle = timetableTitle,
                    timetableName = timetableDisplayName,
                    showTimetableName = showTimetableName,
                    themeIcon = themeIcon,
                    wallpaperBitmap = wallpaperBitmap,
                    wallpaperVisible = wallpaperVisible,
                    wallpaperScrimOpacity = wallpaperScrimOpacity,
                    wallpaperTitleColor = Color(wallpaperTitleColor),
                    wallpaperLabelColor = Color(wallpaperLabelColor),
                    drawerState = drawerState,
                    onSelectTab = { selectedTab = it },
                    onOpenDrawer = { drawerOpen = true },
                    onCloseDrawer = { drawerOpen = false },
                    onTheme = {
                        ThemeTools.switchTheme(getThis())
                        WidgetUtils.sendRefreshToAll(this)
                        refreshTheme()
                    },
                    onWallpaper = { pickWallpaperLauncher.launch("image/*") },
                    onWallpaperLongPress = { showWallpaperMenu() },
                    onTimetableSetting = { FragmentTimetablePanel().show(supportFragmentManager, "panel") },
                    onAgentShortcut = { selectedTab = 2 },
                    onAddEvent = { PopupAddEvent().show(supportFragmentManager, "add_event") },
                    onDrawerHeader = { openDrawerHeader() },
                    onDrawerTimetableManager = { ActivityUtils.startTimetableManager(getThis()) },
                    onDrawerAgreement = { UserAgreementDialog().show(supportFragmentManager, "ua") },
                    onDrawerAbout = { ActivityUtils.startActivity(getThis(), ActivityAbout::class.java) },
                    fragmentFactory = { position ->
                        when (position) {
                            0 -> FragmentTimeLine()
                            1 -> TimetableFragment()
                            2 -> AgentChatFragment()
                            else -> NavigationFragment()
                        }
                    }
                )
            }
        }

        timetableStyleRepository.wallpaperPathLiveData.observe(this) { loadWallpaper(it) }
        timetableStyleRepository.wallpaperScrimLiveData.observe(this) { opacity ->
            wallpaperScrimOpacity = opacity
        }
        timetableStyleRepository.wallpaperDateColorLiveData.observe(this) { color ->
            wallpaperTitleColor = color
        }
        timetableStyleRepository.wallpaperLabelColorLiveData.observe(this) { color ->
            wallpaperLabelColor = color
        }
        viewModel.checkUpdateResult.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { cr ->
                    if (cr.shouldUpdate) ActivityUtils.showUpdateNotification(cr, this)
                }
            }
        }
        viewModel.loggedInUserLiveData.observe(this) {
            refreshDrawerState()
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        viewModel.startRefreshUser()
        refreshTheme()
        refreshDrawerState()
        easRepository.observeEasToken().observe(this, easTokenObserver)
        maybeAutoReimportTimetable()
        try {
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
            if (System.currentTimeMillis() - lastCheckTs > 5 * 60 * 1000) checkedUpdate = false
            if (!checkedUpdate) {
                if (localUserRepository.getLoggedInUser().isValid()) {
                    checkedUpdate = true
                    lastCheckTs = System.currentTimeMillis()
                }
                viewModel.checkForUpdate(code)
            }
        } catch (e: Exception) {
            LogUtils.e("Failed to get package info for update check", e)
        }
    }

    override fun onStop() {
        easRepository.observeEasToken().removeObserver(easTokenObserver)
        super.onStop()
    }

    private fun maybeAutoReimportTimetable() {
        val settings = EasSettingsRepository(application)
        if (!settings.isAutoReimportEnabled()) return
        val token = easRepository.getEasToken()
        if (!token.isLogin()) return
        if (autoReimportAttempted) return
        val now = System.currentTimeMillis()
        val last = settings.getLastAutoReimportTs()
        if (now - last < autoReimportIntervalMs) return
        autoReimportAttempted = true
        val isUndergrad = token.stutype == com.limpu.hitax.data.model.eas.EASToken.TYPE.UNDERGRAD
        easRepository.startAutoImportCurrentTimetable(isUndergrad) { success ->
            if (success) settings.setLastAutoReimportTs(System.currentTimeMillis())
        }
    }

    private fun refreshTheme() {
        themeIcon = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> R.drawable.ic_moon2
            ThemeTools.MODE.LIGHT -> R.drawable.ic_sun
            else -> R.drawable.ic_moon_auto
        }
    }

    private fun refreshDrawerState() {
        val localUser = localUserRepository.getLoggedInUser()
        if (localUser.isValid()) {
            drawerState = DrawerUserState(
                title = localUser.username.orEmpty(),
                subtitle = localUser.nickname.orEmpty(),
                avatar = localUser.avatar,
                loggedInLocalUser = true
            )
            return
        }

        val easToken = easRepository.getEasToken()
        if (easToken.isLogin()) {
            drawerState = DrawerUserState(
                title = easToken.name?.ifBlank { easToken.stuId?.ifBlank { easToken.username } }
                    ?: easToken.stuId?.ifBlank { easToken.username }
                    ?: easToken.username
                    ?: getString(R.string.eas_account_not_logged_in_title),
                subtitle = buildString {
                    val primary = easToken.stuId?.trim().orEmpty()
                    val secondary = listOf(
                        easToken.school,
                        easToken.major,
                        easToken.grade,
                        easToken.className
                    ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                        .joinToString(" · ")
                    append(primary)
                    if (secondary.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(secondary)
                    }
                }.ifBlank { easToken.username.orEmpty() },
                avatar = null,
                loggedInLocalUser = false
            )
        } else {
            drawerState = DrawerUserState(
                title = getString(R.string.eas_account_not_logged_in_title),
                subtitle = getString(R.string.eas_account_not_logged_in_subtitle),
                avatar = null,
                loggedInLocalUser = false
            )
        }
    }

    private fun openDrawerHeader() {
        val localUser = localUserRepository.getLoggedInUser()
        if (localUser.isValid()) {
            ActivityUtils.startProfileActivity(getThis(), localUser.id, null)
        } else {
            ActivityUtils.showEasVerifyWindow<Activity>(
                this,
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

    private fun showWallpaperMenu() {
        AlertDialog.Builder(this)
            .setTitle(R.string.timetable_wallpaper)
            .setItems(arrayOf(getString(R.string.wallpaper_remove))) { _, _ ->
                Thread {
                    filesDir.listFiles()?.filter {
                        it.name.startsWith("timetable_wallpaper")
                    }?.forEach { it.delete() }
                }.start()
                timetableStyleRepository.putData(KEY_WALLPAPER_PATH, "")
            }
            .show()
    }

    private fun loadWallpaper(path: String?) {
        if (path.isNullOrEmpty()) {
            wallpaperBitmap = null
            wallpaperVisible = false
            timetableStyleRepository.wallpaperDateColorLiveData.value = AndroidColor.WHITE
            timetableStyleRepository.wallpaperLabelColorLiveData.value = AndroidColor.WHITE
            return
        }
        val file = File(path.removePrefix("local://"))
        if (!file.exists()) {
            timetableStyleRepository.putData(KEY_WALLPAPER_PATH, "")
            wallpaperBitmap = null
            wallpaperVisible = false
            return
        }
        wallpaperVisible = true
        Glide.with(this)
            .asBitmap()
            .load(file)
            .centerCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    wallpaperBitmap = resource
                    val dateColor = WallpaperColorAnalyzer.sampleRegion(resource, 0f, 0f, 1f, 0.12f)
                    val labelColor = WallpaperColorAnalyzer.sampleRegion(resource, 0f, 0.12f, 0.08f, 0.88f)
                    timetableStyleRepository.wallpaperDateColorLiveData.value = dateColor
                    timetableStyleRepository.wallpaperLabelColorLiveData.value = labelColor
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    wallpaperBitmap = null
                }
            })
    }

    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveWallpaperLocally(it) }
    }

    private fun saveWallpaperLocally(uri: Uri) {
        Thread {
            try {
                val oldPrefix = "timetable_wallpaper"
                filesDir.listFiles()?.filter {
                    it.name.startsWith(oldPrefix)
                }?.forEach { it.delete() }

                val destFile = File(filesDir, "timetable_wallpaper_${System.currentTimeMillis()}")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open wallpaper input stream")
                timetableStyleRepository.putData(KEY_WALLPAPER_PATH, "local://${destFile.absolutePath}")
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.wallpaper_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (drawerOpen) {
                drawerOpen = false
                return
            }
            val intent = Intent(Intent.ACTION_MAIN)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addCategory(Intent.CATEGORY_HOME)
            startActivity(intent)
        }
    }

    override fun setTitleText(string: String) {
        timetableTitle = string
        showTimetableName = true
    }

    override fun setTimetableName(String: String) {
        timetableDisplayName = String
        showTimetableName = true
    }

    override fun setSingleTitle(string: String) {
        timetableTitle = string
        showTimetableName = false
    }

    override fun setTimelineTitleText(string: String) {
        todayTitle = string
    }
}

private data class DrawerUserState(
    val title: String = "",
    val subtitle: String = "",
    val avatar: String? = null,
    val loggedInLocalUser: Boolean = false,
)

private data class MainTabSpec(
    val titleRes: Int,
    val iconRes: Int,
)

@Composable
private fun MainScreen(
    selectedTab: Int,
    drawerOpen: Boolean,
    todayTitle: String,
    timetableTitle: String,
    timetableName: String,
    showTimetableName: Boolean,
    themeIcon: Int,
    wallpaperBitmap: Bitmap?,
    wallpaperVisible: Boolean,
    wallpaperScrimOpacity: Int,
    wallpaperTitleColor: Color,
    wallpaperLabelColor: Color,
    drawerState: DrawerUserState,
    onSelectTab: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onTheme: () -> Unit,
    onWallpaper: () -> Unit,
    onWallpaperLongPress: () -> Unit,
    onTimetableSetting: () -> Unit,
    onAgentShortcut: () -> Unit,
    onAddEvent: () -> Unit,
    onDrawerHeader: () -> Unit,
    onDrawerTimetableManager: () -> Unit,
    onDrawerAgreement: () -> Unit,
    onDrawerAbout: () -> Unit,
    fragmentFactory: (Int) -> Fragment,
) {
    val density = LocalDensity.current
    val drawerWidth = 260.dp
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    val drawerProgress by animateFloatAsState(if (drawerOpen) 1f else 0f, label = "drawer")
    val contentScale = 1f - drawerProgress * 0.2f
    val contentOffset = -drawerWidthPx * drawerProgress
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val threeButtonThresholdPx = with(density) { 32.dp.toPx() }
    val systemNavAvoidance = if (navBottomPx >= threeButtonThresholdPx) {
        with(density) { navBottomPx.toDp() } + 8.dp
    } else {
        12.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectedTab == 1 && wallpaperVisible && wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.timetable_wallpaper_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperScrimOpacity / 100f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp)
                .graphicsLayer {
                    translationX = contentOffset
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                }
        ) {
            MainTopBar(
                selectedTab = selectedTab,
                todayTitle = todayTitle,
                timetableTitle = timetableTitle,
                timetableName = timetableName,
                showTimetableName = showTimetableName,
                themeIcon = themeIcon,
                wallpaperVisible = wallpaperVisible,
                wallpaperTitleColor = wallpaperTitleColor,
                onOpenDrawer = onOpenDrawer,
                onTheme = onTheme,
                onWallpaper = onWallpaper,
                onWallpaperLongPress = onWallpaperLongPress,
                onTimetableSetting = onTimetableSetting,
                onAgentShortcut = onAgentShortcut,
                onAddEvent = onAddEvent,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MainFragmentPager(
                    selectedTab = selectedTab,
                    fragmentFactory = fragmentFactory,
                    modifier = Modifier.fillMaxSize()
                )

                val overlayAlpha = remember { Animatable(0f) }
                val prevTab = remember { mutableIntStateOf(selectedTab) }

                LaunchedEffect(selectedTab) {
                    if (prevTab.intValue != selectedTab) {
                        overlayAlpha.snapTo(1f)
                        delay(100)
                        overlayAlpha.animateTo(0f, tween(250))
                    }
                    prevTab.intValue = selectedTab
                }

                if (overlayAlpha.value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = overlayAlpha.value }
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }

        if (!imeVisible) {
            MainPillTabBar(
                selectedTab = selectedTab,
                alpha = if (selectedTab == 1 && wallpaperVisible) 0.72f else 1f,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = systemNavAvoidance)
            )
        }

        if (drawerProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f * drawerProgress))
                    .pointerInput(drawerOpen) {
                        detectTapGestures { onCloseDrawer() }
                    }
            )
        }

        MainDrawer(
            drawerState = drawerState,
            onHeaderClick = onDrawerHeader,
            onTimetableManager = onDrawerTimetableManager,
            onAgreement = onDrawerAgreement,
            onAbout = onDrawerAbout,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(drawerWidth)
                .fillMaxHeight()
                .offset { IntOffset(((1f - drawerProgress) * drawerWidthPx).roundToInt(), 0) }
        )
    }
}

@Composable
private fun MainTopBar(
    selectedTab: Int,
    todayTitle: String,
    timetableTitle: String,
    timetableName: String,
    showTimetableName: Boolean,
    themeIcon: Int,
    wallpaperVisible: Boolean,
    wallpaperTitleColor: Color,
    onOpenDrawer: () -> Unit,
    onTheme: () -> Unit,
    onWallpaper: () -> Unit,
    onWallpaperLongPress: () -> Unit,
    onTimetableSetting: () -> Unit,
    onAgentShortcut: () -> Unit,
    onAddEvent: () -> Unit,
) {
    val isWallpaperTab = selectedTab == 1 && wallpaperVisible
    val titleColor = if (isWallpaperTab) wallpaperTitleColor else MaterialTheme.colorScheme.onSurface

    // Wallpaper tab: force light status bar icons on dark overlay; restore otherwise
    val view = LocalView.current
    LaunchedEffect(isWallpaperTab) {
        val window = (view.context as android.app.Activity).window
        @Suppress("DEPRECATION")
        val ctrl = androidx.core.view.WindowInsetsControllerCompat(window, view)
        if (isWallpaperTab) {
            ctrl.isAppearanceLightStatusBars = false // light icons on dark overlay
        } else {
            val isDark = (view.context.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            ctrl.isAppearanceLightStatusBars = !isDark
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(bottom = 4.dp)
            .padding(start = HitaTheme.tokens.spacing.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        when (selectedTab) {
            0 -> ToolbarTitle(todayTitle, titleColor)
            1 -> TimetableToolbarTitle(
                title = timetableTitle,
                name = timetableName,
                showName = showTimetableName,
                titleColor = titleColor,
                onWallpaper = onWallpaper,
                onWallpaperLongPress = onWallpaperLongPress,
                onTimetableSetting = onTimetableSetting,
                onAgentShortcut = onAgentShortcut,
                onAddEvent = onAddEvent
            )
            2 -> ToolbarTitle(stringResource(R.string.title_agent), titleColor)
            else -> NavigationToolbar(
                themeIcon = themeIcon,
                onTheme = onTheme,
                onOpenDrawer = onOpenDrawer
            )
        }
    }
}

@Composable
private fun ToolbarTitle(title: String, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier = Modifier
            .height(56.dp)
            .padding(start = HitaTheme.tokens.spacing.sm),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TimetableToolbarTitle(
    title: String,
    name: String,
    showName: Boolean,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onWallpaper: () -> Unit,
    onWallpaperLongPress: () -> Unit,
    onTimetableSetting: () -> Unit,
    onAgentShortcut: () -> Unit,
    onAddEvent: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(visible = showName && name.isNotBlank()) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = HitaTheme.tokens.spacing.md)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ToolbarIcon(R.drawable.ic_wallpaper, onClick = onWallpaper, onLongClick = onWallpaperLongPress)
        ToolbarIcon(R.drawable.ic_theme, onClick = onTimetableSetting)
        ToolbarIcon(R.drawable.ic_baseline_toys_24, onClick = onAgentShortcut)
        ToolbarIcon(R.drawable.ic_baseline_add_24, onClick = onAddEvent)
    }
}

@Composable
private fun NavigationToolbar(
    themeIcon: Int,
    onTheme: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToolbarTitle(stringResource(R.string.title_navigation))
        Spacer(modifier = Modifier.weight(1f))
        ToolbarIcon(themeIcon, onClick = onTheme)
        ToolbarIcon(R.drawable.ic_baseline_menu_24, onClick = onOpenDrawer)
    }
}

@Composable
private fun ToolbarIcon(
    iconRes: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .pointerInput(iconRes) {
                detectTapGestures(
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onLongClick?.invoke()
                    },
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MainFragmentPager(
    selectedTab: Int,
    fragmentFactory: (Int) -> Fragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val containerIds = remember {
        IntArray(4) { View.generateViewId() }
    }
    val createdTabs = remember {
        mutableStateListOf(0)
    }
    val committedTags = remember {
        mutableSetOf<String>()
    }
    LaunchedEffect(selectedTab) {
        if (!createdTabs.contains(selectedTab)) {
            createdTabs.add(selectedTab)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            android.widget.FrameLayout(it).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                repeat(4) { index ->
                    addView(
                        FragmentContainerView(it).apply {
                            id = containerIds[index]
                            visibility = if (index == selectedTab) View.VISIBLE else View.GONE
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    )
                }
            }
        },
        update = { root ->
            repeat(4) { index ->
                val container = root.findViewById<FragmentContainerView>(containerIds[index])
                container.visibility = if (index == selectedTab) View.VISIBLE else View.GONE
                if (!createdTabs.contains(index)) return@repeat
                val tag = "main_tab_$index"
                if (tag !in committedTags) {
                    val existing = activity.supportFragmentManager.findFragmentByTag(tag)
                    if (existing != null) {
                        activity.supportFragmentManager.beginTransaction()
                            .remove(existing)
                            .commitNowAllowingStateLoss()
                    }
                    activity.supportFragmentManager.beginTransaction()
                        .replace(containerIds[index], fragmentFactory(index), tag)
                        .commitNowAllowingStateLoss()
                    committedTags.add(tag)
                }
            }
        }
    )
}

private val CapsuleBlue = Color(0xFF3390EC)
private val ActiveBlue = Color(0xFF0088CC)
private val CapsuleTabWidth = 68.dp
private val CapsuleTabIconSize = 18.dp

@Composable
private fun MainPillTabBar(
    selectedTab: Int,
    alpha: Float,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember {
        listOf(
            MainTabSpec(R.string.title_timeline, R.drawable.ic_nav_today),
            MainTabSpec(R.string.title_timetable, R.drawable.ic_nav_timetable),
            MainTabSpec(R.string.title_agent, R.drawable.ic_baseline_toys_24),
            MainTabSpec(R.string.title_navigation, R.drawable.ic_nav_navigation),
        )
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val tabWidthPx = with(density) { CapsuleTabWidth.toPx() }

    val indicatorOffsetPx by animateFloatAsState(
        targetValue = selectedTab * tabWidthPx,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f),
        label = "pill_offset"
    )

    Surface(
        modifier = modifier.alpha(alpha),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Surface(
                modifier = Modifier
                    .graphicsLayer { translationX = indicatorOffsetPx }
                    .width(CapsuleTabWidth)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = CapsuleBlue.copy(alpha = 0.12f)
            ) {}

            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    val active = index == selectedTab

                    val tint by animateColorAsState(
                        targetValue = if (active) ActiveBlue else MaterialTheme.colorScheme.onSurface,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f),
                        label = "tab_tint_$index"
                    )

                    Column(
                        modifier = Modifier
                            .width(CapsuleTabWidth)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onSelectTab(index)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(CapsuleTabIconSize)
                        )
                        Text(
                            text = stringResource(tab.titleRes),
                            color = tint,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainDrawer(
    drawerState: DrawerUserState,
    onHeaderClick: () -> Unit,
    onTimetableManager: () -> Unit,
    onAgreement: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        DrawerHeader(drawerState = drawerState, onClick = onHeaderClick)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = HitaTheme.tokens.spacing.xl)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        )
        DrawerItem(R.drawable.ic_menu_settings, stringResource(R.string.menu_timeable_curriculum), onTimetableManager)
        DrawerItem(R.drawable.ic_info, stringResource(R.string.name_ua_and_pp), onAgreement)
        DrawerItem(R.drawable.logo, stringResource(R.string.main_drawer_menu_about), onAbout)
    }
}

@Composable
private fun DrawerHeader(drawerState: DrawerUserState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 78.dp, bottom = HitaTheme.tokens.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.size(72.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { image ->
                    val avatar = drawerState.avatar
                    if (!avatar.isNullOrBlank()) {
                        com.limpu.hitauser.util.ImageUtils.loadAvatarInto(image.context, avatar, image)
                    } else {
                        image.setImageResource(R.drawable.place_holder_avatar)
                    }
                }
            )
        }
        Text(
            text = drawerState.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = HitaTheme.tokens.spacing.lg,
                top = HitaTheme.tokens.spacing.lg,
                end = HitaTheme.tokens.spacing.lg
            )
        )
        Text(
            text = drawerState.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alpha(0.6f)
                .padding(
                    start = HitaTheme.tokens.spacing.lg,
                    top = HitaTheme.tokens.spacing.sm,
                    end = HitaTheme.tokens.spacing.lg
                )
        )
    }
}

@Composable
private fun DrawerItem(icon: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
