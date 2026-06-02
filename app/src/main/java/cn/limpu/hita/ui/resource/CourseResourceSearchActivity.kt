package cn.limpu.hita.ui.resource

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limpu.component.data.DataState
import cn.limpu.hita.R
import cn.limpu.hita.data.model.resource.CourseResourceItem
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.utils.ActivityUtils
import cn.limpu.hita.utils.CourseCodeUtils
import com.limpu.style.ThemeTools
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CourseResourceSearchActivity : AppCompatActivity() {
    private val viewModel: CourseResourceSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val nightMode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        super.onCreate(savedInstanceState)

        val mode = runCatching {
            ActivityUtils.CourseResourceMode.valueOf(
                intent.getStringExtra("mode") ?: ActivityUtils.CourseResourceMode.VIEW.name
            )
        }.getOrDefault(ActivityUtils.CourseResourceMode.VIEW)

        setContent {
            HitaComposeTheme() {
                CourseResourceSearchScreen(
                    viewModel = viewModel,
                    mode = mode,
                    initialQuery = intent.getStringExtra("query").orEmpty(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenExternalResource = {
                        startActivity(Intent(this, UnifiedResourceSearchActivity::class.java))
                    },
                    onOpenResource = { item ->
                        if (
                            mode == ActivityUtils.CourseResourceMode.SUBMIT &&
                            item.repoType != "multi-project"
                        ) {
                            ActivityUtils.startCourseContributionActivity(
                                this,
                                item.repoName,
                                item.courseName,
                                item.courseCode,
                                item.repoType,
                            )
                        } else {
                            ActivityUtils.startCourseReadmeActivity(
                                this,
                                item.repoName,
                                item.courseName,
                                item.courseCode,
                                item.repoType,
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseResourceSearchScreen(
    viewModel: CourseResourceSearchViewModel,
    mode: ActivityUtils.CourseResourceMode,
    initialQuery: String,
    onBack: () -> Unit,
    onOpenExternalResource: () -> Unit,
    onOpenResource: (CourseResourceItem) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val tokens = HitaTheme.tokens
    val state by viewModel.resultsLiveData.observeAsState()
    var query by remember(initialQuery) {
        val normalized = CourseCodeUtils.normalize(initialQuery) ?: initialQuery
        mutableStateOf(normalized)
    }
    var items by remember { mutableStateOf(emptyList<CourseResourceItem>()) }
    var emptyText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun startSearch() {
        val normalized = CourseCodeUtils.normalize(query.trim()) ?: query.trim()
        query = normalized
        keyboardController?.hide()
        loading = true
        emptyText = ""
        viewModel.search(normalized)
    }

    LaunchedEffect(Unit) {
        viewModel.search(query)
    }

    LaunchedEffect(state) {
        val current = state ?: return@LaunchedEffect
        loading = false
        if (current.state == DataState.STATE.SUCCESS) {
            items = current.data.orEmpty()
            emptyText = if (items.isEmpty()) {
                context.getString(R.string.course_resource_empty)
            } else {
                ""
            }
        } else {
            items = emptyList()
            emptyText = context.getString(R.string.course_resource_failed)
            current.message?.takeIf { it.isNotBlank() }?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
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
                    text = stringResource(
                        if (mode == ActivityUtils.CourseResourceMode.SUBMIT) {
                            R.string.course_resource_submit_title
                        } else {
                            R.string.course_resource_title
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            actions = {
                IconButton(onClick = onOpenExternalResource) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_menu_24),
                        contentDescription = stringResource(R.string.external_resource_toolbar_entry),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        CourseResourceSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = { startSearch() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg)
        )

        Spacer(modifier = Modifier.height(tokens.spacing.sm))

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { "${it.repoType}:${it.repoName}:${it.courseCode}" }) { item ->
                    CourseResourceCard(
                        item = item,
                        onClick = { onOpenResource(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(tokens.spacing.lg))
                }
            }

            when {
                loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                emptyText.isNotBlank() -> {
                    Text(
                        text = emptyText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(tokens.spacing.xl)
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseResourceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = tokens.spacing.sm,
                end = tokens.spacing.md
            )
        ) {
            IconButton(onClick = onSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_search_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = tokens.spacing.md),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isBlank()) {
                            Text(
                                text = stringResource(R.string.course_resource_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun CourseResourceCard(
    item: CourseResourceItem,
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
            .padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.spacing.sm
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(tokens.spacing.lg)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_menu_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(46.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.lg)
            ) {
                Text(
                    text = item.courseName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = resourceSubtitle(item),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.spacing.xs)
                )
                ResourceTag(
                    text = if (item.repoType == "multi-project") "多课程" else "课程",
                    modifier = Modifier.padding(top = tokens.spacing.sm)
                )
            }
        }
    }
}

@Composable
private fun ResourceTag(
    text: String,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Surface(
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(tokens.radius.full),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xs
            )
        )
    }
}

private fun resourceSubtitle(item: CourseResourceItem): String {
    return listOf(
        item.courseCode,
        item.teachers.take(3).joinToString(" / ")
    ).filter { it.isNotBlank() }
        .joinToString("  ·  ")
}
