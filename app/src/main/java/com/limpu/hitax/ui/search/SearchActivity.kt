package com.limpu.hitax.ui.search

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.search.teacher.FragmentSearchTeacher
import com.limpu.style.ThemeTools
import com.limpu.style.base.BaseTabAdapter
import com.limpu.style.base.FragmentSearchResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : AppCompatActivity(), SearchRoot {
    protected val viewModel: SearchViewModel by viewModels()
    private var searchEditText: TextView? = null
    private var pager: ViewPager? = null
    private var pagerAdapter: SearchPagerAdapter? = null

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
                SearchScreen(
                    activity = this,
                    initialText = intent.getStringExtra("keyword").orEmpty(),
                    purpose = intent.getStringExtra("type").orEmpty(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchViewReady = { editText ->
                        searchEditText = editText
                        editText.setOnEditorActionListener { textView, actionId, _: KeyEvent? ->
                            if (textView.text.toString().isBlank()) return@setOnEditorActionListener false
                            if (
                                actionId == EditorInfo.IME_ACTION_GO ||
                                actionId == EditorInfo.IME_ACTION_SEARCH
                            ) {
                                hideKeyboard()
                                setSearchText(getSearchText())
                                true
                            } else {
                                false
                            }
                        }
                    },
                    onPagerReady = { viewPager, tabs ->
                        if (pagerAdapter == null) {
                            pagerAdapter = SearchPagerAdapter(supportFragmentManager, this)
                        }
                        pager = viewPager
                        viewPager.adapter = pagerAdapter
                        tabs.setupWithViewPager(viewPager)
                        searchForPurpose()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        searchForPurpose()
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        if (!isSearchForPurpose()) {
            popUpKeyboard()
        }
    }

    private fun isSearchForPurpose(): Boolean {
        val text = intent.getStringExtra("keyword")
        val purpose = intent.getStringExtra("type")
        return text?.isNotEmpty() == true && purpose?.isNotEmpty() == true
    }

    private fun searchForPurpose() {
        val text = intent.getStringExtra("keyword")
        searchEditText?.text = text
        val purpose = intent.getStringExtra("type")
        if (text.isNullOrEmpty() || purpose.isNullOrEmpty()) return
        val index = when (purpose) {
            "TEACHER" -> 0
            else -> 0
        }
        pager?.currentItem = index
    }

    @Suppress("DEPRECATION")
    private fun popUpKeyboard() {
        searchEditText?.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override fun getSearchText(): String {
        return searchEditText?.text?.toString().orEmpty()
    }

    fun setSearchText(text: String) {
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is FragmentSearchResult) {
                fragment.setSearchText(text)
            }
        }
    }

    class SearchPagerAdapter(fm: FragmentManager, val context: Context) : BaseTabAdapter(fm, 1) {
        private val titles: IntArray = intArrayOf(R.string.tab_search_teacher)

        override fun getPageTitle(position: Int): CharSequence {
            return context.getString(titles[position])
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            mFragments[position] = null
        }

        override fun initItem(position: Int): Fragment {
            return FragmentSearchTeacher()
        }
    }
}

@Composable
private fun SearchScreen(
    activity: FragmentActivity,
    initialText: String,
    purpose: String,
    onBack: () -> Unit,
    onSearchViewReady: (TextView) -> Unit,
    onPagerReady: (ViewPager, TabLayout) -> Unit,
) {
    val tokens = HitaTheme.tokens
    val density = LocalDensity.current
    val containerId = remember { android.view.View.generateViewId() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val tabHeightPx = with(density) { 32.dp.toPx().toInt() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = tokens.spacing.lg)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(180f)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_search_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = tokens.spacing.sm)
                            .size(24.dp)
                    )
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .padding(start = tokens.spacing.xs),
                        factory = {
                            android.widget.EditText(it).apply {
                                setSingleLine(true)
                                imeOptions = EditorInfo.IME_ACTION_SEARCH
                                inputType = android.text.InputType.TYPE_CLASS_TEXT
                                setText(initialText)
                                setTextColor(textColor)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                onSearchViewReady(this)
                            }
                        },
                        update = { editText ->
                            if (purpose.isNotBlank() && editText.text.toString() != initialText) {
                                editText.setText(initialText)
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    android.widget.LinearLayout(it).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        val tabs = TabLayout(it).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            tabMode = TabLayout.MODE_SCROLLABLE
                            isTabIndicatorFullWidth = false
                        }
                        val viewPager = ViewPager(it).apply {
                            id = containerId
                        }
                        addView(tabs, android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            tabHeightPx
                        ))
                        addView(viewPager, android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                        ))
                        onPagerReady(viewPager, tabs)
                    }
                },
                update = {
                    activity.supportFragmentManager.executePendingTransactions()
                }
            )
        }
    }
}
