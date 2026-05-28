package com.limpu.hitax.ui.teacher

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.style.ThemeTools
import dagger.hilt.android.AndroidEntryPoint

@Suppress("DEPRECATION")
@AndroidEntryPoint
open class ActivityTeacherOfficial : AppCompatActivity() {
    private val viewModel: TeacherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val nightMode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        super.onCreate(savedInstanceState)

        setContent {
            HitaComposeTheme() {
                TeacherOfficialScreen(
                    viewModel = viewModel,
                    teacherId = intent.getStringExtra("id").orEmpty(),
                    teacherUrl = intent.getStringExtra("url").orEmpty(),
                    teacherName = intent.getStringExtra("name").orEmpty(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onContact = { profile ->
                        TeacherContactFragment.newInstance(profile)
                            .show(supportFragmentManager, "ftc")
                    }
                )
            }
        }
    }
}

@Composable
private fun TeacherOfficialScreen(
    viewModel: TeacherViewModel,
    teacherId: String,
    teacherUrl: String,
    teacherName: String,
    onBack: () -> Unit,
    onContact: (Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val tokens = HitaTheme.tokens
    val teacherKey by viewModel.teacherKeyLiveData.observeAsState()
    val profileState by viewModel.teacherProfileLiveData.observeAsState()
    val pagesState by viewModel.teacherPagesLiveData.observeAsState()
    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(teacherId, teacherUrl, teacherName) {
        if (teacherId.isNotBlank() && teacherUrl.isNotBlank()) {
            loading = true
            viewModel.startRefresh(teacherId, teacherUrl, teacherName)
        }
    }

    LaunchedEffect(profileState) {
        val state = profileState ?: return@LaunchedEffect
        if (state.state == DataState.STATE.SUCCESS) {
            profile = state.data.orEmpty()
        }
    }

    LaunchedEffect(pagesState) {
        val state = pagesState ?: return@LaunchedEffect
        loading = false
        if (state.state == DataState.STATE.SUCCESS) {
            pages = state.data.orEmpty()
            if (selectedIndex >= pages.size) {
                selectedIndex = 0
            }
        } else {
            pages = emptyMap()
            Toast.makeText(context, R.string.fail, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TeacherHeader(
                teacherId = teacherKey?.id ?: teacherId,
                teacherName = teacherKey?.name?.ifBlank { teacherName } ?: teacherName,
                profile = profile,
                onBack = onBack
            )
            TeacherTabs(
                titles = pages.keys.toList(),
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (pages.isEmpty() && !loading) {
                    EmptyTeacherPage(modifier = Modifier.align(Alignment.TopCenter))
                } else {
                    val selectedContent = pages.values.toList().getOrNull(selectedIndex).orEmpty()
                    HtmlPage(
                        html = selectedContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(tokens.spacing.xl)
                    )
                }
            }
        }

        if (profile.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { onContact(profile) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_email_24),
                        contentDescription = null
                    )
                },
                text = { Text(text = stringResource(R.string.contact)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(tokens.spacing.xl)
            )
        }

        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun TeacherHeader(
    teacherId: String,
    teacherName: String,
    profile: Map<String, String>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = HitaTheme.tokens
    val post = profile["post"].orEmpty()
    val position = profile["position"].orEmpty()
    val label = profile["label"].orEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = tokens.spacing.xl)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.rotate(180f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = tokens.spacing.xl,
                    end = tokens.spacing.xl,
                    bottom = tokens.spacing.lg
                )
        ) {
            Card(
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.size(84.dp)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    factory = {
                        ImageView(it).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { imageView ->
                        Glide.with(context)
                            .load("http://faculty.hitsz.edu.cn/file/showHP.do?d=$teacherId&&w=200&&h=200&&prevfix=200-")
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.drawable.place_holder_avatar)
                            .into(imageView)
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = tokens.spacing.xl)
            ) {
                Text(
                    text = teacherName,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (post.isNotBlank()) {
                    TeacherChip(
                        text = post,
                        modifier = Modifier.padding(start = tokens.spacing.sm)
                    )
                }
            }
            if (position.isNotBlank()) {
                Text(
                    text = position,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = tokens.spacing.sm)
                )
            }
            if (label.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(tokens.radius.full),
                    modifier = Modifier.padding(top = tokens.spacing.sm)
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = tokens.spacing.sm,
                            vertical = tokens.spacing.xs
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
        shape = RoundedCornerShape(tokens.radius.full),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xs
            )
        )
    }
}

@Composable
private fun TeacherTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.lg)
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(tokens.radius.full),
                modifier = Modifier
                    .padding(end = tokens.spacing.md)
                    .clickable { onSelect(index) }
            ) {
                Text(
                    text = title,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.sm,
                        vertical = tokens.spacing.sm
                    )
                )
            }
        }
    }
}

@Composable
private fun HtmlPage(
    html: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = 16f
                setTextIsSelectable(true)
                autoLinkMask = android.text.util.Linkify.WEB_URLS or
                    android.text.util.Linkify.MAP_ADDRESSES or
                    android.text.util.Linkify.EMAIL_ADDRESSES
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgbCompat())
            textView.text = Html.fromHtml(html)
        }
    )
}

@Composable
private fun EmptyTeacherPage(modifier: Modifier = Modifier) {
    val tokens = HitaTheme.tokens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = 36.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(84.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_empty),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(18.dp)
            )
        }
        Text(
            text = stringResource(R.string.teacher_no_page),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = tokens.spacing.md)
        )
    }
}

private fun Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
