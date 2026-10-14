package cn.limpu.hita.ui.timetable.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.limpu.hita.R
import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec
import cn.limpu.hita.feature.timetableshare.qr.TimetableQrResult
import cn.limpu.hita.feature.timetableshare.qr.encodeTimetableQr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QrDisplayState {
    data object Loading : QrDisplayState
    data object TooLarge : QrDisplayState
    data object Failed : QrDisplayState
    data class Ready(val bitmap: Bitmap, val mode: ShareMode) : QrDisplayState
}

@Composable
fun TimetableQrDialog(token: String, onDismiss: () -> Unit) {
    var state by remember(token) { mutableStateOf<QrDisplayState>(QrDisplayState.Loading) }
    LaunchedEffect(token) {
        state = QrDisplayState.Loading
        state = try {
            withContext(Dispatchers.Default) {
                when (val result = encodeTimetableQr(token)) {
                    TimetableQrResult.TooLarge -> QrDisplayState.TooLarge
                    is TimetableQrResult.Ready -> {
                        val mode = TimetableShareCodec().decodeMessage(token).mode
                        val pixels = IntArray(result.matrix.width * result.matrix.height) { index ->
                            if (result.matrix[index % result.matrix.width, index / result.matrix.width])
                                android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        }
                        val bitmap = Bitmap.createBitmap(result.matrix.width, result.matrix.height, Bitmap.Config.ARGB_8888)
                        bitmap.setPixels(pixels, 0, result.matrix.width, 0, 0, result.matrix.width, result.matrix.height)
                        QrDisplayState.Ready(bitmap, mode)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            QrDisplayState.Failed
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.timetable_share_qr_title), style = MaterialTheme.typography.titleLarge)
                when (val current = state) {
                    QrDisplayState.Loading -> Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    QrDisplayState.TooLarge -> Text(stringResource(R.string.timetable_share_qr_too_large))
                    QrDisplayState.Failed -> Text(stringResource(R.string.timetable_share_qr_failed))
                    is QrDisplayState.Ready -> {
                        Box(Modifier.fillMaxWidth().background(Color.White).padding(4.dp)) {
                            Image(current.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.timetable_share_qr_title),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = FilterQuality.None)
                        }
                        Text(stringResource(if (current.mode == ShareMode.FULL) R.string.timetable_share_qr_full_notice
                            else R.string.timetable_share_qr_busy_notice), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.timetable_share_close))
                }
            }
        }
    }
}
