package cn.limpu.hita.ui.news.lecture

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.limpu.component.data.DataState
import cn.limpu.hita.R
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.utils.ActivityUtils
import cn.limpu.hita.utils.TimeTools
import cn.limpu.hita.utils.TimeTools.TTY_REPLACE
import com.limpu.style.ThemeTools
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActivityLecture : AppCompatActivity() {
    private val viewModel: LectureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)

        setContent {
            HitaComposeTheme() {
                LectureScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenLecture = { data ->
                        ActivityUtils.startNewsActivity(
                            this,
                            data["link"].orEmpty(),
                            data["title"].orEmpty()
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LectureScreen(
    viewModel: LectureViewModel,
    onBack: () -> Unit,
    onOpenLecture: (Map<String, String>) -> Unit
) {
    val tokens = HitaTheme.tokens
    val listState = rememberLazyListState()
    val items = remember { mutableStateListOf<Map<String, String>>() }
    val state by viewModel.listData.observeAsState()
    var firstLoad by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state) {
        val current = state ?: return@LaunchedEffect
        loadingMore = false
        if (current.state == DataState.STATE.SUCCESS) {
            val data = current.data.orEmpty()
            if (current.listAction == DataState.LIST_ACTION.APPEND) {
                items.addAll(data)
            } else {
                items.clear()
                items.addAll(data)
            }
            firstLoad = false
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && last >= items.lastIndex && !loadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadingMore = true
            viewModel.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.lecture_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.rotate(180f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { _, item ->
                LectureCard(
                    data = item,
                    onClick = { onOpenLecture(item) },
                    modifier = Modifier.padding(
                        start = tokens.spacing.lg,
                        top = tokens.spacing.lg,
                        end = tokens.spacing.lg
                    )
                )
            }
            if (firstLoad || loadingMore) {
                item {
                    Text(
                        text = if (firstLoad) "" else "加载中...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(tokens.spacing.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun LectureCard(
    data: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(tokens.radius.lg),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            val picture = data["picture"]
            if (!picture.isNullOrBlank()) {
                GlideImage(
                    url = picture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(tokens.spacing.lg)
                ) {
                    data["title"]?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    data["time"]?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = tokens.spacing.sm)
                        )
                    }
                    data["place"]?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = tokens.spacing.sm)
                        )
                    }
                }
                Text(
                    text = lectureDateText(data),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = tokens.spacing.lg)
                        .clip(RoundedCornerShape(tokens.radius.xl))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(
                            horizontal = tokens.spacing.md,
                            vertical = 6.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun lectureDateText(data: Map<String, String>): String {
    val context = LocalContext.current
    val date = data["date"].orEmpty()
    return if (date.startsWith("!")) {
        val ts = date.substring(1).toLongOrNull() ?: 0L
        TimeTools.getDateString(context, ts, true, TTYMode = TTY_REPLACE)
    } else {
        date.drop(1)
    }
}

@Composable
private fun GlideImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            Glide.with(context).load(url).centerCrop().into(imageView)
        }
    )
}
