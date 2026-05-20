package com.limpu.hitax.ui.resource

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.model.resource.UnifiedResourceItem
import com.limpu.hitax.databinding.ActivityUnifiedResourceSearchBinding
import com.limpu.hitax.databinding.ItemExternalResourceEntryBinding
import com.limpu.hitax.databinding.ItemUnifiedResourceBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.CourseCodeUtils
import com.limpu.hitax.utils.LogUtils
import com.limpu.style.base.BaseListAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UnifiedResourceSearchActivity :
    HiltBaseActivity<ActivityUnifiedResourceSearchBinding>() {

    private val viewModel: UnifiedResourceSearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var entryAdapter: EntryAdapter
    private var isBrowseMode = false
    private val browseStack = ArrayDeque<BrowseState>()

    private data class BrowseState(
        val path: String,
        val source: ResourceSource,
        val breadcrumb: String,
    )

    override fun initViewBinding(): ActivityUnifiedResourceSearchBinding =
        ActivityUnifiedResourceSearchBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
        applyStatusBarInsets()
    }

    override fun initViews() {
        binding.toolbar.title = getString(R.string.unified_resource_title)

        searchAdapter = SearchResultAdapter(mutableListOf())
        entryAdapter = EntryAdapter(mutableListOf())
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = searchAdapter

        binding.searchInput.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                    startSearch()
                    return true
                }
                return false
            }
        })
        binding.searchInputLayout.setEndIconOnClickListener { startSearch() }
        binding.swipeRefresh.setColorSchemeColors(getColorPrimary())
        binding.swipeRefresh.setOnRefreshListener {
            if (isBrowseMode) {
                val state = browseStack.lastOrNull() ?: return@setOnRefreshListener
                viewModel.browse(state.path, state.source)
            } else {
                startSearch()
            }
        }

        viewModel.searchResults.observe(this) { state ->
            if (isBrowseMode) return@observe
            binding.swipeRefresh.isRefreshing = false
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data ?: emptyList()
                searchAdapter.notifyItemChangedSmooth(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.setText(R.string.course_resource_empty)
            } else {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.setText(R.string.course_resource_failed)
                state.message?.takeIf { it.isNotBlank() }?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.browseResults.observe(this) { state ->
            if (!isBrowseMode) return@observe
            binding.swipeRefresh.isRefreshing = false
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data ?: emptyList()
                entryAdapter.notifyItemChangedSmooth(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.setText(R.string.external_resource_empty)
            } else {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.setText(R.string.external_resource_failed)
                state.message?.takeIf { it.isNotBlank() }?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        val initialQuery = intent.getStringExtra("query")
        val browsePath = intent.getStringExtra("browsePath")
        val browseSource = intent.getStringExtra("browseSource")?.let { raw ->
            runCatching { ResourceSource.valueOf(raw) }.getOrNull()
        }
        val browseTitle = intent.getStringExtra("browseTitle")
        if (!browsePath.isNullOrBlank() && browseSource != null) {
            val title = browseTitle?.takeIf { it.isNotBlank() } ?: browsePath.substringAfterLast("/")
            enterBrowseMode(
                UnifiedResourceItem.ExternalCourse(
                    courseName = title,
                    category = "",
                    source = browseSource,
                    path = browsePath,
                )
            )
        } else if (!initialQuery.isNullOrBlank()) {
            val normalized = CourseCodeUtils.normalize(initialQuery) ?: initialQuery
            binding.searchInput.setText(normalized)
            binding.searchInput.setSelection(normalized.length)
            binding.swipeRefresh.isRefreshing = true
            viewModel.search(normalized)
        } else {
            binding.swipeRefresh.isRefreshing = true
            viewModel.search("")
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun startSearch() {
        val input = binding.searchInput.text?.toString()?.trim().orEmpty()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)

        if (isBrowseMode) {
            exitBrowseMode()
        }

        binding.swipeRefresh.isRefreshing = true
        viewModel.search(input)
    }

    private fun onItemClick(item: UnifiedResourceItem) {
        when (item) {
            is UnifiedResourceItem.HoaCourse -> {
                ActivityUtils.startCourseReadmeActivity(
                    this,
                    item.repoName,
                    item.courseName,
                    item.courseCode,
                    item.repoType,
                )
            }
            is UnifiedResourceItem.ExternalCourse -> {
                enterBrowseMode(item)
            }
        }
    }

    private fun enterBrowseMode(item: UnifiedResourceItem.ExternalCourse) {
        isBrowseMode = true
        browseStack.clear()
        val state = BrowseState(item.path, item.source, item.courseName)
        browseStack.addLast(state)

        binding.searchInputLayout.visibility = View.GONE
        binding.breadcrumb.visibility = View.VISIBLE
        binding.breadcrumb.text = item.courseName
        binding.list.adapter = entryAdapter
        binding.toolbar.title = getString(R.string.unified_resource_browse)

        binding.swipeRefresh.isRefreshing = true
        viewModel.browse(item.path, item.source)
    }

    private fun navigateInto(entry: ExternalResourceEntry) {
        if (!entry.isDir) {
            handleFileClick(entry)
            return
        }

        val currentState = browseStack.lastOrNull() ?: return
        val newState = BrowseState(
            path = entry.path,
            source = currentState.source,
            breadcrumb = "${currentState.breadcrumb} / ${entry.name}",
        )
        browseStack.addLast(newState)

        binding.breadcrumb.text = newState.breadcrumb
        binding.swipeRefresh.isRefreshing = true
        viewModel.browse(entry.path, entry.source)
    }

    private fun exitBrowseMode() {
        isBrowseMode = false
        browseStack.clear()

        binding.searchInputLayout.visibility = View.VISIBLE
        binding.breadcrumb.visibility = View.GONE
        binding.list.adapter = searchAdapter
        binding.toolbar.title = getString(R.string.unified_resource_title)
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isBrowseMode && browseStack.size > 1) {
                browseStack.removeLast()
                val previous = browseStack.last()
                binding.breadcrumb.text = previous.breadcrumb
                binding.swipeRefresh.isRefreshing = true
                viewModel.browse(previous.path, previous.source)
            } else if (isBrowseMode) {
                exitBrowseMode()
                binding.emptyText.visibility = View.GONE
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    private fun handleFileClick(entry: ExternalResourceEntry) {
        val url = entry.downloadUrl

        if (url.startsWith("https://fireworks.jwyihao.top")) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        }

        val rawUrl = if (entry.path.isNotBlank()) {
            val repo = when (entry.source) {
                ResourceSource.HITCS -> "HITLittleZheng/HITCS"
                ResourceSource.FIREWORKS -> "HIT-Fireworks/fireworks-notes-society"
            }
            val encodedPath = entry.path.split("/").joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            "https://raw.githubusercontent.com/$repo/main/$encodedPath"
        } else if (url.isNotBlank() && !url.startsWith("https://fireworks.")) {
            url
        } else {
            return
        }
        val downloadUrl = "https://ghproxy.net/$rawUrl"

        if (entry.name.endsWith(".md", ignoreCase = true)) {
            startActivity(Intent(this, MarkdownViewerActivity::class.java).apply {
                putExtra("url", downloadUrl)
                putExtra("title", entry.name)
            })
            return
        }

        val fileName = entry.name
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle(fileName)
                .setDescription("正在下载 $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            dm.enqueue(request)
            Snackbar.make(binding.root, "开始下载: $fileName", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogUtils.e("Download failed: ${e.message}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            } catch (e2: Exception) {
                Snackbar.make(binding.root, "下载失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
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

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    inner class SearchResultAdapter(mBeans: MutableList<UnifiedResourceItem>) :
        BaseListAdapter<UnifiedResourceItem, SearchResultAdapter.Holder>(this, mBeans) {

        inner class Holder(val binding: ItemUnifiedResourceBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
            return ItemUnifiedResourceBinding.inflate(layoutInflater, parent, false)
        }

        override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): Holder {
            return Holder(viewBinding as ItemUnifiedResourceBinding)
        }

        override fun bindHolder(holder: Holder, data: UnifiedResourceItem?, position: Int) {
            data ?: return
            holder.binding.title.text = data.displayName
            holder.binding.subtitle.text = data.subtitle
            val chip = holder.binding.sourceChip
            chip.text = data.sourceTag
            try {
                chip.setChipBackgroundColorResource(data.sourceColor)
            } catch (_: Exception) {
                // fallback to default
            }
            holder.binding.root.setOnClickListener { onItemClick(data) }
        }
    }

    inner class EntryAdapter(mBeans: MutableList<ExternalResourceEntry>) :
        BaseListAdapter<ExternalResourceEntry, EntryAdapter.Holder>(this, mBeans) {

        inner class Holder(val binding: ItemExternalResourceEntryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
            return ItemExternalResourceEntryBinding.inflate(layoutInflater, parent, false)
        }

        override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): Holder {
            return Holder(viewBinding as ItemExternalResourceEntryBinding)
        }

        override fun bindHolder(holder: Holder, data: ExternalResourceEntry?, position: Int) {
            data ?: return
            holder.binding.name.text = data.name
            if (data.isDir) {
                holder.binding.icon.setImageResource(R.drawable.ic_baseline_menu_24)
                holder.binding.fileSize.visibility = View.GONE
            } else {
                holder.binding.icon.setImageResource(R.drawable.ic_baseline_search_24)
                if (data.size > 0) {
                    holder.binding.fileSize.text = formatFileSize(data.size)
                    holder.binding.fileSize.visibility = View.VISIBLE
                } else {
                    holder.binding.fileSize.visibility = View.GONE
                }
            }
            holder.binding.card.setOnClickListener {
                navigateInto(data)
            }
        }
    }
}
