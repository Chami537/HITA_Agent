package com.limpu.hitax.ui.myprofile

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.model.UserProfile
import com.limpu.style.ThemeTools
import com.limpu.style.widgets.PopUpEditText
import com.limpu.style.widgets.PopUpSelectableList
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyProfileActivity : AppCompatActivity() {
    private val viewModel: MyProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)

        bindResultToasts()

        setContent {
            HitaComposeTheme() {
                MyProfileScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onEditNickname = ::showNicknameEditor,
                    onEditGender = ::showGenderPicker,
                    onEditSignature = ::showSignatureEditor
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startRefresh()
    }

    private fun bindResultToasts() {
        viewModel.changeNicknameResult?.observe(this) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                Toast.makeText(this, R.string.notif_nick_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.changeGenderResult.observe(this) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                Toast.makeText(this, R.string.notif_nick_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.changeSignatureResult?.observe(this) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                Toast.makeText(this, R.string.notif_signature_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun currentProfile(): UserProfile? {
        val state = viewModel.userProfileLiveData.value
        return if (state?.state == DataState.STATE.SUCCESS) state.data else null
    }

    private fun showNicknameEditor() {
        val profile = currentProfile() ?: return
        PopUpEditText()
            .setTitle(R.string.set_nickname)
            .setText(profile.nickname)
            .setOnConfirmListener(object : PopUpEditText.OnConfirmListener {
                override fun OnConfirm(text: String) {
                    viewModel.startChangeNickname(text)
                }
            })
            .show(supportFragmentManager, "edit_nickname")
    }

    private fun showGenderPicker() {
        val profile = currentProfile() ?: return
        PopUpSelectableList<UserLocal.GENDER>()
            .setTitle(R.string.choose_gender)
            .setInitValue(profile.gender)
            .setListData(
                listOf(
                    getString(R.string.male),
                    getString(R.string.female),
                    getString(R.string.other_gender)
                ),
                listOf(UserLocal.GENDER.MALE, UserLocal.GENDER.FEMALE, UserLocal.GENDER.OTHER)
            )
            .setOnConfirmListener(object : PopUpSelectableList.OnConfirmListener<UserLocal.GENDER> {
                override fun onConfirm(title: String?, key: UserLocal.GENDER) {
                    viewModel.startChangeGender(key)
                }
            })
            .show(supportFragmentManager, "select_gender")
    }

    private fun showSignatureEditor() {
        val profile = currentProfile() ?: return
        PopUpEditText()
            .setTitle(R.string.choose_signature)
            .setText(profile.signature)
            .setOnConfirmListener(object : PopUpEditText.OnConfirmListener {
                override fun OnConfirm(text: String) {
                    viewModel.startChangeSignature(text)
                }
            })
            .show(supportFragmentManager, "edit_signature")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyProfileScreen(
    viewModel: MyProfileViewModel,
    onBack: () -> Unit,
    onEditNickname: () -> Unit,
    onEditGender: () -> Unit,
    onEditSignature: () -> Unit
) {
    val context = LocalContext.current
    val tokens = HitaTheme.tokens
    val profileState by viewModel.userProfileLiveData.observeAsState()
    val profile = profileState?.data

    LaunchedEffect(profileState?.state) {
        val state = profileState ?: return@LaunchedEffect
        if (state.state != DataState.STATE.SUCCESS) {
            Toast.makeText(context, "加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.my_profile_title),
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.label_basic_profile),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(0.4f)
                    .padding(
                        start = tokens.spacing.lg,
                        top = 18.dp,
                        end = tokens.spacing.lg,
                        bottom = tokens.spacing.lg
                    )
            )

            ProfileRow(
                label = stringResource(R.string.username2),
                value = profile?.username.orEmpty(),
                primaryLabel = false
            )
            ProfileRow(
                label = stringResource(R.string.avatar),
                customValue = {
                    AvatarImage(
                        avatar = profile?.avatar,
                        modifier = Modifier.size(52.dp)
                    )
                },
                height = 80.dp
            )
            ProfileRow(
                label = stringResource(R.string.gender),
                value = genderLabel(profile?.gender),
                onClick = onEditGender
            )
            ProfileRow(
                label = stringResource(R.string.prompt_nickname),
                value = profile?.nickname.orEmpty(),
                onClick = onEditNickname
            )
            ProfileRow(
                label = stringResource(R.string.signature),
                value = profile?.signature?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.drawer_signature_none),
                onClick = onEditSignature
            )
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    value: String = "",
    primaryLabel: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 72.dp,
    onClick: (() -> Unit)? = null,
    customValue: @Composable (() -> Unit)? = null
) {
    val tokens = HitaTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (primaryLabel) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(tokens.spacing.lg))
        if (customValue != null) {
            customValue()
        } else {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun AvatarImage(
    avatar: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.place_holder_avatar)
            }
        },
        update = { imageView ->
            com.limpu.hitauser.util.ImageUtils.loadAvatarInto(context, avatar, imageView)
        }
    )
}

@Composable
private fun genderLabel(gender: UserLocal.GENDER?): String {
    return stringResource(
        when (gender) {
            UserLocal.GENDER.MALE -> R.string.male
            UserLocal.GENDER.FEMALE -> R.string.female
            UserLocal.GENDER.OTHER -> R.string.other_gender
            else -> R.string.other_gender
        }
    )
}
