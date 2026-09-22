package cn.limpu.hita.ui.notice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import cn.limpu.hita.R
import cn.limpu.hita.data.analytics.UsageAnalyticsClient
import cn.limpu.hita.data.analytics.UsageAnalyticsDimensions
import cn.limpu.hita.data.analytics.UsageAnalyticsEvent
import cn.limpu.hita.data.notice.AppNotice
import cn.limpu.hita.data.notice.AppNoticeCenter
import cn.limpu.hita.ui.design.HitaComposeTheme

/** 公告列表页：展示全部生效公告（服务/故障/版本）。 */
class AppNoticesActivity : AppCompatActivity() {

    private var notices by mutableStateOf<List<AppNotice>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 打开公告列表即视为已读：红点消失且后续不再亮起（新公告 id 才会重新亮）
        AppNoticeCenter.markNoticesSeen(this, AppNoticeCenter.mergedActiveNotices(this).map { it.id })
        notices = AppNoticeCenter.mergedActiveNotices(this)
        AppNoticeCenter.fetch(this) { fetched ->
            // fetch 回调已是「本地+远程」合并后的生效公告，直接展示；
            // 列表正在展示它们，一并标记已读，避免返回主界面后红点误亮
            notices = fetched
            AppNoticeCenter.markNoticesSeen(this, fetched.map { it.id })
            fetched.forEach { notice ->
                UsageAnalyticsClient.record(
                    UsageAnalyticsEvent.NOTICE_SHOWN,
                    mapOf(
                        UsageAnalyticsDimensions.PRESENTATION to "notice_list",
                        UsageAnalyticsDimensions.KIND to notice.kind,
                    )
                )
            }
        }
        setContentView(ComposeView(this).apply {
            setContent {
                HitaComposeTheme() {
                    NoticesScreen(notices)
                }
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticesScreen(notices: List<AppNotice>) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notices_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (notices.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.notices_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            } else {
                items(notices, key = { it.id }) { notice ->
                    NoticeCard(notice)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: AppNotice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = if (notice.isCritical) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = notice.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = notice.body,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
