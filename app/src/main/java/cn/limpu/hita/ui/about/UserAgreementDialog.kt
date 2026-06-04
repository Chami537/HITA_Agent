package cn.limpu.hita.ui.about

import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cn.limpu.hita.R
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme

@Suppress("DEPRECATION")
class UserAgreementDialog : BottomSheetDialogFragment() {

    var onResponseListener: OnResponseListener? = null
    private var showActionButtons = false

    interface OnResponseListener {
        fun onAgree()
        fun onRefuse()
    }

    fun setShowActionButtons(show: Boolean): UserAgreementDialog {
        showActionButtons = show
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
                    UserAgreementScreen(
                        showActions = showActionButtons || onResponseListener != null,
                        onAgree = {
                            onResponseListener?.onAgree()
                            dismiss()
                        },
                        onRefuse = {
                            onResponseListener?.onRefuse()
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun isCancelable(): Boolean {
        return onResponseListener == null
    }
}

@Composable
private fun UserAgreementScreen(
    showActions: Boolean,
    onAgree: () -> Unit,
    onRefuse: () -> Unit
) {
    val tokens = HitaTheme.tokens
    val tabs = listOf(
        stringResource(R.string.name_user_agreement),
        stringResource(R.string.name_privacy_agreement)
    )
    val uaContent = stringResource(R.string.user_agreement)
    val ppContent = stringResource(R.string.privacy_policy)
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }
        }

        val content = if (selectedTab == 0) uaContent else ppContent
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val linkColor = MaterialTheme.colorScheme.primary.toArgb()
        val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(tokens.spacing.sm),
            factory = { context ->
                TextView(context).apply {
                    movementMethod = ScrollingMovementMethod()
                    setPadding(16, 8, 16, 8)
                }
            },
            update = { textView ->
                val newContent = if (selectedTab == 0) uaContent else ppContent
                textView.text = Html.fromHtml(newContent)
                textView.setTextColor(textColor)
                textView.setLinkTextColor(linkColor)
                textView.setBackgroundColor(surfaceColor)
            }
        )

        if (showActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = tokens.spacing.sm,
                        end = tokens.spacing.sm,
                        top = tokens.spacing.xs,
                        bottom = tokens.spacing.sm
                    )
            ) {
                TextButton(
                    onClick = onRefuse,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.refuse),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(start = tokens.spacing.xs),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = stringResource(R.string.agree))
                }
            }
        }
    }
}
