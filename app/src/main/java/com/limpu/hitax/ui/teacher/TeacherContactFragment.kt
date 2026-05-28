package com.limpu.hitax.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme

class TeacherContactFragment : BottomSheetDialogFragment() {

    private var phoneS: String? = null
    private var emailS: String? = null
    private var addressS: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            phoneS = it.getString("phone")
            emailS = it.getString("email")
            addressS = it.getString("address")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    TeacherContactSheet(
                        phone = phoneS,
                        email = emailS,
                        address = addressS
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance(contact: Map<String, String>): TeacherContactFragment {
            val b = Bundle()
            b.putString("phone", contact["phone"])
            b.putString("email", contact["email"])
            b.putString("address", contact["address"])
            val f = TeacherContactFragment()
            f.arguments = b
            return f
        }
    }
}

@Composable
private fun TeacherContactSheet(
    phone: String?,
    email: String?,
    address: String?
) {
    val tokens = HitaTheme.tokens
    val noDataText = stringResource(R.string.no_teacher_contact_data)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.sm)
    ) {
        ContactRow(
            iconRes = R.drawable.ic_baseline_local_phone_24,
            label = stringResource(R.string.phone),
            value = phone?.ifBlank { null } ?: noDataText
        )
        ContactRow(
            iconRes = R.drawable.ic_baseline_email_24,
            label = stringResource(R.string.email),
            value = email?.ifBlank { null } ?: noDataText
        )
        ContactRow(
            iconRes = R.drawable.ic_baseline_location_city_24,
            label = stringResource(R.string.address),
            value = address?.ifBlank { null } ?: noDataText
        )
    }
}

@Composable
private fun ContactRow(
    iconRes: Int,
    label: String,
    value: String
) {
    val tokens = HitaTheme.tokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .padding(tokens.spacing.xs)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = tokens.spacing.xs)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
