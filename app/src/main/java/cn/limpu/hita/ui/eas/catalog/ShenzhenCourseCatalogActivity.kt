package cn.limpu.hita.ui.eas.catalog

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limpu.component.data.DataState
import com.limpu.style.widgets.PopUpCheckableList
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.ui.base.ComposeViewBinding
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.ui.design.hitaGlassCardBorder
import cn.limpu.hita.ui.design.hitaGlassCardColors
import cn.limpu.hita.ui.design.hitaGlassCardModifier
import cn.limpu.hita.ui.eas.EASActivity
import cn.limpu.hita.ui.eas.login.PopUpLoginEAS
import cn.limpu.hita.utils.ActivityUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShenzhenCourseCatalogActivity :
    EASActivity<ShenzhenCourseCatalogViewModel, ComposeViewBinding>() {

    override val viewModel: ShenzhenCourseCatalogViewModel by viewModels()
    private var terms by mutableStateOf<List<TermItem>>(emptyList())
    private var uiState by mutableStateOf<CatalogUiState>(CatalogUiState.Loading)

    override fun initViewBinding(): ComposeViewBinding = ComposeViewBinding(ComposeView(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        (binding.root as ComposeView).setContent {
            HitaComposeTheme {
                ShenzhenCourseCatalogScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { finish() },
                    onRefresh = { refresh() },
                    onConnectWeb = { connectWebSession() },
                    onSelectTerm = { showTermPicker() },
                    onSelectPool = { showPoolPicker() },
                    onSelectStudentType = { showStudentTypePicker() }
                )
            }
        }
    }

    private fun bindLiveData() {
        viewModel.termsLiveData.observe(this) { state ->
            when (state.state) {
                DataState.STATE.SUCCESS -> {
                    terms = state.data.orEmpty()
                    uiState = CatalogUiState.Ready(refreshing = true)
                    viewModel.reconcileTerms(terms)
                }
                DataState.STATE.NOT_LOGGED_IN -> {
                    uiState = CatalogUiState.NeedsWebLogin(state.message)
                }
                DataState.STATE.FETCH_FAILED -> {
                    uiState = CatalogUiState.Error(state.message ?: "学期列表加载失败")
                }
                DataState.STATE.NOTHING -> Unit
                else -> {
                    uiState = CatalogUiState.Error(state.message ?: "学期列表暂不可用")
                }
            }
        }
        viewModel.queryLiveData.observe(this) {
            uiState = CatalogUiState.Ready(refreshing = true)
        }
        viewModel.coursesLiveData.observe(this) { state ->
            when (state.state) {
                DataState.STATE.SUCCESS -> {
                    uiState = CatalogUiState.Ready(
                        message = if (state.data?.items.isNullOrEmpty()) {
                            "当前筛选条件下没有课程"
                        } else {
                            null
                        }
                    )
                    resetSessionRetryState()
                }
                DataState.STATE.NOT_LOGGED_IN -> {
                    uiState = CatalogUiState.NeedsWebLogin(state.message)
                }
                DataState.STATE.FETCH_FAILED -> {
                    uiState = CatalogUiState.Ready(
                        message = state.message ?: "课程数据加载失败"
                    )
                }
                DataState.STATE.NOTHING -> Unit
                else -> uiState = CatalogUiState.Ready(
                    message = state.message ?: "课程数据暂不可用"
                )
            }
        }
    }

    override fun refresh() {
        uiState = CatalogUiState.Loading
        viewModel.startRefresh()
    }

    private fun connectWebSession() {
        ActivityUtils.showEasVerifyWindow<Activity>(
            this,
            easRepository,
            preferredCampus = EASToken.Campus.SHENZHEN,
            onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                override fun onSuccess(window: PopUpLoginEAS) {
                    window.dismiss()
                    if (easRepository.hasShenzhenWebSession()) {
                        refresh()
                    } else {
                        Toast.makeText(
                            this@ShenzhenCourseCatalogActivity,
                            "请点击“使用统一身份认证网页登录”完成 Web 会话连接",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailed(window: PopUpLoginEAS) = Unit
            }
        )
    }

    private fun showTermPicker() {
        if (terms.isEmpty()) return
        PopUpCheckableList<TermItem>()
            .setTitle("选择学期")
            .setListData(terms.map { it.name }, terms)
            .setOnConfirmListener(object : PopUpCheckableList.OnConfirmListener<TermItem> {
                override fun OnConfirm(title: String?, key: TermItem) {
                    viewModel.selectTerm(key)
                }
            })
            .show(supportFragmentManager, "shenzhen_catalog_terms")
    }

    private fun showPoolPicker() {
        val labels = viewModel.pools.map { it.name }.toTypedArray()
        val current = viewModel.selectedPoolLiveData.value
        val checked = viewModel.pools.indexOfFirst { it.code == current?.code }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("选择课程类型")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.selectPool(viewModel.pools[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showStudentTypePicker() {
        val labels = arrayOf("本科", "研究生")
        val checked = if (viewModel.studentTypeLiveData.value == "2") 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle("培养层次")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.selectStudentType(if (which == 1) "2" else "1")
                dialog.dismiss()
            }
            .show()
    }
}

private sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class NeedsWebLogin(val message: String?) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
    data class Ready(
        val refreshing: Boolean = false,
        val message: String? = null
    ) : CatalogUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShenzhenCourseCatalogScreen(
    viewModel: ShenzhenCourseCatalogViewModel,
    uiState: CatalogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnectWeb: () -> Unit,
    onSelectTerm: () -> Unit,
    onSelectPool: () -> Unit,
    onSelectStudentType: () -> Unit
) {
    val tokens = HitaTheme.tokens
    val source by viewModel.sourceLiveData.observeAsState(ShenzhenCourseCatalogSource.AVAILABLE)
    val term by viewModel.selectedTermLiveData.observeAsState()
    val pool by viewModel.selectedPoolLiveData.observeAsState()
    val studentType by viewModel.studentTypeLiveData.observeAsState("1")
    val query by viewModel.queryLiveData.observeAsState()
    val pageState by viewModel.coursesLiveData.observeAsState()
    val page = pageState?.data
    val isLoading = uiState is CatalogUiState.Loading ||
        (uiState is CatalogUiState.Ready && uiState.refreshing)
    val errorMessage = when (uiState) {
        is CatalogUiState.Error -> uiState.message
        is CatalogUiState.Ready -> uiState.message
        is CatalogUiState.NeedsWebLogin -> uiState.message
        CatalogUiState.Loading -> null
    }
    var keyword by remember(query?.keyword) { mutableStateOf(query?.keyword.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "深圳课程浏览",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                        contentDescription = "返回",
                        modifier = Modifier.rotate(180f)
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_settings_backup_restore_24),
                            contentDescription = "刷新"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (uiState is CatalogUiState.NeedsWebLogin) {
            WebLoginRequiredCard(
                message = uiState.message,
                onConnectWeb = onConnectWeb,
                modifier = Modifier.padding(tokens.spacing.lg)
            )
            return@Column
        }

        if (uiState is CatalogUiState.Error && page == null) {
            CatalogErrorCard(
                message = uiState.message,
                onRetry = onRefresh,
                modifier = Modifier.padding(tokens.spacing.lg)
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            FilterChip(
                selected = source == ShenzhenCourseCatalogSource.AVAILABLE,
                onClick = { viewModel.selectSource(ShenzhenCourseCatalogSource.AVAILABLE) },
                label = { Text("可选课程") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = source == ShenzhenCourseCatalogSource.SCHOOL,
                onClick = { viewModel.selectSource(ShenzhenCourseCatalogSource.SCHOOL) },
                label = { Text("全校课表") },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "深圳 Web 教务 · 只读浏览，不会提交选课",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = tokens.spacing.xl)
        )

        CatalogFilters(
            termName = term?.name ?: "选择学期",
            secondaryName = if (source == ShenzhenCourseCatalogSource.AVAILABLE) {
                pool?.name ?: "选择课程类型"
            } else if (studentType == "2") {
                "研究生"
            } else {
                "本科"
            },
            keyword = keyword,
            onKeywordChange = { keyword = it },
            onSearch = { viewModel.search(keyword) },
            onSelectTerm = onSelectTerm,
            onSelectSecondary = if (source == ShenzhenCourseCatalogSource.AVAILABLE) {
                onSelectPool
            } else {
                onSelectStudentType
            },
            modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = tokens.spacing.xl, vertical = tokens.spacing.xs)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = tokens.spacing.lg,
                end = tokens.spacing.lg,
                top = tokens.spacing.xs,
                bottom = tokens.spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            if (page != null) {
                item {
                    Text(
                        text = "共 ${page.total} 条 · 第 ${page.page} 页",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                items(page.items, key = { "${it.source}-${it.id}" }) { item ->
                    CourseCatalogCard(item)
                }
                item {
                    PaginationRow(
                        page = page.page,
                        hasNext = page.hasNextPage,
                        onPrevious = viewModel::previousPage,
                        onNext = viewModel::nextPage
                    )
                }
            } else if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun WebLoginRequiredCard(
    message: String?,
    onConnectWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .hitaGlassCardModifier(shape),
        shape = shape,
        colors = hitaGlassCardColors(),
        border = hitaGlassCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Text("需要深圳 Web 会话", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = message ?: "可选课程和全校课表属于 Web 教务独占接口，需要通过统一身份认证连接。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spacing.sm)
            )
            Button(
                onClick = onConnectWeb,
                modifier = Modifier.padding(top = tokens.spacing.lg)
            ) {
                Text("连接深圳 Web 教务")
            }
        }
    }
}

@Composable
private fun CatalogErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .hitaGlassCardModifier(shape),
        shape = shape,
        colors = hitaGlassCardColors(),
        border = hitaGlassCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Text("课程数据暂不可用", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spacing.sm)
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = tokens.spacing.lg)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun CatalogFilters(
    termName: String,
    secondaryName: String,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectTerm: () -> Unit,
    onSelectSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .hitaGlassCardModifier(shape),
        shape = shape,
        colors = hitaGlassCardColors(),
        border = hitaGlassCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                FilterValue(termName, onSelectTerm, Modifier.weight(1f))
                FilterValue(secondaryName, onSelectSecondary, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    label = { Text("课程名 / 代码 / 教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSearch) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_search_24),
                        contentDescription = "搜索"
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterValue(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                painter = painterResource(R.drawable.ic_baseline_arrow_drop_down_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CourseCatalogCard(item: ShenzhenCourseCatalogItem) {
    val tokens = HitaTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hitaGlassCardModifier(shape),
        shape = shape,
        colors = hitaGlassCardColors(),
        border = hitaGlassCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.courseName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (item.courseCode.isNotBlank()) {
                        Text(
                            item.courseCode,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (item.credits.isNotBlank()) {
                    Text("${item.credits} 学分", color = MaterialTheme.colorScheme.primary)
                }
            }
            val metadata = listOf(
                item.teacher,
                item.offeringCollege,
                item.campus,
                item.courseNature,
                item.courseCategory,
                item.teachingLanguage
            ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = tokens.spacing.sm)
                )
            }
            if (item.schedule.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = tokens.spacing.sm),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Text(item.schedule, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.selectionPoolName.isNotBlank()) {
                    Text(
                        item.selectionPoolName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                } else if (item.trainingLevel.isNotBlank()) {
                    Text(item.trainingLevel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                val seats = when {
                    item.selectedCount != null && item.capacity != null ->
                        "已选 ${item.selectedCount} / ${item.capacity}"
                    item.capacity != null -> "容量 ${item.capacity}"
                    else -> ""
                }
                if (seats.isNotBlank()) {
                    val full = item.capacity != null && item.selectedCount != null &&
                        item.selectedCount >= item.capacity
                    Text(
                        seats,
                        color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PaginationRow(
    page: Int,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrevious, enabled = page > 1) { Text("上一页") }
        Spacer(modifier = Modifier.width(16.dp))
        Text("第 $page 页", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedButton(onClick = onNext, enabled = hasNext) { Text("下一页") }
    }
}
