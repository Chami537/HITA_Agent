package com.limpu.hitax.ui.resource

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.limpu.hitax.databinding.ActivityMarkdownViewerBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.utils.LogUtils
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.jsoup.Connection
import org.jsoup.Jsoup

class MarkdownViewerActivity : HiltBaseActivity<ActivityMarkdownViewerBinding>() {

    override fun initViewBinding(): ActivityMarkdownViewerBinding =
        ActivityMarkdownViewerBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
        applyStatusBarInsets()
    }

    override fun initViews() {
        val url = intent.getStringExtra("url") ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra("title") ?: ""
        binding.toolbar.title = title

        binding.progress.visibility = View.VISIBLE
        binding.contentText.text = ""

        Thread {
            try {
                val response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(15000)
                    .header("Accept", "text/plain,text/markdown,*/*")
                    .method(Connection.Method.GET)
                    .execute()

                if (response.statusCode() >= 400) {
                    runOnUiThread {
                        binding.progress.visibility = View.GONE
                        val msg = "加载失败: HTTP ${response.statusCode()}"
                        binding.contentText.text = msg
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val markdown = response.body()
                runOnUiThread {
                    binding.progress.visibility = View.GONE
                    renderMarkdown(markdown)
                }
            } catch (e: Exception) {
                LogUtils.e("MarkdownViewer load failed", e)
                runOnUiThread {
                    binding.progress.visibility = View.GONE
                    val msg = "加载失败: ${e.message}"
                    binding.contentText.text = msg
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun renderMarkdown(markdown: String) {
        val markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(JLatexMathPlugin.create(binding.contentText.textSize))
            .usePlugin(GlideImagesPlugin.create(this))
            .build()
        markwon.setMarkdown(binding.contentText, markdown)
        binding.contentText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun applyStatusBarInsets() {
        val target = binding.root
        val originalTop = target.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = originalTop + bars.top)
            insets
        }
    }
}
