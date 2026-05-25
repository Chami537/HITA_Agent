package com.limpu.hitax.ui.about

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import androidx.activity.viewModels
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.data.model.ReleaseHistoryItem
import com.limpu.hitax.databinding.ActivityAboutBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.ImageUtils
import com.limpu.hitax.utils.LogUtils
import com.limpu.style.widgets.PopUpText
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Suppress("DEPRECATION")
@AndroidEntryPoint
class ActivityAbout: HiltBaseActivity<ActivityAboutBinding>() {

    protected val viewModel: AboutViewModel by viewModels()
    private val releaseMarkwon: Markwon by lazy {
        Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setWindowParams(true,false,false)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        viewModel.aboutPageLiveData.observe(this){ state ->
            state.data?.let {
                binding.aboutInfo.text = Html.fromHtml(it)
            }
        }
        viewModel.releaseHistoryLiveData.observe(this) { state ->
            renderReleaseHistory(state)
        }
        binding.privacyProtocol.setOnClickListener {
             UserAgreementDialog().show(
                 supportFragmentManager,"a"
             )
        }
        binding.check.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            binding.check.startAnimation()
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).longVersionCode
            } else {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).versionCode.toLong()
            }
            viewModel.checkForUpdate(code)
        }
        viewModel.checkUpdateResult.observe(this){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                binding.check.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                binding.check.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            val bitmap =
                ImageUtils.getResourceBitmap(getThis(), if (it.state==DataState.STATE.SUCCESS)
                    R.drawable.ic_baseline_done_24 else R.drawable.ic_baseline_error_24)
            binding.check.doneLoadingAnimation(
                getColorPrimary(), bitmap
            )
            binding.check.postDelayed({
                binding.check.revertAnimation()
            }, 600)
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { cr ->
                    if (cr.shouldUpdate) {
                        ActivityUtils.showUpdateNotificationForce(cr,this)
                    }else{
                        val msg = if (BuildConfig.DEBUG && cr.downloadCount > 0) {
                            getString(R.string.already_up_to_date) + " · 累计下载 ${cr.downloadCount} 次"
                        } else {
                            getString(R.string.already_up_to_date)
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    @SuppressLint("SetTextI18n")
    fun refresh(){
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = packageManager
                .getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            LogUtils.e("Failed to get package info for version", e)
        }
        //获取APP版本versionName
        //获取APP版本versionName
        var versionName: String? = null
        if (packageInfo != null) {
            versionName = packageInfo.versionName
        }
        //获取APP版本versionCode
        //获取APP版本versionCode
        binding.version.text = getString(R.string.version) + versionName
        viewModel.refresh()
    }

    private fun renderReleaseHistory(state: DataState<List<ReleaseHistoryItem>>) {
        binding.releaseHistoryProgress.visibility =
            if (state.state == DataState.STATE.LOADING) View.VISIBLE else View.GONE
        binding.releaseHistoryList.removeAllViews()
        binding.releaseHistoryStatus.visibility = View.GONE

        if (state.state == DataState.STATE.LOADING) return

        if (state.state != DataState.STATE.SUCCESS) {
            binding.releaseHistoryStatus.visibility = View.VISIBLE
            binding.releaseHistoryStatus.setText(R.string.release_history_failed)
            return
        }

        val items = state.data.orEmpty()
        if (items.isEmpty()) {
            binding.releaseHistoryStatus.visibility = View.VISIBLE
            binding.releaseHistoryStatus.setText(R.string.release_history_empty)
            return
        }

        items.forEach { item ->
            binding.releaseHistoryList.addView(createReleaseHistoryView(item))
        }
    }

    private fun createReleaseHistoryView(item: ReleaseHistoryItem): View {
        val card = CardView(this).apply {
            radius = ImageUtils.dp2px(this@ActivityAbout, 8f).toFloat()
            cardElevation = 0f
            useCompatPadding = false
            setCardBackgroundColor(resolveThemeColor(R.attr.backgroundColorBottom))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ImageUtils.dp2px(this@ActivityAbout, 8f)
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ImageUtils.dp2px(this@ActivityAbout, 14f),
                ImageUtils.dp2px(this@ActivityAbout, 12f),
                ImageUtils.dp2px(this@ActivityAbout, 14f),
                ImageUtils.dp2px(this@ActivityAbout, 12f)
            )
        }
        val title = TextView(this).apply {
            text = buildString {
                append(item.releaseName)
                if (item.prerelease) append(" · 预发布")
            }
            setTextColor(resolveThemeColor(R.attr.textColorPrimary))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val content = TextView(this).apply {
            visibility = View.GONE
            setTextColor(resolveThemeColor(R.attr.textColorPrimary))
            textSize = 14f
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, ImageUtils.dp2px(this@ActivityAbout, 10f), 0, 0)
            releaseMarkwon.setMarkdown(this, item.markdown)
        }
        container.addView(title)
        container.addView(content)
        card.addView(container)
        card.setOnClickListener {
            content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        return card
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun initViewBinding(): ActivityAboutBinding {
        return ActivityAboutBinding.inflate(layoutInflater)
    }
}
