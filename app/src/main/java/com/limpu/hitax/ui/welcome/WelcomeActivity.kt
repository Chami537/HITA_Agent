package com.limpu.hitax.ui.welcome

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.welcome.login.LoginFragment
import com.limpu.hitax.ui.welcome.signup.SignUpFragment
import com.limpu.style.ThemeTools
import com.limpu.style.base.BaseTabAdapter
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("NonConstantResourceId")
@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {
    protected val viewModel: WelcomeViewModel by viewModels()
    private var pagerAdapter: BaseTabAdapter? = null

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
                WelcomeScreen(
                    activity = this,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onPagerReady = { pager, tabs ->
                        if (pagerAdapter == null) {
                            pagerAdapter = WelcomePagerAdapter(supportFragmentManager, this)
                        }
                        pager.adapter = pagerAdapter
                        tabs.setupWithViewPager(pager)
                    }
                )
            }
        }
    }

    private class WelcomePagerAdapter(
        fm: FragmentManager,
        private val activity: FragmentActivity
    ) : BaseTabAdapter(fm, 2) {
        override fun initItem(position: Int): Fragment {
            return if (position == 0) {
                LoginFragment.newInstance()
            } else {
                SignUpFragment.newInstance()
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return if (position == 0) {
                activity.getString(R.string.login)
            } else {
                activity.getString(R.string.sign_up)
            }
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            mFragments[position] = null
        }
    }
}

@Composable
private fun WelcomeScreen(
    activity: FragmentActivity,
    onBack: () -> Unit,
    onPagerReady: (ViewPager, TabLayout) -> Unit,
) {
    val tokens = HitaTheme.tokens
    val density = LocalDensity.current
    val pagerId = remember { android.view.View.generateViewId() }
    val tabHeightPx = with(density) { 38.dp.toPx().toInt() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(180f)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = tokens.spacing.xxl)
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    val tabContainer = android.widget.FrameLayout(context).apply {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = with(density) { 19.dp.toPx() }
                            setColor(android.graphics.Color.TRANSPARENT)
                        }
                    }
                    val tabs = TabLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        tabMode = TabLayout.MODE_FIXED
                        isTabIndicatorFullWidth = false
                    }
                    tabContainer.addView(
                        tabs,
                        android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER
                        )
                    )
                    val pager = ViewPager(context).apply { id = pagerId }
                    addView(
                        tabContainer,
                        android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            tabHeightPx
                        )
                    )
                    addView(
                        pager,
                        android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    )
                    onPagerReady(pager, tabs)
                }
            },
            update = {
                activity.supportFragmentManager.executePendingTransactions()
            }
        )
    }
}
