package cn.limpu.hita.feature.timetableshare.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

sealed interface TimetableQrResult {
    data class Ready(val matrix: BitMatrix, val level: ErrorCorrectionLevel) : TimetableQrResult
    data object TooLarge : TimetableQrResult
}

/** Encodes one complete HITA2 token as an offline, single-symbol QR code. */
fun encodeTimetableQr(token: String): TimetableQrResult {
    require(token.startsWith("HITA2:") && token.length > 6 && token.substring(6).all(::base64UrlChar)) {
        "Expected one complete HITA2 token"
    }
    val level = when {
        token.length <= 1663 -> ErrorCorrectionLevel.Q
        token.length <= 2331 -> ErrorCorrectionLevel.M
        else -> return TimetableQrResult.TooLarge
    }
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to level,
        EncodeHintType.MARGIN to 4,
    )
    return try {
        // ASCII payload uses ZXing's default single-byte encoding without a UTF-8 ECI header.
        TimetableQrResult.Ready(QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 1024, 1024, hints), level)
    } catch (_: WriterException) {
        TimetableQrResult.TooLarge
    }
}

private fun base64UrlChar(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
